package com.github.satori87.gdx.webrtc;

/**
 * Static entry point for creating {@link WebRTCClient} instances.
 *
 * <p>Platform modules must set the {@link #FACTORY} field during initialization
 * before calling {@link #newClient(WebRTCConfiguration, WebRTCClientListener)}.
 * This decouples platform-agnostic game code from platform-specific WebRTC
 * implementations.</p>
 *
 * <p>Usage example:</p>
 * <pre>
 * // In platform launcher (e.g. DesktopLauncher.java):
 * WebRTCClients.FACTORY = new DesktopWebRTCFactory();
 *
 * // In platform launcher (e.g. AndroidLauncher.java):
 * WebRTCClients.FACTORY = new AndroidWebRTCFactory(this);
 *
 * // In platform launcher (e.g. TeaVM main):
 * WebRTCClients.FACTORY = new TeaVMWebRTCFactory();
 *
 * // In platform launcher (e.g. IOSLauncher.java):
 * WebRTCClients.FACTORY = new IOSWebRTCFactory();
 *
 * // Then in shared/platform-agnostic code:
 * WebRTCConfiguration config = new WebRTCConfiguration();
 * config.signalingServerUrl = "ws://myserver.com:9090";
 * WebRTCClient client = WebRTCClients.newClient(config, myListener);
 * client.connect();
 *
 * // Or use the high-level client/server API:
 * WebRTCServer server = WebRTCClients.newServer(config, serverListener);
 * server.start();
 *
 * WebRTCGameClient gameClient = WebRTCClients.newGameClient(config, clientListener);
 * gameClient.connect();
 * </pre>
 *
 * @see WebRTCFactory
 * @see WebRTCClient
 * @see WebRTCServer
 * @see WebRTCGameClient
 */
public class WebRTCClients {

    /**
     * The platform-specific factory used to create WebRTC clients.
     *
     * <p>Must be set before calling {@link #newClient(WebRTCConfiguration, WebRTCClientListener)}.
     * Typically set once during application startup in the platform launcher.</p>
     */
    public static WebRTCFactory FACTORY;

    /**
     * Creates a new {@link WebRTCClient} using the platform factory.
     *
     * <p>The returned client is not yet connected; call {@link WebRTCClient#connect()}
     * to initiate the signaling server connection.</p>
     *
     * @param config   the WebRTC and signaling configuration
     * @param listener the listener for connection and data events
     * @return a new, unconnected WebRTC client
     * @throws IllegalStateException if {@link #FACTORY} has not been set
     */
    public static WebRTCClient newClient(WebRTCConfiguration config, WebRTCClientListener listener) {
        if (FACTORY == null) {
            throw new IllegalStateException(
                "WebRTCClients.FACTORY is not set. "
                + "Call WebRTCClients.FACTORY = new DesktopWebRTCFactory() "
                + "or the platform equivalent before use.");
        }
        return FACTORY.createClient(config, listener);
    }

    /**
     * Creates a new {@link WebRTCServer} for client/server communication.
     *
     * <p>The server wraps a {@link WebRTCClient} internally and automatically
     * connects to all peers that join the signaling server. Call
     * {@link WebRTCServer#start()} to begin listening.</p>
     *
     * @param config   the WebRTC and signaling configuration
     * @param listener the listener for server events
     * @return a new, unstarted WebRTC server
     * @throws IllegalStateException if {@link #FACTORY} has not been set
     */
    public static WebRTCServer newServer(WebRTCConfiguration config, WebRTCServerListener listener) {
        if (FACTORY == null) {
            throw new IllegalStateException(
                "WebRTCClients.FACTORY is not set. "
                + "Call WebRTCClients.FACTORY = new DesktopWebRTCFactory() "
                + "or the platform equivalent before use.");
        }
        return new WebRTCServer(config, listener);
    }

    /**
     * Creates a new {@link WebRTCGameClient} for client/server communication.
     *
     * <p>The client wraps a {@link WebRTCClient} internally and waits for the
     * server to initiate the peer-to-peer connection. Call
     * {@link WebRTCGameClient#connect()} to connect to signaling.</p>
     *
     * @param config   the WebRTC and signaling configuration
     * @param listener the listener for client events
     * @return a new, unconnected game client
     * @throws IllegalStateException if {@link #FACTORY} has not been set
     */
    public static WebRTCGameClient newGameClient(WebRTCConfiguration config, WebRTCGameClientListener listener) {
        if (FACTORY == null) {
            throw new IllegalStateException(
                "WebRTCClients.FACTORY is not set. "
                + "Call WebRTCClients.FACTORY = new DesktopWebRTCFactory() "
                + "or the platform equivalent before use.");
        }
        return new WebRTCGameClient(config, listener);
    }
}
