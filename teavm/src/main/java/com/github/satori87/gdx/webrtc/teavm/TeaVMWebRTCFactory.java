package com.github.satori87.gdx.webrtc.teavm;

import com.github.satori87.gdx.webrtc.BaseWebRTCClient;
import com.github.satori87.gdx.webrtc.WebRTCClient;
import com.github.satori87.gdx.webrtc.WebRTCClientListener;
import com.github.satori87.gdx.webrtc.WebRTCConfiguration;
import com.github.satori87.gdx.webrtc.WebRTCFactory;

/**
 * Factory for creating browser-based WebRTC clients using TeaVM's JavaScript interop.
 *
 * <p>This factory produces {@link BaseWebRTCClient} instances wired with browser-specific
 * provider implementations:</p>
 * <ul>
 *   <li>{@link TeaVMPeerConnectionProvider} -- delegates to the native {@code RTCPeerConnection}
 *       API via {@code @JSBody} inline JavaScript</li>
 *   <li>{@link TeaVMSignalingProvider} -- delegates to the native {@code WebSocket} API for
 *       signaling server communication</li>
 *   <li>{@link TeaVMScheduler} -- delegates to the browser's {@code setTimeout}/
 *       {@code clearTimeout} for timed operations (ICE restart delays, etc.)</li>
 * </ul>
 *
 * <h3>Usage</h3>
 * <pre>
 * // In your TeaVM launcher, before creating any clients:
 * WebRTCClients.FACTORY = new TeaVMWebRTCFactory();
 * WebRTCClient client = WebRTCClients.newClient(config, listener);
 * </pre>
 *
 * <p>No initialization parameters are required because the browser's WebRTC and WebSocket
 * APIs are always available and require no factory or context setup (unlike the Android
 * factory which needs an Android {@code Context}).</p>
 *
 * @see WebRTCFactory
 * @see BaseWebRTCClient
 * @see TeaVMPeerConnectionProvider
 * @see TeaVMSignalingProvider
 * @see TeaVMScheduler
 */
public class TeaVMWebRTCFactory implements WebRTCFactory {

    /**
     * Creates a new browser-based WebRTC client.
     *
     * <p>Instantiates a {@link BaseWebRTCClient} with the browser log prefix
     * {@code "[WebRTC-Browser] "} and the three TeaVM provider implementations. The
     * returned client is ready to connect to a signaling server and establish
     * peer-to-peer connections.</p>
     *
     * @param config   the WebRTC configuration containing STUN/TURN server URLs,
     *                 credentials, and connection preferences
     * @param listener the listener to receive connection lifecycle and data events
     * @return a new {@link WebRTCClient} configured for the browser platform
     */
    public WebRTCClient createClient(WebRTCConfiguration config, WebRTCClientListener listener) {
        return new BaseWebRTCClient("[WebRTC-Browser] ", config, listener,
                new TeaVMPeerConnectionProvider(),
                new TeaVMSignalingProvider(),
                new TeaVMScheduler());
    }
}
