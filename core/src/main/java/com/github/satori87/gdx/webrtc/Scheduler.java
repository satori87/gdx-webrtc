package com.github.satori87.gdx.webrtc;

/**
 * Strategy interface for platform-specific task scheduling.
 *
 * <p>Used by the ICE state machine in {@link BaseWebRTCClient} to schedule
 * delayed ICE restarts and exponential backoff timers. Each platform provides
 * an implementation:</p>
 * <ul>
 *   <li>{@code ExecutorScheduler} (Desktop/Android/iOS) - uses
 *       {@code ScheduledExecutorService} with a single daemon thread</li>
 *   <li>{@code TeaVMScheduler} (Browser) - uses JavaScript
 *       {@code setTimeout}/{@code clearTimeout} via {@code @JSBody}</li>
 * </ul>
 *
 * @see BaseWebRTCClient
 */
public interface Scheduler {

    /**
     * Schedules a task to execute after the given delay.
     *
     * <p>The task will run on a background thread (or the browser's event loop
     * for TeaVM). The returned handle can be passed to {@link #cancel(Object)}
     * to prevent execution.</p>
     *
     * @param task    the {@link Runnable} to execute after the delay
     * @param delayMs the delay in milliseconds before execution
     * @return an opaque handle that can be passed to {@link #cancel(Object)}
     *         to cancel the scheduled task
     */
    Object schedule(Runnable task, long delayMs);

    /**
     * Cancels a previously scheduled task.
     *
     * <p>If the task has already executed or been cancelled, this method
     * should be a no-op.</p>
     *
     * @param handle the opaque handle returned by {@link #schedule(Runnable, long)}
     */
    void cancel(Object handle);

    /**
     * Shuts down the scheduler and releases resources.
     *
     * <p>Called during {@link WebRTCClient#disconnect()}. For thread-based
     * schedulers, this terminates the executor thread. For browser-based
     * schedulers, this may be a no-op.</p>
     */
    void shutdown();
}
