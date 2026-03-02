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
 * Uses WebRTCServerTransport for data and ChatSignalServer for signaling.
 * The host participates in the chat as [SERVER].
 */
public class EmbeddedChatServer {

    public interface HostCallback {
        void onStarted();
        void onMessageReceived(String message);
        void onError(String error);
    }

    private final ChatSignalServer signalServer;
    private final HostCallback callback;
    private WebRTCServerTransport serverTransport;

    private final Object lock = new Object();
    private final Map<Integer, Integer> signalToConn = new HashMap<Integer, Integer>();
    private final Map<Integer, Integer> connToSignal = new HashMap<Integer, Integer>();
    private final Map<Integer, String> connToName = new HashMap<Integer, String>();
    private int userCounter = 0;
    private boolean running = false;

    public EmbeddedChatServer(ChatSignalServer signalServer, HostCallback callback) {
        this.signalServer = signalServer;
        this.callback = callback;
    }

    public void start(int port) {
        WebRTCConfiguration config = new WebRTCConfiguration();
        serverTransport = WebRTCTransports.newServerTransport(config);
        serverTransport.setListener(new ServerTransportListener() {
            public void onClientConnected(int connId) {
                final String name;
                synchronized (lock) {
                    name = connToName.containsKey(connId) ? connToName.get(connId) : "Unknown";
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
                    Integer signalId = connToSignal.remove(connId);
                    if (signalId != null) signalToConn.remove(signalId);
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

        signalServer.start(port, new ChatSignalServer.Listener() {
            public void onClientConnected(final int signalClientId) {
                final String name;
                synchronized (lock) {
                    userCounter++;
                    name = "User " + userCounter;
                }

                int connId = serverTransport.createPeerForOffer(new WebRTCServerTransport.SignalCallback() {
                    public void onOffer(int connId, String sdpOffer) {
                        signalServer.send(signalClientId, "{\"type\":\"offer\",\"sdp\":\""
                                + SignalMessage.escapeJson(sdpOffer) + "\"}");
                    }

                    public void onIceCandidate(int connId, String iceJson) {
                        signalServer.send(signalClientId, "{\"type\":\"ice\",\"candidate\":\""
                                + SignalMessage.escapeJson(iceJson) + "\"}");
                    }
                });

                synchronized (lock) {
                    signalToConn.put(signalClientId, connId);
                    connToSignal.put(connId, signalClientId);
                    connToName.put(connId, name);
                }
            }

            public void onClientMessage(int signalClientId, String text) {
                final int connId;
                synchronized (lock) {
                    Integer c = signalToConn.get(signalClientId);
                    if (c == null) return;
                    connId = c;
                }
                String type = SignalMessage.extractString(text, "type");
                if ("answer".equals(type)) {
                    String sdp = SignalMessage.extractString(text, "sdp");
                    serverTransport.setAnswer(connId, sdp);
                } else if ("ice".equals(type)) {
                    String candidate = SignalMessage.extractString(text, "candidate");
                    serverTransport.addIceCandidate(connId, candidate);
                }
            }

            public void onClientDisconnected(int signalClientId) {
                final int connId;
                synchronized (lock) {
                    Integer c = signalToConn.remove(signalClientId);
                    if (c == null) return;
                    connId = c;
                    connToSignal.remove(connId);
                }
                serverTransport.disconnect(connId);
            }
        });

        running = true;
        Gdx.app.postRunnable(new Runnable() {
            public void run() {
                callback.onStarted();
            }
        });
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
        signalServer.stop();
        if (serverTransport != null) {
            serverTransport.stop();
            serverTransport = null;
        }
        synchronized (lock) {
            signalToConn.clear();
            connToSignal.clear();
            connToName.clear();
        }
    }
}
