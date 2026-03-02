package studio.whitlock.webrtc.chat;

import com.badlogic.gdx.Gdx;
import com.github.satori87.gdx.webrtc.SignalMessage;
import com.github.satori87.gdx.webrtc.WebRTCConfiguration;
import com.github.satori87.gdx.webrtc.transport.TransportListener;
import com.github.satori87.gdx.webrtc.transport.WebRTCClientTransport;
import com.github.satori87.gdx.webrtc.transport.WebRTCTransports;

import java.nio.charset.StandardCharsets;

/**
 * Manages a client connection to the chat server using WebRTCClientTransport
 * with external signaling over a WebSocket.
 */
public class ConnectionManager {

    public interface ChatCallback {
        void onConnected();
        void onDisconnected();
        void onMessageReceived(String message);
        void onError(String error);
    }

    private final ChatSignalClient signalClient;
    private final ChatCallback callback;
    private WebRTCClientTransport transport;
    private boolean connected;

    public ConnectionManager(ChatSignalClient signalClient, ChatCallback callback) {
        this.signalClient = signalClient;
        this.callback = callback;
    }

    public void connect(String serverUrl) {
        WebRTCConfiguration config = new WebRTCConfiguration();
        transport = WebRTCTransports.newClientTransport(config);
        transport.setListener(new TransportListener() {
            public void onConnected() {
                connected = true;
                Gdx.app.postRunnable(new Runnable() {
                    public void run() {
                        callback.onConnected();
                    }
                });
            }

            public void onDisconnected() {
                connected = false;
                Gdx.app.postRunnable(new Runnable() {
                    public void run() {
                        callback.onDisconnected();
                    }
                });
            }

            public void onMessage(byte[] data, boolean reliable) {
                final String text = new String(data, StandardCharsets.UTF_8);
                Gdx.app.postRunnable(new Runnable() {
                    public void run() {
                        callback.onMessageReceived(text);
                    }
                });
            }

            public void onError(final String message) {
                Gdx.app.postRunnable(new Runnable() {
                    public void run() {
                        callback.onError(message);
                    }
                });
            }
        });

        signalClient.connect(serverUrl, new ChatSignalClient.Listener() {
            public void onOpen() {
                // Server will send us an offer
            }

            public void onMessage(String text) {
                handleSignalingMessage(text);
            }

            public void onClose(final String reason) {
                if (!connected) {
                    Gdx.app.postRunnable(new Runnable() {
                        public void run() {
                            callback.onError("Signaling closed: " + reason);
                        }
                    });
                }
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
        String type = SignalMessage.extractString(json, "type");
        if ("offer".equals(type)) {
            String sdp = SignalMessage.extractString(json, "sdp");
            transport.connectWithOffer(sdp, new WebRTCClientTransport.SignalCallback() {
                public void onAnswer(String sdpAnswer) {
                    signalClient.send("{\"type\":\"answer\",\"sdp\":\""
                            + SignalMessage.escapeJson(sdpAnswer) + "\"}");
                }

                public void onIceCandidate(String iceJson) {
                    signalClient.send("{\"type\":\"ice\",\"candidate\":\""
                            + SignalMessage.escapeJson(iceJson) + "\"}");
                }
            });
        } else if ("ice".equals(type)) {
            String candidate = SignalMessage.extractString(json, "candidate");
            transport.addIceCandidate(candidate);
        }
    }

    public void sendMessage(String text) {
        if (transport != null && connected) {
            transport.sendReliable(text.getBytes(StandardCharsets.UTF_8));
        }
    }

    public boolean isConnected() {
        return connected;
    }

    public void disconnect() {
        connected = false;
        if (signalClient != null) {
            signalClient.close();
        }
        if (transport != null) {
            transport.disconnect();
            transport = null;
        }
    }
}
