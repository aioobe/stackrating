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
import java.time.temporal.ChronoUnit;
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
    Game lastProcessed;

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

            // Are we done? (Since we advance time to the point of the last game, we will
            // unfortunately always receive the last processed game in the next query too.)
            if (questions.isEmpty() || containsOnlyLastProcessed(questions)) {
                logger.info("All questions in cycle period (" + formatInstant(from) + " - " + formatInstant(visitTime) + ") downloaded.");
                break;
            }

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
    
    private boolean containsOnlyLastProcessed(PagedList<Question> questions) {
        return lastProcessed != null
                && questions.size() == 1
                && questions.get(0).getQuestionId() == lastProcessed.getId();
    }

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
            Player existingPlayer = storage.getPlayer((int) owner.getUserId());
            Player player = new Player((int) owner.getUserId(),
                                       owner.getDisplayName(),
                                       (int) owner.getReputation(),
                                       existingPlayer != null ? existingPlayer.getRating() : 1500,
                                       existingPlayer != null ? existingPlayer.getRepPos() : 0,
                                       existingPlayer != null ? existingPlayer.getRatingPos() : 0);
            storage.upsertUser(player);

            // 3. Find (or create) entry
            short newVotes = (short) (answer.getScore() + (answer.isIsAccepted() ? 1 : 0));
            Entry existingEntry = storage.getEntry((int) answer.getAnswerId(), game.getId());
            Entry entry = new Entry((int) answer.getAnswerId(),
                                    player.getId(),
                                    game.getId(),
                                    newVotes,
                                    Timestamp.from(answer.getCreationDate().toInstant()),
                                    existingEntry != null ? existingEntry.getRatingDelta() : 0,
                                    null,
                                    null,
                                    null);
            storage.upsertEntry(entry);
        }
        return game;
    }
}
