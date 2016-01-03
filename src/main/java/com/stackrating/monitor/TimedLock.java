package com.stackrating.monitor;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.TemporalUnit;
import java.util.concurrent.Semaphore;

public class TimedLock {

    Instant releaseTime = Instant.now();
    Semaphore sem = new Semaphore(1);

    public void acquire() throws InterruptedException {
        sem.acquire();
        Instant now = Instant.now();
        if (now.isBefore(releaseTime)) {
            Thread.sleep(Duration.between(now, releaseTime).toMillis());
        }
    }

    public void release(int backoffTime, TemporalUnit unit) {
        releaseTime = Instant.now().plus(backoffTime, unit);
        sem.release();
    }
}
