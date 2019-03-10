package com.stackrating.monitor;

import com.google.code.stackexchange.client.query.StackExchangeApiQueryFactory;
import com.google.code.stackexchange.common.PagedList;
import com.google.code.stackexchange.schema.*;
import com.google.code.stackexchange.schema.Question.SortOrder;
import com.stackrating.Main;
import com.stackrating.log.Progress;
import com.stackrating.model.Entry;
import com.stackrating.model.Game;
import com.stackrating.model.Player;
import com.stackrating.storage.NonThrowingCloseable;
import com.stackrating.storage.Storage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.google.code.stackexchange.client.query.StackExchangeApiQueryFactory.newInstance;
import static com.google.code.stackexchange.schema.StackExchangeSite.STACK_OVERFLOW;
import static com.stackrating.Util.formatInstant;
import static java.time.temporal.ChronoUnit.SECONDS;

public class SOContentDownloader {

    private static final Logger logger = LoggerFactory.getLogger(SOContentDownloader.class);

    // Leave at least MIN_QUOTA available. Could for instance be useful if one needs to debug a
    // crash that happened earlier the same day.
    private final int MIN_QUOTA = 50;

    private final String USER_FILTER = "!T6o*9ZK8_erLKZ5IC*";
    private final String QUESTION_FILTER = "!)IMA2K9zb551p*lhR1G2BOBhUlbt_W6F_3Xb";
    private final StackExchangeApiQueryFactory queryFactory;

    private final Storage storage;
    private TimedLock apiLock = new TimedLock();
    private int lastSeenQuota = Integer.MAX_VALUE;
    private Game lastProcessed;
    private int lastUserPage = 0;

    public SOContentDownloader(Storage storage) throws IOException {
        this.storage = storage;
        
        // Load app key from properties file
        Properties conf = new Properties();
        conf.load(SOContentDownloader.class.getResourceAsStream("/stackrating.properties"));
        String appKey = conf.get("appkey").toString();
        
        this.queryFactory = newInstance(appKey, STACK_OVERFLOW);
    }

    public void refreshQuestions(Instant from) throws InterruptedException {
        logger.info("Refreshing/downloading new questions...");

        Instant t = from;
        Instant visitTime = Instant.now();
        Progress progress = new Progress(logger,
                                         "Refreshing/downloading questions...",
                                         Duration.between(from, visitTime).toHours());
        outer: while (!Main.shutdownRequested) {
            apiLock.acquire();
            PagedList<Question> questions = queryFactory
                    .newQuestionApiQuery()
                    .withTimePeriod(new TimePeriod(Date.from(t), Date.from(visitTime)))
                    .withSort(SortOrder.LEAST_RECENTLY_CREATED)
                    .withFilter(QUESTION_FILTER)
                    .withPaging(new Paging(1, 100))
                    .list();
            apiLock.release(questions.getBackoff(), SECONDS);

            lastSeenQuota = questions.getQuotaRemaining();

            // Are we done? (Since we advance time to the point of the last game, we will
            // unfortunately always receive the last processed game in the next query too.)
            if (questions.isEmpty() || containsOnlyLastProcessed(questions)) {
                logger.info("All questions in cycle period ({} - {}) downloaded",
                        formatInstant(from),
                        formatInstant(visitTime));
                break;
            }

            try (NonThrowingCloseable c = storage.openSession()) {

                for (Question q : questions) {
                    lastProcessed = processQuestion(q, visitTime);
                    if (Main.shutdownRequested) {
                        break outer;
                    }
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
            }

            if (questions.getQuotaRemaining() < MIN_QUOTA) {
                // Under normal operation, this is probably quite bad, since we now only do one of
                // these cycles per day.
                logger.info("Low on quota. Enough downloading for now.");
                break;
            }
        }
        logger.info("Remaining quota after refreshQuestions: " + lastSeenQuota);
    }

    public void refreshPlayers() throws InterruptedException {
        logger.info("Refreshing/downloading player information...");
        int initialQuotaRemaining = lastSeenQuota;
        Progress progress = new Progress(logger,
                "Refreshing/downloading player information...",
                lastSeenQuota - MIN_QUOTA);

        // This will loop until we're too low on quota
        while (!Main.shutdownRequested) {
            apiLock.acquire();
            PagedList<User> users = queryFactory.newUserApiQuery()
                    .withSort(User.SortOrder.MOST_REPUTED)
                    .withPaging(new Paging(lastUserPage, 100))
                    .withFilter(USER_FILTER)
                    .list();
            apiLock.release(users.getBackoff(), SECONDS);

            lastSeenQuota = users.getQuotaRemaining();

            progress.setProgress(initialQuotaRemaining - users.getQuotaRemaining(),
                    "page: " + lastUserPage,
                    "quota remaining: " + users.getQuotaRemaining());

            try (NonThrowingCloseable c = storage.openSession()) {
                for (User user : users) {
                    storage.updateNameAndRep(
                            (int) user.getUserId(),
                            user.getDisplayName(),
                            (int) user.getReputation());
                }
            }

            if (users.hasMore()) {
                lastUserPage++;
            } else {
                lastUserPage = 0;
            }

            if (users.getQuotaRemaining() < MIN_QUOTA) {
                logger.info("Low on quota. Enough downloading for now.");
                break;
            }
        }

        logger.info("Remaining quota after refreshPlayers: " + lastSeenQuota);
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
            Optional<Player> existingPlayer = storage.findPlayer((int) owner.getUserId());
            Player player = new Player(
                    (int) owner.getUserId(),
                    owner.getDisplayName(),
                    (int) owner.getReputation(),
                    existingPlayer.map(Player::getRating).orElse(1500.0),
                    existingPlayer.map(Player::getRepPos).orElse(0),
                    existingPlayer.map(Player::getRatingPos).orElse(0));
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
