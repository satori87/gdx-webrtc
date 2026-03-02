package studio.whitlock.webrtc.chat;

import com.badlogic.gdx.Gdx;
import com.github.satori87.gdx.webrtc.SignalMessage;
import com.github.satori87.gdx.webrtc.WebRTCConfiguration;
import com.github.satori87.gdx.webrtc.transport.TransportListener;
import com.github.satori87.gdx.webrtc.transport.WebRTCClientTransport;
import com.github.satori87.gdx.webrtc.transport.WebRTCTransports;

import java.nio.charset.StandardCharsets;

/**
 * Manages a client connection to a hosted chat server using WebRTCClientTransport.
 * Connects to the gdx-webrtc signaling server and exchanges SDP/ICE with the host
 * using the SignalMessage protocol.
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
    private int localPeerId = -1;
    private int hostPeerId = -1;

    public ConnectionManager(ChatSignalClient signalClient, ChatCallback callback) {
        this.signalClient = signalClient;
        this.callback = callback;
    }

    public void connect(String relayUrl) {
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

        signalClient.connect(relayUrl, new ChatSignalClient.Listener() {
            public void onOpen() {
                // Wait for WELCOME message
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
        SignalMessage msg = SignalMessage.fromJson(json);
        if (msg == null) {
            System.out.println("[ChatClient] Failed to parse signaling message: " + json);
            return;
        }

        System.out.println("[ChatClient] Received signal type=" + msg.type + " source=" + msg.source + " target=" + msg.target);

        switch (msg.type) {
            case SignalMessage.TYPE_WELCOME:
                localPeerId = Integer.parseInt(msg.data);
                System.out.println("[ChatClient] Our peer ID: " + localPeerId);
                break;

            case SignalMessage.TYPE_PEER_JOINED:
                // Note the host peer — the host will proactively send us an offer
                System.out.println("[ChatClient] Peer joined: " + msg.source);
                break;

            case SignalMessage.TYPE_PEER_LIST:
                // Host will send offers proactively, no action needed
                break;

            case SignalMessage.TYPE_OFFER:
                hostPeerId = msg.source;
                System.out.println("[ChatClient] Received OFFER from host " + hostPeerId);
                handleOffer(msg.source, msg.data);
                break;

            case SignalMessage.TYPE_ICE:
                System.out.println("[ChatClient] Received ICE from host");
                handleIce(msg.data);
                break;

            case SignalMessage.TYPE_PEER_LEFT:
                if (msg.source == hostPeerId && connected) {
                    connected = false;
                    Gdx.app.postRunnable(new Runnable() {
                        public void run() {
                            callback.onDisconnected();
                        }
                    });
                }
                break;

            case SignalMessage.TYPE_ERROR:
                final String errorData = msg.data;
                Gdx.app.postRunnable(new Runnable() {
                    public void run() {
                        callback.onError(errorData);
                    }
                });
                break;
        }
    }

    private void handleOffer(final int fromPeerId, String sdp) {
        transport.connectWithOffer(sdp, new WebRTCClientTransport.SignalCallback() {
            public void onAnswer(String sdpAnswer) {
                System.out.println("[ChatClient] Sending ANSWER to host " + fromPeerId);
                SignalMessage answer = new SignalMessage(
                        SignalMessage.TYPE_ANSWER, localPeerId, fromPeerId, sdpAnswer);
                signalClient.send(answer.toJson());
            }

            public void onIceCandidate(String iceJson) {
                System.out.println("[ChatClient] Sending ICE to host " + fromPeerId);
                SignalMessage ice = new SignalMessage(
                        SignalMessage.TYPE_ICE, localPeerId, fromPeerId, iceJson);
                signalClient.send(ice.toJson());
            }
        });
    }

    private void handleIce(String iceJson) {
        if (transport != null) {
            transport.addIceCandidate(iceJson);
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
