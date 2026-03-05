package com.github.satori87.gdx.webrtc;

import com.github.satori87.gdx.webrtc.transport.ClientTransport;
import com.github.satori87.gdx.webrtc.transport.TransportListener;

/**
 * High-level client for client/server WebRTC communication.
 *
 * <p>Wraps {@link WebRTCClient} to provide a simple client abstraction. The client
 * connects to a signaling server and waits for a server (host) to initiate the
 * peer-to-peer connection. All SDP/ICE negotiation is handled internally.</p>
 *
 * <p>Usage:</p>
 * <pre>
 * WebRTCConfiguration config = new WebRTCConfiguration();
 * config.signalingServerUrl = "ws://localhost:9090";
 *
 * WebRTCGameClient client = WebRTCClients.newGameClient(config, myListener);
 * client.connect();
 * </pre>
 *
 * @see WebRTCGameClientListener
 * @see WebRTCClients#newGameClient(WebRTCConfiguration, WebRTCGameClientListener)
 */
public class WebRTCGameClient implements ClientTransport {

    private final WebRTCClient client;
    private final WebRTCGameClientListener listener;
    private volatile WebRTCPeer serverPeer;
    private TransportListener transportListener;

    /**
     * Creates a new game client.
     *
     * @param config   the WebRTC and signaling configuration
     * @param listener the listener for client events
     */
    public WebRTCGameClient(WebRTCConfiguration config, WebRTCGameClientListener listener) {
        this.listener = listener;
        this.client = WebRTCClients.newClient(config, new WebRTCClientListener() {
            public void onSignalingConnected(int localId) {
                // Client just waits for the server to initiate the connection
            }

            public void onPeerJoined(int peerId) {
                // Client never initiates connections — server does
            }

            public void onPeerLeft(int peerId) {
                // Handled by onDisconnected
            }

            public void onConnected(WebRTCPeer peer) {
                serverPeer = peer;
                WebRTCGameClient.this.listener.onConnected();
                if (transportListener != null) {
                    transportListener.onConnected();
                }
            }

            public void onDisconnected(WebRTCPeer peer) {
                serverPeer = null;
                WebRTCGameClient.this.listener.onDisconnected();
                if (transportListener != null) {
                    transportListener.onDisconnected();
                }
            }

            public void onMessage(WebRTCPeer peer, byte[] data, boolean reliable) {
                WebRTCGameClient.this.listener.onMessage(data, reliable);
                if (transportListener != null) {
                    transportListener.onMessage(data, reliable);
                }
            }

            public void onError(String error) {
                WebRTCGameClient.this.listener.onError(error);
                if (transportListener != null) {
                    transportListener.onError(error);
                }
            }
        });
    }

    /**
     * Connects to the signaling server and waits for the server to initiate
     * a peer-to-peer connection.
     */
    public void connect() {
        client.connect();
    }

    /**
     * Disconnects from the server and signaling.
     */
    public void disconnect() {
        serverPeer = null;
        client.disconnect();
    }

    /**
     * Sends a reliable message to the server.
     *
     * @param data the raw bytes to send
     */
    public void sendReliable(byte[] data) {
        WebRTCPeer peer = serverPeer;
        if (peer != null && peer.isConnected()) {
            peer.sendReliable(data);
        }
    }

    /**
     * Sends an unreliable message to the server.
     *
     * @param data the raw bytes to send
     */
    public void sendUnreliable(byte[] data) {
        WebRTCPeer peer = serverPeer;
        if (peer != null && peer.isConnected()) {
            peer.sendUnreliable(data);
        }
    }

    /**
     * Returns whether the client is connected to the server.
     *
     * @return {@code true} if connected to the server peer
     */
    public boolean isConnected() {
        WebRTCPeer peer = serverPeer;
        return peer != null && peer.isConnected();
    }

    public void setListener(TransportListener listener) {
        this.transportListener = listener;
    }
}
