package com.github.satori87.gdx.webrtc.teavm;

import com.github.satori87.gdx.webrtc.Scheduler;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

/**
 * Browser implementation of {@link Scheduler} using the native {@code setTimeout} and
 * {@code clearTimeout} APIs via TeaVM's JSO (JavaScript Object) interop layer.
 *
 * <p>This scheduler maps the platform-agnostic {@link Scheduler} interface to the browser's
 * built-in timer functions. Scheduled tasks execute on the browser's main event loop thread,
 * which is the same thread that drives all TeaVM-compiled Java code in the browser.</p>
 *
 * <p>Timer handles are represented as boxed {@link Integer} values corresponding to the
 * integer IDs returned by JavaScript's {@code setTimeout()}. These handles can be passed
 * to {@link #cancel(Object)} to clear the timer via {@code clearTimeout()}.</p>
 *
 * <p>The {@link #shutdown()} method is a no-op since the browser manages its own event loop
 * lifecycle and there is no thread pool to terminate.</p>
 *
 * @see Scheduler
 * @see TeaVMWebRTCFactory
 */
public class TeaVMScheduler implements Scheduler {

    /**
     * JSFunctor callback interface for receiving timer expirations from JavaScript.
     *
     * <p>Passed to the native {@code setTimeout()} function. When the timer expires,
     * the browser invokes {@link #onTimer()}, which in turn runs the scheduled
     * {@link Runnable} task.</p>
     *
     * @see org.teavm.jso.JSFunctor
     */
    @JSFunctor
    public interface TimerCallback extends JSObject {
        /**
         * Called when the browser timer expires.
         */
        void onTimer();
    }

    /**
     * Schedules a task to run after the specified delay using the browser's {@code setTimeout}.
     *
     * <p>The task will execute on the browser's main event loop thread. The returned handle
     * is a boxed {@link Integer} containing the timer ID, which can be passed to
     * {@link #cancel(Object)} to clear the timer before it fires.</p>
     *
     * @param task    the {@link Runnable} to execute when the timer expires
     * @param delayMs the delay in milliseconds before the task executes
     * @return an {@link Integer} handle representing the browser timer ID
     */
    public Object schedule(final Runnable task, long delayMs) {
        int timerId = setTimeout(new TimerCallback() {
            public void onTimer() {
                task.run();
            }
        }, (int) delayMs);
        return Integer.valueOf(timerId);
    }

    /**
     * Cancels a previously scheduled task using the browser's {@code clearTimeout}.
     *
     * <p>If the handle is {@code null}, this method does nothing. The handle must be
     * a boxed {@link Integer} as returned by {@link #schedule(Runnable, long)}.</p>
     *
     * @param handle the timer handle returned by {@link #schedule(Runnable, long)},
     *               or {@code null} to do nothing
     */
    public void cancel(Object handle) {
        if (handle != null) {
            int timerId = ((Integer) handle).intValue();
            clearTimeout(timerId);
        }
    }

    /**
     * Shuts down the scheduler.
     *
     * <p>This is a no-op for the browser implementation. The browser manages its own
     * event loop lifecycle, and there is no thread pool or executor to terminate.
     * Individual timers can still be cancelled via {@link #cancel(Object)}.</p>
     */
    public void shutdown() {
        // No-op: browser manages its own lifecycle
    }

    // --- Native methods ---

    /**
     * Schedules a callback to fire after the given delay using the browser's {@code setTimeout}.
     *
     * @param cb      the callback to invoke when the timer expires
     * @param delayMs the delay in milliseconds
     * @return the browser timer ID, usable with {@link #clearTimeout(int)}
     */
    @JSBody(params = {"cb", "delayMs"}, script = "return setTimeout(cb, delayMs);")
    private static native int setTimeout(TimerCallback cb, int delayMs);

    /**
     * Cancels a previously scheduled timer using the browser's {@code clearTimeout}.
     *
     * @param timerId the timer ID returned by {@link #setTimeout(TimerCallback, int)}
     */
    @JSBody(params = {"timerId"}, script = "clearTimeout(timerId);")
    private static native void clearTimeout(int timerId);
}
