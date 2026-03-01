package com.github.satori87.gdx.webrtc.android;

import com.github.satori87.gdx.webrtc.Scheduler;

import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Android implementation of {@link Scheduler} using a {@link ScheduledExecutorService}
 * backed by a single daemon thread.
 *
 * <p>This scheduler is used by {@link com.github.satori87.gdx.webrtc.BaseWebRTCClient}
 * to schedule delayed ICE restart attempts and exponential backoff timers when a
 * peer connection enters the DISCONNECTED or FAILED state. It provides a simple
 * schedule/cancel API that abstracts away the underlying Java concurrency primitives.</p>
 *
 * <h3>Thread Configuration</h3>
 * <p>The backing thread is configured as a daemon thread named {@code "webrtc-scheduler"},
 * so it will not prevent JVM shutdown. Only a single thread is used, meaning all
 * scheduled tasks execute sequentially on the same thread.</p>
 *
 * <h3>Cancellation</h3>
 * <p>The opaque handle returned by {@link #schedule(Runnable, long)} is a
 * {@link Future} instance. The {@link #cancel(Object)} method checks for this
 * type and calls {@link Future#cancel(boolean)} with {@code mayInterruptIfRunning}
 * set to {@code false}, allowing in-progress tasks to complete naturally.</p>
 *
 * <h3>Shutdown</h3>
 * <p>Calling {@link #shutdown()} invokes {@link ScheduledExecutorService#shutdownNow()},
 * which cancels all pending tasks and interrupts the scheduler thread. This is
 * called during {@link com.github.satori87.gdx.webrtc.WebRTCClient#disconnect()}.</p>
 *
 * @see Scheduler
 * @see com.github.satori87.gdx.webrtc.BaseWebRTCClient
 */
class ExecutorScheduler implements Scheduler {

    /**
     * The single-threaded scheduled executor that runs all scheduled tasks.
     * Uses a daemon thread named {@code "webrtc-scheduler"} so it does not
     * prevent JVM/process shutdown.
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
     * <p>Delegates to {@link ScheduledExecutorService#schedule(Runnable, long, TimeUnit)}
     * with the delay converted to milliseconds. The returned handle is a
     * {@link java.util.concurrent.ScheduledFuture} that can be passed to
     * {@link #cancel(Object)}.</p>
     *
     * @param task    the task to execute after the delay
     * @param delayMs the delay in milliseconds before execution
     * @return a {@link Future} handle for cancellation
     */
    public Object schedule(Runnable task, long delayMs) {
        return executor.schedule(task, delayMs, TimeUnit.MILLISECONDS);
    }

    /**
     * {@inheritDoc}
     *
     * <p>If the handle is a {@link Future}, calls {@link Future#cancel(boolean)}
     * with {@code mayInterruptIfRunning} set to {@code false}. If the handle is
     * {@code null} or not a {@link Future}, this method is a no-op.</p>
     *
     * @param handle the opaque handle returned by {@link #schedule(Runnable, long)},
     *               expected to be a {@link Future} instance
     */
    public void cancel(Object handle) {
        if (handle instanceof Future) {
            ((Future) handle).cancel(false);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Calls {@link ScheduledExecutorService#shutdownNow()} to cancel all
     * pending scheduled tasks and interrupt the executor thread. After this
     * call, no further tasks can be scheduled.</p>
     */
    public void shutdown() {
        executor.shutdownNow();
    }
}
