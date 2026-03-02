package com.github.satori87.gdx.webrtc.transport;

import com.github.satori87.gdx.webrtc.WebRTCClients;
import com.github.satori87.gdx.webrtc.WebRTCConfiguration;

/**
 * Static entry point for creating WebRTC transport instances.
 *
 * <p>This class is the transport equivalent of
 * {@link com.github.satori87.gdx.webrtc.WebRTCClients}. It uses the same
 * platform-specific factory set on {@link WebRTCClients#FACTORY} to create
 * client and server transports.</p>
 *
 * <p>Unlike the peer-to-peer {@link com.github.satori87.gdx.webrtc.WebRTCClient},
 * transports use <em>external signaling</em> — the application is responsible
 * for relaying SDP offers/answers and ICE candidates through its own signaling
 * channel (e.g. a game lobby or matchmaker).</p>
 *
 * <h3>Usage Example</h3>
 * <pre>
 * // Set up the platform factory (same as for WebRTCClients):
 * WebRTCClients.FACTORY = new DesktopWebRTCFactory();
 *
 * // Create a client transport:
 * WebRTCConfiguration clientConfig = new WebRTCConfiguration();
 * clientConfig.turnServer = "turn:myserver.com:3478";
 * clientConfig.turnUsername = "user";
 * clientConfig.turnPassword = "pass";
 * WebRTCClientTransport client = WebRTCTransports.newClientTransport(clientConfig);
 *
 * // Create a server transport (with faster ICE restart for LAN):
 * WebRTCConfiguration serverConfig = new WebRTCConfiguration();
 * serverConfig.iceRestartDelayMs = 500;
 * WebRTCServerTransport server = WebRTCTransports.newServerTransport(serverConfig);
 * </pre>
 *
 * @see WebRTCClientTransport
 * @see WebRTCServerTransport
 * @see com.github.satori87.gdx.webrtc.WebRTCClients
 * @see com.github.satori87.gdx.webrtc.WebRTCFactory
 */
public class WebRTCTransports {

    /**
     * Creates a new {@link WebRTCClientTransport} using the platform factory.
     *
     * <p>The returned transport is not connected. Call
     * {@link WebRTCClientTransport#connectWithOffer(String, WebRTCClientTransport.SignalCallback)}
     * to initiate the WebRTC handshake when an SDP offer is received.</p>
     *
     * @param config the WebRTC configuration containing ICE server settings
     *               and connection parameters
     * @return a new, unconnected client transport
     * @throws IllegalStateException if {@link WebRTCClients#FACTORY} has not been set
     */
    public static WebRTCClientTransport newClientTransport(WebRTCConfiguration config) {
        if (WebRTCClients.FACTORY == null) {
            throw new IllegalStateException(
                    "WebRTCClients.FACTORY is not set. "
                    + "Call WebRTCClients.FACTORY = new DesktopWebRTCFactory() "
                    + "or the platform equivalent before use.");
        }
        return WebRTCClients.FACTORY.createClientTransport(config);
    }

    /**
     * Creates a new {@link WebRTCServerTransport} using the platform factory.
     *
     * <p>The returned transport is ready to accept clients. Call
     * {@link WebRTCServerTransport#createPeerForOffer(WebRTCServerTransport.SignalCallback)}
     * to create a peer connection for each incoming client.</p>
     *
     * @param config the WebRTC configuration containing ICE server settings
     *               and connection parameters
     * @return a new server transport ready to accept clients
     * @throws IllegalStateException if {@link WebRTCClients#FACTORY} has not been set
     */
    public static WebRTCServerTransport newServerTransport(WebRTCConfiguration config) {
        if (WebRTCClients.FACTORY == null) {
            throw new IllegalStateException(
                    "WebRTCClients.FACTORY is not set. "
                    + "Call WebRTCClients.FACTORY = new DesktopWebRTCFactory() "
                    + "or the platform equivalent before use.");
        }
        return WebRTCClients.FACTORY.createServerTransport(config);
    }
}
