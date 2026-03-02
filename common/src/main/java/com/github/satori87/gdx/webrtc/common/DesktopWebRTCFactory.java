package com.github.satori87.gdx.webrtc.common;

import com.github.satori87.gdx.webrtc.BaseWebRTCClient;
import com.github.satori87.gdx.webrtc.WebRTCClient;
import com.github.satori87.gdx.webrtc.WebRTCClientListener;
import com.github.satori87.gdx.webrtc.WebRTCConfiguration;
import com.github.satori87.gdx.webrtc.WebRTCFactory;
import com.github.satori87.gdx.webrtc.transport.BaseWebRTCClientTransport;
import com.github.satori87.gdx.webrtc.transport.BaseWebRTCServerTransport;
import com.github.satori87.gdx.webrtc.transport.WebRTCClientTransport;
import com.github.satori87.gdx.webrtc.transport.WebRTCServerTransport;

/**
 * Desktop/JVM implementation of {@link WebRTCFactory} that creates WebRTC clients
 * backed by the <a href="https://github.com/nicegram/webrtc-java">webrtc-java</a>
 * ({@code dev.onvoid.webrtc}) native library.
 *
 * <p>This factory assembles a {@link BaseWebRTCClient} with three desktop-specific
 * strategy implementations:</p>
 * <ul>
 *   <li>{@link DesktopPeerConnectionProvider} -- manages native RTCPeerConnection
 *       instances via the webrtc-java API.</li>
 *   <li>{@link DesktopSignalingProvider} -- handles WebSocket signaling via the
 *       Java-WebSocket library.</li>
 *   <li>{@link ExecutorScheduler} -- schedules delayed tasks (e.g., ICE restart
 *       timers) on a daemon thread.</li>
 * </ul>
 *
 * <h3>Usage</h3>
 * <pre>
 * // In your desktop launcher:
 * WebRTCClients.FACTORY = new DesktopWebRTCFactory();
 * </pre>
 *
 * @see WebRTCFactory
 * @see BaseWebRTCClient
 * @see DesktopPeerConnectionProvider
 * @see DesktopSignalingProvider
 * @see ExecutorScheduler
 */
public class DesktopWebRTCFactory implements WebRTCFactory {

    /**
     * {@inheritDoc}
     *
     * <p>Creates a new {@link BaseWebRTCClient} configured with desktop-specific
     * provider implementations. The returned client is ready to connect to a
     * signaling server and establish peer-to-peer WebRTC connections.</p>
     *
     * @param config   the WebRTC configuration containing signaling server URL,
     *                 ICE server settings, TURN credentials, and relay policy
     * @param listener callbacks for connection lifecycle events, peer discovery,
     *                 and incoming data
     * @return a new {@link WebRTCClient} instance backed by the desktop WebRTC stack
     */
    public WebRTCClient createClient(WebRTCConfiguration config, WebRTCClientListener listener) {
        return new BaseWebRTCClient("[WebRTC-Desktop] ", config, listener,
                new DesktopPeerConnectionProvider(),
                new DesktopSignalingProvider(),
                new ExecutorScheduler());
    }

    /**
     * {@inheritDoc}
     *
     * <p>Creates a client transport backed by the desktop WebRTC stack
     * (webrtc-java). Uses external signaling — no
     * {@link DesktopSignalingProvider} is used.</p>
     */
    public WebRTCClientTransport createClientTransport(WebRTCConfiguration config) {
        return new BaseWebRTCClientTransport("[WebRTC-Desktop] ", config,
                new DesktopPeerConnectionProvider(),
                new ExecutorScheduler());
    }

    /**
     * {@inheritDoc}
     *
     * <p>Creates a server transport backed by the desktop WebRTC stack
     * (webrtc-java). Uses external signaling — no
     * {@link DesktopSignalingProvider} is used.</p>
     */
    public WebRTCServerTransport createServerTransport(WebRTCConfiguration config) {
        return new BaseWebRTCServerTransport("[WebRTC-Desktop] ", config,
                new DesktopPeerConnectionProvider(),
                new ExecutorScheduler());
    }
}
