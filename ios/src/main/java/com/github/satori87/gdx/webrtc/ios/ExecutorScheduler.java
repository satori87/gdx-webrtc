package com.github.satori87.gdx.webrtc.ios;

import com.github.satori87.gdx.webrtc.Scheduler;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * iOS implementation of Scheduler using ScheduledExecutorService.
 * Uses a single daemon thread for all scheduled tasks.
 */
class ExecutorScheduler implements Scheduler {

    /**
     * Single-thread executor that runs all scheduled tasks. Uses a daemon thread
     * named "webrtc-scheduler" so that the JVM can exit even if tasks are pending.
     */
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(
            new java.util.concurrent.ThreadFactory() {
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "webrtc-scheduler");
                    t.setDaemon(true);
                    return t;
                }
            });

    /** {@inheritDoc} */
    public Object schedule(Runnable task, long delayMs) {
        return executor.schedule(task, delayMs, TimeUnit.MILLISECONDS);
    }

    /** {@inheritDoc} */
    public void cancel(Object handle) {
        if (handle instanceof ScheduledFuture) {
            ((ScheduledFuture) handle).cancel(false);
        }
    }

    /** {@inheritDoc} */
    public void shutdown() {
        executor.shutdown();
    }
}
