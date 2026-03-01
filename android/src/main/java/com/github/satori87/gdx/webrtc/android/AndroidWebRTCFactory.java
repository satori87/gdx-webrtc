package com.github.satori87.gdx.webrtc.android;

import android.content.Context;

import com.github.satori87.gdx.webrtc.BaseWebRTCClient;
import com.github.satori87.gdx.webrtc.WebRTCClient;
import com.github.satori87.gdx.webrtc.WebRTCClientListener;
import com.github.satori87.gdx.webrtc.WebRTCConfiguration;
import com.github.satori87.gdx.webrtc.WebRTCFactory;

/**
 * Android implementation of {@link WebRTCFactory} that creates WebRTC clients
 * using Google's WebRTC SDK ({@code io.github.webrtc-sdk:android}).
 *
 * <p>This factory assembles the three platform-specific strategy implementations
 * required by {@link BaseWebRTCClient}:</p>
 * <ul>
 *   <li>{@link AndroidPeerConnectionProvider} -- manages native WebRTC peer
 *       connections via the {@code org.webrtc} API</li>
 *   <li>{@link AndroidSignalingProvider} -- handles WebSocket signaling via
 *       the Java-WebSocket library</li>
 *   <li>{@link ExecutorScheduler} -- schedules ICE restart timers using a
 *       {@code ScheduledExecutorService}</li>
 * </ul>
 *
 * <h3>Context Requirement</h3>
 * <p>An Android {@link Context} is required because the Google WebRTC SDK needs
 * it to initialize its native {@link org.webrtc.PeerConnectionFactory}. The
 * constructor calls {@link Context#getApplicationContext()} to avoid leaking
 * Activity references. Typically, you pass {@code this} from your Activity or
 * Application class.</p>
 *
 * <h3>Usage</h3>
 * <pre>
 * // In your Android launcher Activity:
 * WebRTCClients.FACTORY = new AndroidWebRTCFactory(this);
 *
 * // Then create clients anywhere in shared code:
 * WebRTCClient client = WebRTCClients.newClient(config, listener);
 * client.connect();
 * </pre>
 *
 * @see WebRTCFactory
 * @see com.github.satori87.gdx.webrtc.WebRTCClients
 * @see BaseWebRTCClient
 */
public class AndroidWebRTCFactory implements WebRTCFactory {

    /**
     * The Android application context, obtained via {@link Context#getApplicationContext()}
     * to avoid leaking Activity references. Passed to
     * {@link AndroidPeerConnectionProvider} for native WebRTC SDK initialization.
     */
    private final Context applicationContext;

    /**
     * Creates a new Android WebRTC factory.
     *
     * <p>The provided context is converted to the application context via
     * {@link Context#getApplicationContext()} to prevent Activity leaks.
     * This factory can then be assigned to
     * {@link com.github.satori87.gdx.webrtc.WebRTCClients#FACTORY} for use
     * in platform-agnostic shared code.</p>
     *
     * @param context any Android {@link Context} (Activity, Service, Application, etc.).
     *                The application context is extracted automatically.
     */
    public AndroidWebRTCFactory(Context context) {
        this.applicationContext = context.getApplicationContext();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Creates a new {@link BaseWebRTCClient} wired with the Android-specific
     * strategy implementations:</p>
     * <ul>
     *   <li>{@link AndroidPeerConnectionProvider} -- for native WebRTC peer
     *       connection management via the Google WebRTC SDK</li>
     *   <li>{@link AndroidSignalingProvider} -- for WebSocket signaling via
     *       the Java-WebSocket library</li>
     *   <li>{@link ExecutorScheduler} -- for scheduling ICE restart timers</li>
     * </ul>
     *
     * <p>The returned client is not yet connected. Call
     * {@link WebRTCClient#connect()} to initiate the signaling connection.</p>
     *
     * @param config   the WebRTC and signaling configuration
     * @param listener the listener for connection and data events
     * @return a new, unconnected {@link BaseWebRTCClient} configured for Android
     */
    public WebRTCClient createClient(WebRTCConfiguration config, WebRTCClientListener listener) {
        return new BaseWebRTCClient("[WebRTC-Android] ", config, listener,
                new AndroidPeerConnectionProvider(applicationContext),
                new AndroidSignalingProvider(),
                new ExecutorScheduler());
    }
}
