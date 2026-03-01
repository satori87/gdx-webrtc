package com.github.satori87.gdx.webrtc.server;

import com.github.satori87.gdx.webrtc.SignalMessage;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * WebSocket-based signaling server that facilitates WebRTC peer-to-peer
 * connection establishment by relaying {@link SignalMessage} instances
 * between connected clients.
 *
 * <p>The server acts as a "dumb relay": it assigns each connecting client a
 * unique numeric peer ID, notifies all peers when someone joins or leaves,
 * and forwards SDP offers/answers and ICE candidates from one peer to
 * another. It does not inspect or modify the SDP/ICE payloads.</p>
 *
 * <h3>Protocol overview</h3>
 * <ol>
 *   <li>A client connects via WebSocket and receives a {@code WELCOME}
 *       message containing its assigned peer ID.</li>
 *   <li>The server sends {@code PEER_JOINED} messages to the new client
 *       for every already-connected peer, and broadcasts a
 *       {@code PEER_JOINED} to all existing peers about the newcomer.</li>
 *   <li>When a client sends a signaling message (e.g. {@code OFFER},
 *       {@code ANSWER}, {@code ICE}, {@code CONNECT_REQUEST}), the server
 *       stamps the source peer ID and forwards it to the target peer.</li>
 *   <li>A {@code PEER_LIST} request returns a comma-separated list of all
 *       other connected peer IDs.</li>
 *   <li>When a client disconnects, a {@code PEER_LEFT} message is
 *       broadcast to all remaining peers.</li>
 * </ol>
 *
 * <h3>Thread safety</h3>
 * <p>Peer-to-connection mappings are stored in {@link ConcurrentHashMap}
 * instances, and peer IDs are generated via an {@link AtomicInteger},
 * making the server safe for concurrent WebSocket callbacks.</p>
 *
 * <h3>Usage</h3>
 * <pre>
 * // Embeddable in any Java application:
 * WebRTCSignalingServer server = new WebRTCSignalingServer(9090);
 * server.start();
 * // ...
 * server.stop();
 * </pre>
 *
 * @see SignalMessage
 * @see SignalingMain
 * @see SignalingServerConfig
 * @see SignalingConnection
 */
public class WebRTCSignalingServer {

    /** Log prefix used for all console output from this server. */
    private static final String TAG = "[Signaling] ";

    /** Configuration for this server instance. */
    private final SignalingServerConfig config;

    /** The underlying java-websocket server instance, created on {@link #start()}. */
    private WebSocketServer wsServer;

    /** Atomic counter used to generate monotonically-increasing peer IDs. */
    private final AtomicInteger nextPeerId = new AtomicInteger(1);

    /** Maps each open connection to its assigned peer ID. */
    private final Map<SignalingConnection, Integer> connToPeer =
            new ConcurrentHashMap<SignalingConnection, Integer>();

    /** Maps each assigned peer ID back to its connection. */
    private final Map<Integer, SignalingConnection> peerToConn =
            new ConcurrentHashMap<Integer, SignalingConnection>();

    /**
     * Creates a new signaling server with the given configuration.
     * The server does not start until {@link #start()} is called.
     *
     * @param config the server configuration
     */
    public WebRTCSignalingServer(SignalingServerConfig config) {
        this.config = config;
    }

    /**
     * Creates a new signaling server that will listen on the specified port
     * with default configuration.
     *
     * @param port the TCP port to bind the WebSocket server to
     */
    public WebRTCSignalingServer(int port) {
        this.config = new SignalingServerConfig();
        this.config.port = port;
    }

    /**
     * Creates and starts the underlying WebSocket server.
     *
     * <p>Once started, the server begins accepting client connections on the
     * configured port. A connection-lost timeout is set per
     * {@link SignalingServerConfig#connectionLostTimeout}.</p>
     *
     * <p>This method returns immediately; the WebSocket server runs on its
     * own threads.</p>
     */
    public void start() {
        wsServer = new WebSocketServer(new InetSocketAddress(config.port)) {
            @Override
            public void onOpen(WebSocket conn, ClientHandshake handshake) {
                handleOpen(new WebSocketSignalingConnection(conn));
            }

            @Override
            public void onClose(WebSocket conn, int code, String reason, boolean remote) {
                handleClose(new WebSocketSignalingConnection(conn));
            }

            @Override
            public void onMessage(WebSocket conn, String message) {
                handleMessage(new WebSocketSignalingConnection(conn), message);
            }

            @Override
            public void onError(WebSocket conn, Exception ex) {
                System.err.println(TAG + "Error: " + ex.getMessage());
            }

            @Override
            public void onStart() {
                System.out.println(TAG + "Signaling server started on port " + config.port);
            }
        };
        wsServer.setConnectionLostTimeout(config.connectionLostTimeout);
        wsServer.start();
    }

