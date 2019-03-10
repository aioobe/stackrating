package com.stackrating;

import com.stackrating.model.Game;
import com.stackrating.monitor.SOContentDownloader;
import com.stackrating.storage.NonThrowingCloseable;
import com.stackrating.storage.Storage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.stackrating.Util.formatInstant;
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
        contentDownloader.refreshQuestions(cycleStartGame.getPostTime().toInstant());
        if (Main.shutdownRequested) {
            return;
        }
        contentDownloader.refreshPlayers();
        if (Main.shutdownRequested) {
            return;
        }
        doDatabaseFixup(cycleStartGame.getId());
    }
    
    public void doDatabaseFixup(int fromGameId) {
        // While fetching new questions, seen users are updated too. Adjust their rep positions.
        logger.info("Updating rep positions...");
        try (NonThrowingCloseable c = storage.openSession()) {
            storage.updateRepPositions();
        }
        if (Main.shutdownRequested) {
            return;
        }
        try (NonThrowingCloseable c = storage.openSession()) {
            // Update rating_deltas in entries and all ratings and rating positions for the players.
            logger.info("Recalculating rating deltas from game " + fromGameId + " onwards...");
            storage.rejudgeGames(fromGameId);
        }
    }
    
    private void setCycleStartTime() {
        try (NonThrowingCloseable c = storage.openSession()) {
            logger.info("Figuring out cycle starting point...");
            int cycleStartGameId = storage.getCycleStartGameId();
            cycleStartGame = storage.findGame(cycleStartGameId).get();
            logger.info("Starting new update cycle at {} (game id {})",
                    formatInstant(cycleStartGame.getPostTime().toInstant()),
                    cycleStartGameId);
        }
    }

    public void startLooping(Runnable endOfCycleCallback) throws InterruptedException {
        try {
            control.acquire();
            while (!Main.shutdownRequested) {

                printMemoryUsage();

                doUpdateCycle();

                if (Main.shutdownRequested) {
                    return;
                }

                // Used to for instance reload caches.
                endOfCycleCallback.run();

                // Currently, we have a daily quota on 10000, and one cycle consumes ~6000. 1 cycle a
                // day is acceptable right now, so we're fine. The code below avoids an endless CPU and
                // disk hogging cycle where we download just a few questions and then refresh the entire
                // database over and over again.
                if (contentDownloader.getLastSeenQuota() < 1000) {
                    logger.info("Low on quota. Sleeping for 24 hours.");
                    // Sleep for 24 hours.
                    long leftToSleep = Duration.of(24, HOURS).toMillis();
                    while (leftToSleep > 0) {
                        if (Main.shutdownRequested) {
                            return;
                        }
                        Thread.sleep(500);
                        leftToSleep -= 500;
                    }
                }
            }
        } finally {
            control.release();
        }
    }
    
    private void printMemoryUsage() {
        double mb = 1024*1024;
        Runtime runtime = Runtime.getRuntime();
        long totalMem = runtime.totalMemory();
        long freeMem = runtime.freeMemory();
        long maxMem = runtime.maxMemory();
        logger.debug("Memory usage:");
        logger.debug(String.format("    used: %.0f MB", (totalMem - freeMem) / mb));
        logger.debug(String.format("    free: %.0f MB", freeMem / mb));
        logger.debug(String.format("    total: %.0f MB", totalMem / mb));
        logger.debug(String.format("    max: %.0f MB", maxMem / mb));
    }

    public void awaitShutdown() throws InterruptedException {
        control.acquire();
    }
}
