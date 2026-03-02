package studio.whitlock.webrtc.chat;

import com.badlogic.gdx.Gdx;
import com.github.satori87.gdx.webrtc.SignalMessage;
import com.github.satori87.gdx.webrtc.WebRTCConfiguration;
import com.github.satori87.gdx.webrtc.transport.ServerTransportListener;
import com.github.satori87.gdx.webrtc.transport.WebRTCServerTransport;
import com.github.satori87.gdx.webrtc.transport.WebRTCTransports;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Embeddable chat server that runs inside the client application.
 * Connects to the gdx-webrtc signaling server as a WebSocket client,
 * uses WebRTCServerTransport for data channels.
 * The host participates in the chat as [SERVER].
 */
public class EmbeddedChatServer {

    public interface HostCallback {
        void onStarted();
        void onMessageReceived(String message);
        void onError(String error);
    }

    private final ChatSignalClient signalClient;
    private final HostCallback callback;
    private WebRTCServerTransport serverTransport;

    private final Object lock = new Object();
    /** Maps signaling peer ID → transport connection ID. */
    private final Map<Integer, Integer> peerToConn = new HashMap<Integer, Integer>();
    /** Maps transport connection ID → signaling peer ID. */
    private final Map<Integer, Integer> connToPeer = new HashMap<Integer, Integer>();
    /** Maps transport connection ID → user display name. */
    private final Map<Integer, String> connToName = new HashMap<Integer, String>();
    private int localPeerId = -1;
    private int userCounter = 0;
    private boolean running = false;

    public EmbeddedChatServer(ChatSignalClient signalClient, HostCallback callback) {
        this.signalClient = signalClient;
        this.callback = callback;
    }

    public void start(String relayUrl) {
        WebRTCConfiguration config = new WebRTCConfiguration();
        serverTransport = WebRTCTransports.newServerTransport(config);
        serverTransport.setListener(new ServerTransportListener() {
            public void onClientConnected(int connId) {
                final String name;
                synchronized (lock) {
                    String n = connToName.get(connId);
                    name = n != null ? n : "Unknown";
                }
                final String announcement = "-- " + name + " joined --";
                broadcastToClients(announcement);
                Gdx.app.postRunnable(new Runnable() {
                    public void run() {
                        callback.onMessageReceived(announcement);
                    }
                });
            }

            public void onClientDisconnected(int connId) {
                final String name;
                synchronized (lock) {
                    String n = connToName.remove(connId);
                    name = n != null ? n : "Unknown";
                    Integer peerId = connToPeer.remove(connId);
                    if (peerId != null) peerToConn.remove(peerId);
                }
                final String announcement = "-- " + name + " left --";
                broadcastToClients(announcement);
                Gdx.app.postRunnable(new Runnable() {
                    public void run() {
                        callback.onMessageReceived(announcement);
                    }
                });
            }

            public void onClientMessage(int connId, byte[] data, boolean reliable) {
                String text = new String(data, StandardCharsets.UTF_8);
                final String name;
                synchronized (lock) {
                    String n = connToName.get(connId);
                    name = n != null ? n : "Unknown";
                }
                final String formatted = name + ": " + text;
                broadcastToClientsExcept(connId, formatted);
                Gdx.app.postRunnable(new Runnable() {
                    public void run() {
                        callback.onMessageReceived(formatted);
                    }
                });
            }
        });

        signalClient.connect(relayUrl, new ChatSignalClient.Listener() {
            public void onOpen() {
                // Wait for WELCOME message with our peer ID
            }

            public void onMessage(String text) {
                handleSignalingMessage(text);
            }

            public void onClose(final String reason) {
                Gdx.app.postRunnable(new Runnable() {
                    public void run() {
                        callback.onError("Signaling closed: " + reason);
                    }
                });
            }

            public void onError(final String error) {
                Gdx.app.postRunnable(new Runnable() {
                    public void run() {
                        callback.onError(error);
                    }
                });
            }
        });
    }

    private void handleSignalingMessage(String json) {
        SignalMessage msg = SignalMessage.fromJson(json);
        if (msg == null) return;

        switch (msg.type) {
            case SignalMessage.TYPE_WELCOME:
                localPeerId = Integer.parseInt(msg.data);
                running = true;
                Gdx.app.postRunnable(new Runnable() {
                    public void run() {
                        callback.onStarted();
                    }
                });
                break;

            case SignalMessage.TYPE_PEER_JOINED:
                handlePeerJoined(msg.source);
                break;

            case SignalMessage.TYPE_PEER_LEFT:
                handlePeerLeft(msg.source);
                break;

            case SignalMessage.TYPE_CONNECT_REQUEST:
                handlePeerJoined(msg.source);
                break;

            case SignalMessage.TYPE_ANSWER:
                handleAnswer(msg.source, msg.data);
                break;

            case SignalMessage.TYPE_ICE:
                handleIce(msg.source, msg.data);
                break;
        }
    }

    private void handlePeerJoined(final int peerId) {
        synchronized (lock) {
            userCounter++;
            final String name = "User " + userCounter;

            int connId = serverTransport.createPeerForOffer(new WebRTCServerTransport.SignalCallback() {
                public void onOffer(int connId, String sdpOffer) {
                    SignalMessage offer = new SignalMessage(
                            SignalMessage.TYPE_OFFER, localPeerId, peerId, sdpOffer);
                    signalClient.send(offer.toJson());
                }

                public void onIceCandidate(int connId, String iceJson) {
                    SignalMessage ice = new SignalMessage(
                            SignalMessage.TYPE_ICE, localPeerId, peerId, iceJson);
                    signalClient.send(ice.toJson());
                }
            });

            peerToConn.put(peerId, connId);
            connToPeer.put(connId, peerId);
            connToName.put(connId, name);
        }
    }

    private void handlePeerLeft(int peerId) {
        synchronized (lock) {
            Integer connId = peerToConn.remove(peerId);
            if (connId != null) {
                connToPeer.remove(connId);
                serverTransport.disconnect(connId);
            }
        }
    }

    private void handleAnswer(int peerId, String sdp) {
        synchronized (lock) {
            Integer connId = peerToConn.get(peerId);
            if (connId != null) {
                serverTransport.setAnswer(connId, sdp);
            }
        }
    }

    private void handleIce(int peerId, String iceJson) {
        synchronized (lock) {
            Integer connId = peerToConn.get(peerId);
            if (connId != null) {
                serverTransport.addIceCandidate(connId, iceJson);
            }
        }
    }

    public void broadcastChat(String message) {
        final String formatted = "[SERVER]: " + message;
        broadcastToClients(formatted);
        Gdx.app.postRunnable(new Runnable() {
            public void run() {
                callback.onMessageReceived(formatted);
            }
        });
    }

    private void broadcastToClients(String message) {
        if (serverTransport != null) {
            serverTransport.broadcastReliable(message.getBytes(StandardCharsets.UTF_8));
        }
    }

    private void broadcastToClientsExcept(int excludeConnId, String message) {
        byte[] data = message.getBytes(StandardCharsets.UTF_8);
        synchronized (lock) {
            for (Integer connId : connToName.keySet()) {
                if (connId != excludeConnId) {
                    serverTransport.sendReliable(connId, data);
                }
            }
        }
    }

    public boolean isRunning() {
        return running;
    }

    public void stop() {
        running = false;
        signalClient.close();
        if (serverTransport != null) {
            serverTransport.stop();
            serverTransport = null;
        }
        synchronized (lock) {
            peerToConn.clear();
            connToPeer.clear();
            connToName.clear();
        }
    }
}