    /**
     * Gracefully stops the WebSocket server, closing all active connections.
     *
     * <p>The server is given up to {@link SignalingServerConfig#stopTimeoutMs}
     * milliseconds to complete the shutdown.</p>
     */
    public void stop() {
        if (wsServer != null) {
            try {
                wsServer.stop(config.stopTimeoutMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        System.out.println(TAG + "Signaling server stopped");
    }

    /**
     * Returns the number of peers currently connected to this signaling server.
     *
     * @return the count of active peer connections
     */
    public int getPeerCount() {
        return connToPeer.size();
    }

    // --- Testable message handling methods (package-private) ---

    /**
     * Handles a new client connection: assigns a peer ID, sends WELCOME,
     * notifies the new peer about existing peers, and broadcasts PEER_JOINED
     * to all existing peers.
     *
     * @param conn the new connection
     */
    void handleOpen(SignalingConnection conn) {
        int peerId = nextPeerId.getAndIncrement();
        connToPeer.put(conn, peerId);
        peerToConn.put(peerId, conn);

        System.out.println(TAG + "Peer " + peerId + " connected from " + conn.getRemoteSocketAddress());

        // Send WELCOME with assigned peer ID
        SignalMessage welcome = new SignalMessage(
                SignalMessage.TYPE_WELCOME, 0, peerId, String.valueOf(peerId));
        conn.send(welcome.toJson());

        // Notify the new peer about every already-connected peer
        for (Map.Entry<SignalingConnection, Integer> entry : connToPeer.entrySet()) {
            int existingId = entry.getValue();
            if (existingId != peerId) {
                try {
                    SignalMessage existing = new SignalMessage(
                            SignalMessage.TYPE_PEER_JOINED, existingId, peerId,
                            String.valueOf(existingId));
                    conn.send(existing.toJson());
                } catch (Exception e) {
                    // Ignore send failures
                }
            }
        }

        // Broadcast PEER_JOINED to all other connected peers
        SignalMessage joined = new SignalMessage(
                SignalMessage.TYPE_PEER_JOINED, peerId, 0, String.valueOf(peerId));
        String joinedJson = joined.toJson();
        for (Map.Entry<SignalingConnection, Integer> entry : connToPeer.entrySet()) {
            if (entry.getValue() != peerId) {
                try {
                    entry.getKey().send(joinedJson);
                } catch (Exception e) {
                    // Ignore send failures
                }
            }
        }
    }

    /**
     * Handles a client disconnection: removes the peer from maps and
     * broadcasts PEER_LEFT to all remaining peers.
     *
     * @param conn the disconnected connection
     */
    void handleClose(SignalingConnection conn) {
        Integer peerId = connToPeer.remove(conn);
        if (peerId != null) {
            peerToConn.remove(peerId);
            System.out.println(TAG + "Peer " + peerId + " disconnected");

            // Broadcast PEER_LEFT to remaining peers
            SignalMessage left = new SignalMessage(
                    SignalMessage.TYPE_PEER_LEFT, peerId, 0, String.valueOf(peerId));
            String leftJson = left.toJson();
            for (Map.Entry<SignalingConnection, Integer> entry : connToPeer.entrySet()) {
                try {
                    entry.getKey().send(leftJson);
                } catch (Exception e) {
                    // Ignore send failures
                }
            }
        }
    }

    /**
     * Handles an incoming message from a client: parses it, stamps the source
     * peer ID, and either responds to PEER_LIST requests or relays the message
     * to the target peer.
     *
     * @param conn    the connection that sent the message
     * @param message the raw JSON message string
     */
    void handleMessage(SignalingConnection conn, String message) {
        Integer sourcePeerId = connToPeer.get(conn);
        if (sourcePeerId == null) return;

        SignalMessage msg = SignalMessage.fromJson(message);
        if (msg == null) return;

        // Stamp source
        msg.source = sourcePeerId;

        if (msg.type == SignalMessage.TYPE_PEER_LIST) {
            // Respond with comma-separated list of connected peer IDs
            StringBuilder sb = new StringBuilder();
            for (Integer id : peerToConn.keySet()) {
                if (!id.equals(sourcePeerId)) {
                    if (sb.length() > 0) sb.append(",");
                    sb.append(id);
                }
            }
            SignalMessage list = new SignalMessage(
                    SignalMessage.TYPE_PEER_LIST, 0, sourcePeerId, sb.toString());
            conn.send(list.toJson());
            return;
        }

        // Relay to target peer
        int targetId = msg.target;
        SignalingConnection targetConn = peerToConn.get(targetId);
        if (targetConn != null && targetConn.isOpen()) {
            targetConn.send(msg.toJson());
        } else {
            // Target not found
            SignalMessage err = new SignalMessage(SignalMessage.TYPE_ERROR, 0, sourcePeerId,
                    "Peer " + targetId + " not found");
            conn.send(err.toJson());
        }
    }

    // --- Package-private accessors for testing ---

    /**
     * Returns the configuration. Package-private for testing.
     *
     * @return the server configuration
     */
    SignalingServerConfig getConfig() {
        return config;
    }

    /**
     * Returns the connection-to-peer map. Package-private for testing.
     *
     * @return the connection-to-peer ID map
     */
    Map<SignalingConnection, Integer> getConnToPeer() {
        return connToPeer;
    }

    /**
     * Returns the peer-to-connection map. Package-private for testing.
     *
     * @return the peer ID-to-connection map
     */
    Map<Integer, SignalingConnection> getPeerToConn() {
        return peerToConn;
    }

    // --- WebSocket adapter ---

    /**
     * Adapter that wraps a {@code org.java_websocket.WebSocket} as a
     * {@link SignalingConnection}.
     */
    private static class WebSocketSignalingConnection implements SignalingConnection {
        private final WebSocket ws;

        WebSocketSignalingConnection(WebSocket ws) {
            this.ws = ws;
        }

        public void send(String text) {
            ws.send(text);
        }

        public boolean isOpen() {
            return ws.isOpen();
        }

        public InetSocketAddress getRemoteSocketAddress() {
            return ws.getRemoteSocketAddress();
        }

        @Override
        public int hashCode() {
            return ws.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof WebSocketSignalingConnection)) return false;
            return ws.equals(((WebSocketSignalingConnection) obj).ws);
        }
    }
}
