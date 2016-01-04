package com.stackrating;

import com.stackrating.model.Game;
import com.stackrating.monitor.SOContentDownloader;
import com.stackrating.storage.RatingUpdater;
import com.stackrating.storage.Storage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;

import static com.stackrating.Main.formatInstant;
import static java.time.temporal.ChronoUnit.HOURS;

public class ContentUpdater {

    private final static Logger logger = LoggerFactory.getLogger(ContentUpdater.class);
    
    private Storage storage;
    private Game cycleStartGame;
    
    // This field is set to true if, for instance, we ran out of quota when downloading new
    // questions during last cycle. When set to true, the next cycleStartTime should be set to
    // max(games.post_time) so that we pick up where we left off.
    // -- this doesn't work. We might still be in the phase of refreshing existing games when we
    //    run out of quota.
//    private boolean lastCycleIncomplete;

    // For graceful shutdowns
    SOContentDownloader contentDownloader;
    private volatile boolean keepRunning;
    private Semaphore control = new Semaphore(1);

    public ContentUpdater(Storage storage) throws IOException {
        this.storage = storage;
        contentDownloader = new SOContentDownloader(storage);
    }

    // Questions should keep being revisited as long as they are younger than 90 days. When a
    // question is older than 90 days it is "archived" and no longer revisited.
    //
    // This means that a question whose lastVisit time is less than postTime + 90 days should be
    // revisited.

    public void doUpdateCycle() throws InterruptedException {
        setCycleStartTime();
        logger.info("Refreshing/downloading new questions...");

        contentDownloader.refreshContent(cycleStartGame.getPostTime().toInstant());  // .minus(10, MINUTES); // Step back a little bit to make sure we visit the question at the 'from' timestamp.

        doDatabaseFixup(cycleStartGame.getId());
    }
    
    public void doDatabaseFixup(int fromGameId) {
        // While fetching new questions, seen users are updated too. Adjust their rep positions.
        logger.info("Updating rep positions...");
        storage.updateRepPositions();

        // Update rating_deltas in entries and all ratings and rating positions for the players.
        logger.info("Recalculating rating deltas from game with id " + fromGameId + " onwards...");
        storage.rejudgeGames(fromGameId);
    }
    
    private void setCycleStartTime() {
        int cycleStartGameId = storage.getCycleStartGameId();
        cycleStartGame = storage.getGame(cycleStartGameId);
        logger.info("*** Starting new update cycle at "
                            + formatInstant(cycleStartGame.getPostTime().toInstant())
                            + " (game id " + cycleStartGameId + ")");
    }

    public void startLooping(Runnable endOfCycleCallback) throws InterruptedException {
        keepRunning = true;
        control.acquire();
        while (keepRunning) {
            doUpdateCycle();

            // Used to for instance reload caches.
            endOfCycleCallback.run();

            // Currently, we have a daily quota on 10000, and one cycle consumes ~6000. 1 cycle a
            // day is acceptable right now, so we're fine. The code below avoids an endless CPU and
            // disk hogging cycle where we download just a few questions and then refresh the entire
            // database over and over again.
            if (contentDownloader.getLastSeenQuota() < 1000) {
                // Sleep for 24 hours.
                for (long i = 0; i < Duration.of(24, HOURS).toMillis() && keepRunning; i += 1000) {
                    Thread.sleep(1000);
                }
            }
        }
        control.release();
    }
    
    public void shutdown() throws InterruptedException {
        keepRunning = false;
        control.acquire();
    }
}
