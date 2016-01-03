package com.stackrating.monitor;

import com.google.code.stackexchange.client.query.StackExchangeApiQueryFactory;
import com.google.code.stackexchange.common.PagedList;
import com.google.code.stackexchange.schema.*;
import com.google.code.stackexchange.schema.Question.SortOrder;
import com.stackrating.log.Progress;
import com.stackrating.model.Entry;
import com.stackrating.model.Game;
import com.stackrating.model.Player;
import com.stackrating.storage.Storage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Properties;

import static com.google.code.stackexchange.client.query.StackExchangeApiQueryFactory.newInstance;
import static com.google.code.stackexchange.schema.StackExchangeSite.STACK_OVERFLOW;
import static com.stackrating.Main.formatInstant;
import static java.time.temporal.ChronoUnit.SECONDS;

public class SOContentDownloader {

    private static final Logger logger = LoggerFactory.getLogger(SOContentDownloader.class);

    private final String FILTER = "!)IMA2K9zb551p*lhR1G2BOBhUlbt_W6F_3Xb";
    private final StackExchangeApiQueryFactory queryFactory;

    Storage storage;
    TimedLock apiLock = new TimedLock();
    int lastSeenQuota = Integer.MAX_VALUE;

    public SOContentDownloader(Storage storage) throws IOException {
        this.storage = storage;
        
        // Load app key from properties file
        Properties conf = new Properties();
        conf.load(SOContentDownloader.class.getResourceAsStream("/stackrating.properties"));
        String appKey = conf.get("appkey").toString();
        
        this.queryFactory = newInstance(appKey, STACK_OVERFLOW);
    }

    public void refreshContent(Instant from) throws InterruptedException {
        
        Instant t = from;
        Instant visitTime = Instant.now();
        Progress progress = new Progress(logger,
                                         "Refreshing/downloading questions...",
                                         Duration.between(from, visitTime).toHours());
        int questionsDownloadedSoFar = 0;
        while (true) {
            apiLock.acquire();
            PagedList<Question> questions = queryFactory.newQuestionApiQuery()
                                                        .withTimePeriod(new TimePeriod(Date.from(t), Date.from(visitTime)))
                                                        .withSort(SortOrder.LEAST_RECENTLY_CREATED)
                                                        .withFilter(FILTER)
                                                        .withPaging(new Paging(1, 100))
                                                        .list();
            lastSeenQuota = questions.getQuotaRemaining();
            apiLock.release(questions.getBackoff(), SECONDS);
            if (questions.isEmpty()) {
                logger.info("All questions in cycle period (" + formatInstant(from) + " - " + formatInstant(visitTime) + ") downloaded.");
                break;
            }

            Game lastProcessed = null;
            for (Question q : questions) {
                lastProcessed = processQuestion(q, visitTime);
                questionsDownloadedSoFar++;
            }
            
            // Some games may have been deleted. These games will not be returned in this query,
            // which means their lastVisit will be unchanged. This causes the cycle to reset to
            // this game over and over again. We could either use a different query for existing
            // games (fetching based on question ids) and detect deleted questions and take the
            // deleted flag into account when figuring out the start of the update cycle. But this
            // feels like a lot of book keeping so for now we batch update lastVisit for the given
            // time range instead.
            Instant nextT = lastProcessed.getPostTime().toInstant();
            storage.batchUpdateLastVisit(t, nextT, visitTime);
            
            // Advance current point in time
            t = nextT;
            progress.setProgress(Duration.between(from, t).toHours(),
                                 "quota: " + questions.getQuotaRemaining(),
                                 "time: " + formatInstant(t));

            if (questions.getQuotaRemaining() < 100) {
                logger.info("Low on quota. Enough downloading for now.");
                break;
            }

            // Implementation note:
            // This check was originally added since during catchup the algorithm would just keep
            // downloading new questions and never start rejudging them. I thought that it made
            // sense to have this limit. The problem with this is that once the algorithm have
            // caught up, and this limit kicks in in the middle of the last 90 days, the algorithm
            // will reset to the cycle start point all the time (right before 90 days ago) over and
            // over and "today" will never be reached.
//            if (questionsDownloadedSoFar >= 400000) {
//                logger.info(questionsDownloadedSoFar + " questions downloaded. Enough for now.");
//                break;
//            }
        }
        logger.info("Remaining quota: " + lastSeenQuota);
    }

    public int getLastSeenQuota() {
        return lastSeenQuota;
    }
/*
    private void refreshExistingQuestions() throws InterruptedException {
        System.out.println("Refreshing existing questions...");
        Set<Game> nextVisitOverdue = storage.extractGamesWithNextVisitOverdue(100);
        List<Integer> ids = nextVisitOverdue.stream().map(Game::getId).collect(toList());

        if (nextVisitOverdue.isEmpty()) {
            System.out.println("    No games with next visit overdue.");
            return;
        }

        System.out.println("    Revisiting " + nextVisitOverdue.size() + " questions...");

        Instant visitTime = Instant.now();

        apiLock.acquire();
        PagedList<Question> questions = queryFactory.newQuestionApiQuery()
                                                    .withQuestionIds(toLongs(ids))
                                                    .withFilter(FILTER)
                                                    .withPaging(new Paging(1, 100))
                                                    .list();
        apiLock.release(questions.getBackoff(), ChronoUnit.SECONDS);

        // Mark questions not found as deleted
        Set<Integer> foundIds = getAllQuestionIds(questions);
        ids.stream()
           .filter(id -> !foundIds.contains(id))
           .forEach(id -> deleteGame(id, visitTime));

        processQuestionList(questions, visitTime);
        System.out.println("Done refreshing existing questions.");
    }
*/

//    private Set<Integer> getAllQuestionIds(PagedList<Question> questions) {
//        return questions.stream()
//                        .map(q -> (int) q.getQuestionId())
//                        .collect(toSet());
//    }

//    private void deleteGame(int gameId, Instant visitTime) {
//        System.out.println("    Marking game " + gameId + " as deleted...");
//        Game game = storage.getGame(gameId);
//        storage.updateGame(game,
//                           game.getTitle(),
//                           visitTime,
//                           computeNextVisit(visitTime, game),
//                           true);
//    }

    private Game processQuestion(Question q, Instant visitTime) {

        // 1. Update game
        Game game = new Game((int) q.getQuestionId(),
                             q.getTitle(),
                             Timestamp.from(q.getCreationDate().toInstant()),
                             Timestamp.from(visitTime));
        storage.upsertGame(game);

        // For each answer...
        for (Answer answer : q.getAnswers()) {

            // Make sure it's a valid player
            User owner = answer.getOwner();
            if (owner.getDisplayName() == null || owner.getUserId() <= 0) {
                continue;
            }

            // 2. Update player
            Player existing = storage.getPlayer((int) owner.getUserId());
            Player player = new Player((int) owner.getUserId(),
                                       owner.getDisplayName(),
                                       (int) owner.getReputation(),
                                       existing != null ? existing.getRating() : 1500,
                                       0,  // needs to be recomputed anyway
                                       0); // needs to be recomputed anyway
            storage.upsertUser(player);

            // 3. Find (or create) entry
            short newVotes = (short) (answer.getScore() + (answer.isIsAccepted() ? 1 : 0));
            Entry entry = new Entry((int) answer.getAnswerId(),
                                    player.getId(),
                                    game.getId(),
                                    newVotes,
                                    Timestamp.from(answer.getCreationDate().toInstant()),
                                    0,
                                    null,
                                    null,
                                    null);
            storage.upsertEntry(entry);
        }
        return game;
    }
}
