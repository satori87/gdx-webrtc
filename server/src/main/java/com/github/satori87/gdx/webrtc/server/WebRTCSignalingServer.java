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
 * WebRTC signaling server. Relays SDP offers/answers and ICE candidates
 * between peers to establish WebRTC connections.
 *
 * <pre>
 * // Embeddable:
 * WebRTCSignalingServer server = new WebRTCSignalingServer(9090);
 * server.start();
 * </pre>
 */
public class WebRTCSignalingServer {

    private static final String TAG = "[Signaling] ";

    private final int port;
    private WebSocketServer wsServer;
    private final AtomicInteger nextPeerId = new AtomicInteger(1);
    private final Map<WebSocket, Integer> connToPeer = new ConcurrentHashMap<WebSocket, Integer>();
    private final Map<Integer, WebSocket> peerToConn = new ConcurrentHashMap<Integer, WebSocket>();

    public WebRTCSignalingServer(int port) {
        this.port = port;
    }

    public void start() {
        wsServer = new WebSocketServer(new InetSocketAddress(port)) {
            @Override
            public void onOpen(WebSocket conn, ClientHandshake handshake) {
                int peerId = nextPeerId.getAndIncrement();
                connToPeer.put(conn, peerId);
                peerToConn.put(peerId, conn);

                System.out.println(TAG + "Peer " + peerId + " connected from " + conn.getRemoteSocketAddress());

                // Send WELCOME with assigned peer ID
                SignalMessage welcome = new SignalMessage(SignalMessage.TYPE_WELCOME, 0, peerId, String.valueOf(peerId));
                conn.send(welcome.toJson());

                // Broadcast PEER_JOINED to all other connected peers
                SignalMessage joined = new SignalMessage(SignalMessage.TYPE_PEER_JOINED, peerId, 0, String.valueOf(peerId));
                String joinedJson = joined.toJson();
                for (Map.Entry<WebSocket, Integer> entry : connToPeer.entrySet()) {
                    if (entry.getValue() != peerId) {
                        try {
                            entry.getKey().send(joinedJson);
                        } catch (Exception e) {
                            // Ignore send failures
                        }
                    }
                }
            }

            @Override
            public void onClose(WebSocket conn, int code, String reason, boolean remote) {
                Integer peerId = connToPeer.remove(conn);
                if (peerId != null) {
                    peerToConn.remove(peerId);
                    System.out.println(TAG + "Peer " + peerId + " disconnected");

                    // Broadcast PEER_LEFT to remaining peers
                    SignalMessage left = new SignalMessage(SignalMessage.TYPE_PEER_LEFT, peerId, 0, String.valueOf(peerId));
                    String leftJson = left.toJson();
                    for (Map.Entry<WebSocket, Integer> entry : connToPeer.entrySet()) {
                        try {
                            entry.getKey().send(leftJson);
                        } catch (Exception e) {
                            // Ignore send failures
                        }
                    }
                }
            }

            @Override
            public void onMessage(WebSocket conn, String message) {
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
                        if (id != sourcePeerId) {
                            if (sb.length() > 0) sb.append(",");
                            sb.append(id);
                        }
                    }
                    SignalMessage list = new SignalMessage(SignalMessage.TYPE_PEER_LIST, 0, sourcePeerId, sb.toString());
                    conn.send(list.toJson());
                    return;
                }

                // Relay to target peer
                int targetId = msg.target;
                WebSocket targetConn = peerToConn.get(targetId);
                if (targetConn != null && targetConn.isOpen()) {
                    targetConn.send(msg.toJson());
                } else {
                    // Target not found
                    SignalMessage err = new SignalMessage(SignalMessage.TYPE_ERROR, 0, sourcePeerId,
                            "Peer " + targetId + " not found");
                    conn.send(err.toJson());
                }
            }

            @Override
            public void onError(WebSocket conn, Exception ex) {
                System.err.println(TAG + "Error: " + ex.getMessage());
            }

            @Override
            public void onStart() {
                System.out.println(TAG + "Signaling server started on port " + port);
            }
        };
        wsServer.setConnectionLostTimeout(30);
        wsServer.start();
    }

    public void stop() {
        if (wsServer != null) {
            try {
                wsServer.stop(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        System.out.println(TAG + "Signaling server stopped");
    }

    /** Get the number of currently connected peers. */
    public int getPeerCount() {
        return connToPeer.size();
    }
}
