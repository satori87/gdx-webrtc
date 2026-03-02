package studio.whitlock.webrtc.chat.server;

import com.github.satori87.gdx.webrtc.SignalMessage;
import com.github.satori87.gdx.webrtc.WebRTCClients;
import com.github.satori87.gdx.webrtc.WebRTCConfiguration;
import com.github.satori87.gdx.webrtc.common.DesktopWebRTCFactory;
import com.github.satori87.gdx.webrtc.transport.ServerTransportListener;
import com.github.satori87.gdx.webrtc.transport.WebRTCServerTransport;
import com.github.satori87.gdx.webrtc.transport.WebRTCTransports;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Chat server that uses WebRTCServerTransport for data and WebSocket for signaling.
 *
 * <p>Each client connects via WebSocket, receives an SDP offer, sends back an answer,
 * and exchanges ICE candidates. Once the WebRTC connection is established, chat messages
 * flow over the WebRTC DataChannel. The server broadcasts messages to all other clients.</p>
 */
public class ChatServer {

    private static final String TAG = "[ChatServer] ";

    private final int port;
    private WebSocketServer wsServer;
    private WebRTCServerTransport serverTransport;

    private final Map<WebSocket, Integer> wsToConnId = new ConcurrentHashMap<WebSocket, Integer>();
    private final Map<Integer, WebSocket> connIdToWs = new ConcurrentHashMap<Integer, WebSocket>();
    private final AtomicInteger userCounter = new AtomicInteger(1);
    private final Map<Integer, String> connIdToName = new ConcurrentHashMap<Integer, String>();

    public ChatServer(int port) {
        this.port = port;
    }

    public void start() {
        WebRTCClients.FACTORY = new DesktopWebRTCFactory();

        WebRTCConfiguration config = new WebRTCConfiguration();
        serverTransport = WebRTCTransports.newServerTransport(config);
        serverTransport.setListener(new ServerTransportListener() {
            public void onClientConnected(int connId) {
                String name = connIdToName.get(Integer.valueOf(connId));
                if (name == null) name = "Unknown";
                System.out.println(TAG + name + " (conn " + connId + ") WebRTC connected");

                String announcement = "-- " + name + " joined --";
                byte[] data = announcement.getBytes(StandardCharsets.UTF_8);
                for (Map.Entry<Integer, String> entry : connIdToName.entrySet()) {
                    int otherId = entry.getKey().intValue();
                    if (otherId != connId) {
                        serverTransport.sendReliable(otherId, data);
                    }
                }
            }

            public void onClientDisconnected(int connId) {
                String name = connIdToName.remove(Integer.valueOf(connId));
                if (name == null) name = "Unknown";
                System.out.println(TAG + name + " (conn " + connId + ") disconnected");

                WebSocket ws = connIdToWs.remove(Integer.valueOf(connId));
                if (ws != null) {
                    wsToConnId.remove(ws);
                }

                String announcement = "-- " + name + " left --";
                serverTransport.broadcastReliable(announcement.getBytes(StandardCharsets.UTF_8));
            }

            public void onClientMessage(int connId, byte[] data, boolean reliable) {
                String text = new String(data, StandardCharsets.UTF_8);
                String name = connIdToName.get(Integer.valueOf(connId));
                if (name == null) name = "Unknown";

                String broadcastMsg = name + ": " + text;
                System.out.println(TAG + broadcastMsg);

                byte[] broadcastData = broadcastMsg.getBytes(StandardCharsets.UTF_8);
                for (Map.Entry<Integer, String> entry : connIdToName.entrySet()) {
                    int otherId = entry.getKey().intValue();
                    if (otherId != connId) {
                        serverTransport.sendReliable(otherId, broadcastData);
                    }
                }
            }
        });

        wsServer = new WebSocketServer(new InetSocketAddress(port)) {
            @Override
            public void onOpen(WebSocket conn, ClientHandshake handshake) {
                handleWsOpen(conn);
            }

            @Override
            public void onClose(WebSocket conn, int code, String reason, boolean remote) {
                handleWsClose(conn);
            }

            @Override
            public void onMessage(WebSocket conn, String message) {
                handleWsMessage(conn, message);
            }

            @Override
            public void onError(WebSocket conn, Exception ex) {
                System.err.println(TAG + "WebSocket error: " + ex.getMessage());
            }

            @Override
            public void onStart() {
                System.out.println(TAG + "Signaling server started on port " + port);
            }
        };
        wsServer.setConnectionLostTimeout(10);
        wsServer.start();
        System.out.println(TAG + "Chat server starting on port " + port);
    }

    private void handleWsOpen(final WebSocket ws) {
        System.out.println(TAG + "Client connected from " + ws.getRemoteSocketAddress());

        String name = "User " + userCounter.getAndIncrement();

        int connId = serverTransport.createPeerForOffer(
                new WebRTCServerTransport.SignalCallback() {
                    public void onOffer(int connId, String sdpOffer) {
                        if (ws.isOpen()) {
                            ws.send("{\"type\":\"offer\",\"sdp\":\""
                                    + SignalMessage.escapeJson(sdpOffer) + "\"}");
                        }
                    }

                    public void onIceCandidate(int connId, String iceJson) {
                        if (ws.isOpen()) {
                            ws.send("{\"type\":\"ice\",\"candidate\":\""
                                    + SignalMessage.escapeJson(iceJson) + "\"}");
                        }
                    }
                });

        wsToConnId.put(ws, Integer.valueOf(connId));
        connIdToWs.put(Integer.valueOf(connId), ws);
        connIdToName.put(Integer.valueOf(connId), name);

        System.out.println(TAG + "Created WebRTC peer " + connId + " (" + name + ")");
    }

    private void handleWsClose(WebSocket ws) {
        Integer connId = wsToConnId.remove(ws);
        if (connId != null) {
            connIdToWs.remove(connId);
            serverTransport.disconnect(connId.intValue());
        }
    }

    private void handleWsMessage(WebSocket ws, String json) {
        Integer connId = wsToConnId.get(ws);
        if (connId == null) return;

        String type = SignalMessage.extractString(json, "type");
        if ("answer".equals(type)) {
            String sdp = SignalMessage.extractString(json, "sdp");
            serverTransport.setAnswer(connId.intValue(), sdp);
        } else if ("ice".equals(type)) {
            String candidate = SignalMessage.extractString(json, "candidate");
            serverTransport.addIceCandidate(connId.intValue(), candidate);
        }
    }

    public void stop() {
        if (serverTransport != null) {
            serverTransport.stop();
        }
        if (wsServer != null) {
            try {
                wsServer.stop(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        System.out.println(TAG + "Chat server stopped");
    }

    public static void main(String[] args) {
        int port = 9090;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port: " + args[0] + ", using default 9090");
            }
        }

        final ChatServer server = new ChatServer(port);

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                System.out.println("Shutting down...");
                server.stop();
            }
        });

        server.start();
        System.out.println("Chat server running. Clients connect to ws://YOUR_IP:" + port);

        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            server.stop();
        }
    }
}
