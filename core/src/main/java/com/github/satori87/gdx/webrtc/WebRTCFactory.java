package com.github.satori87.gdx.webrtc;

import com.github.satori87.gdx.webrtc.transport.WebRTCClientTransport;
import com.github.satori87.gdx.webrtc.transport.WebRTCServerTransport;

/**
 * Factory interface for creating platform-specific WebRTC instances.
 *
 * <p>Each platform module provides an implementation of this interface:</p>
 * <ul>
 *   <li>{@code DesktopWebRTCFactory} (lwjgl3 module) - Desktop/JVM via webrtc-java</li>
 *   <li>{@code TeaVMWebRTCFactory} (teavm module) - Browser via JavaScript WebRTC API</li>
 *   <li>{@code AndroidWebRTCFactory} (android module) - Android via Google WebRTC SDK</li>
 *   <li>{@code IOSWebRTCFactory} (ios module) - iOS via WebRTC.framework + RoboVM</li>
 * </ul>
 *
 * <p>Set the appropriate factory on {@link WebRTCClients#FACTORY} during platform
 * initialization, then use {@link WebRTCClients#newClient(WebRTCConfiguration, WebRTCClientListener)}
 * to create peer-to-peer clients or
 * {@link com.github.satori87.gdx.webrtc.transport.WebRTCTransports} to create
 * client/server transports.</p>
 *
 * @see WebRTCClients
 * @see com.github.satori87.gdx.webrtc.transport.WebRTCTransports
 */
public interface WebRTCFactory {

    /**
     * Creates a new {@link WebRTCClient} with the given configuration and listener.
     *
     * <p>The returned client is not yet connected; call {@link WebRTCClient#connect()}
     * to initiate the signaling connection.</p>
     *
     * @param config   the WebRTC and signaling configuration
     * @param listener the listener for connection and data events
     * @return a new, unconnected WebRTC client
     */
    WebRTCClient createClient(WebRTCConfiguration config, WebRTCClientListener listener);

    /**
     * Creates a new {@link WebRTCClientTransport} for connecting to a server
     * via external signaling.
     *
     * <p>The returned transport does not use the library's built-in signaling.
     * The application is responsible for relaying SDP/ICE through its own
     * signaling channel.</p>
     *
     * @param config the WebRTC configuration containing ICE server settings
     * @return a new, unconnected client transport
     * @see com.github.satori87.gdx.webrtc.transport.WebRTCTransports#newClientTransport(WebRTCConfiguration)
     */
    WebRTCClientTransport createClientTransport(WebRTCConfiguration config);

    /**
     * Creates a new {@link WebRTCServerTransport} for accepting client connections
     * via external signaling.
     *
     * <p>The returned transport does not use the library's built-in signaling.
     * The application is responsible for relaying SDP/ICE through its own
     * signaling channel.</p>
     *
     * @param config the WebRTC configuration containing ICE server settings
     * @return a new server transport ready to accept clients
     * @see com.github.satori87.gdx.webrtc.transport.WebRTCTransports#newServerTransport(WebRTCConfiguration)
     */
    WebRTCServerTransport createServerTransport(WebRTCConfiguration config);
}
