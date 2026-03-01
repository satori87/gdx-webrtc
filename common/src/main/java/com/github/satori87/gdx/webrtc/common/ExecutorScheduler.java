package com.github.satori87.gdx.webrtc.common;

import com.github.satori87.gdx.webrtc.Scheduler;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Desktop/JVM implementation of {@link Scheduler} backed by a
 * {@link java.util.concurrent.ScheduledExecutorService}.
 *
 * <p>This scheduler uses a single daemon thread (named {@code "webrtc-scheduler"})
 * to execute all delayed tasks. Because the thread is a daemon thread, it will
 * not prevent the JVM from shutting down when the main application exits.</p>
 *
 * <p>This class is used by {@link BaseWebRTCClient} to schedule ICE restart
 * delays and other time-based operations without blocking the calling thread.
 * Task handles returned by {@link #schedule(Runnable, long)} are opaque
 * {@link java.util.concurrent.ScheduledFuture} instances that can be passed
 * to {@link #cancel(Object)} to abort a pending task.</p>
 *
 * <p>This class is package-private and is instantiated by
 * {@link DesktopWebRTCFactory} when creating a new {@link BaseWebRTCClient}.</p>
 *
 * @see Scheduler
 * @see DesktopWebRTCFactory
 * @see BaseWebRTCClient
 */
class ExecutorScheduler implements Scheduler {

    /**
     * Single-threaded scheduled executor that runs all delayed tasks. The backing
     * thread is configured as a daemon thread named {@code "webrtc-scheduler"}
     * so it does not prevent JVM shutdown.
     */
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(
            new java.util.concurrent.ThreadFactory() {
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "webrtc-scheduler");
                    t.setDaemon(true);
                    return t;
                }
            });

    /**
     * {@inheritDoc}
     *
     * <p>Submits the task to the underlying
     * {@link java.util.concurrent.ScheduledExecutorService} for execution after
     * the specified delay. The task will run on the daemon scheduler thread.</p>
     *
     * @param task    the task to execute after the delay
     * @param delayMs the delay in milliseconds before the task is executed
     * @return a {@link java.util.concurrent.ScheduledFuture} handle (as an opaque
     *         {@link Object}) that can be passed to {@link #cancel(Object)} to
     *         abort the task before it runs
     */
    public Object schedule(Runnable task, long delayMs) {
        return executor.schedule(task, delayMs, TimeUnit.MILLISECONDS);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Cancels a previously scheduled task by casting the handle to a
     * {@link java.util.concurrent.ScheduledFuture} and calling
     * {@code cancel(false)}. The {@code false} argument means that if the task
     * is already running, it will be allowed to complete rather than being
     * interrupted. If the handle is not a {@link java.util.concurrent.ScheduledFuture}
     * (or is {@code null}), this method is a no-op.</p>
     *
     * @param handle the opaque handle returned by {@link #schedule(Runnable, long)},
     *               or {@code null}
     */
    public void cancel(Object handle) {
        if (handle instanceof ScheduledFuture) {
            ((ScheduledFuture) handle).cancel(false);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Initiates an orderly shutdown of the underlying
     * {@link java.util.concurrent.ScheduledExecutorService}. Previously submitted
     * tasks are executed, but no new tasks will be accepted. This method does not
     * wait for pending tasks to complete; it returns immediately.</p>
     */
    public void shutdown() {
        executor.shutdown();
    }
}
