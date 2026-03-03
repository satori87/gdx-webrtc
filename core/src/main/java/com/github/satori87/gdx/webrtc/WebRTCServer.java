package com.github.satori87.gdx.webrtc;

import java.util.ArrayList;
import java.util.List;

/**
 * High-level server for client/server WebRTC communication.
 *
 * <p>Wraps {@link WebRTCClient} to provide a simple server abstraction. The server
 * connects to a signaling server and automatically accepts connections from all
 * peers that join. All SDP/ICE negotiation is handled internally.</p>
 *
 * <p>Usage:</p>
 * <pre>
 * WebRTCConfiguration config = new WebRTCConfiguration();
 * config.signalingServerUrl = "ws://localhost:9090";
 *
 * WebRTCServer server = WebRTCClients.newServer(config, myListener);
 * server.start();
 * </pre>
 *
 * @see WebRTCServerListener
 * @see WebRTCClients#newServer(WebRTCConfiguration, WebRTCServerListener)
 */
public class WebRTCServer {

    private final WebRTCClient client;
    private final WebRTCServerListener listener;
    private final List/*<WebRTCPeer>*/ clients = new ArrayList/*<WebRTCPeer>*/();
    private volatile boolean running;

    /**
     * Creates a new server.
     *
     * @param config   the WebRTC and signaling configuration
     * @param listener the listener for server events
     */
    public WebRTCServer(WebRTCConfiguration config, WebRTCServerListener listener) {
        this.listener = listener;
        this.client = WebRTCClients.newClient(config, new WebRTCClientListener() {
            public void onSignalingConnected(int localId) {
                running = true;
                WebRTCServer.this.listener.onStarted(localId);
            }

            public void onPeerJoined(int peerId) {
                client.connectToPeer(peerId);
            }

            public void onPeerLeft(int peerId) {
                // Handled by onDisconnected
            }

            public void onConnected(WebRTCPeer peer) {
                clients.add(peer);
                WebRTCServer.this.listener.onClientConnected(peer.getId());
            }

            public void onDisconnected(WebRTCPeer peer) {
                clients.remove(peer);
                WebRTCServer.this.listener.onClientDisconnected(peer.getId());
            }

            public void onMessage(WebRTCPeer peer, byte[] data, boolean reliable) {
                WebRTCServer.this.listener.onClientMessage(peer.getId(), data, reliable);
            }

            public void onError(String error) {
                WebRTCServer.this.listener.onError(error);
            }
        });
    }

    /**
     * Starts the server by connecting to the signaling server.
     */
    public void start() {
        client.connect();
    }

    /**
     * Stops the server by disconnecting from signaling and closing all client connections.
     */
    public void stop() {
        running = false;
        clients.clear();
        client.disconnect();
    }

    /**
     * Sends a reliable message to a specific client.
     *
     * @param clientId the signaling ID of the target client
     * @param data     the raw bytes to send
     */
    public void sendToClient(int clientId, byte[] data) {
        WebRTCPeer peer = findPeer(clientId);
        if (peer != null && peer.isConnected()) {
            peer.sendReliable(data);
        }
    }

    /**
     * Sends an unreliable message to a specific client.
     *
     * @param clientId the signaling ID of the target client
     * @param data     the raw bytes to send
     */
    public void sendToClientUnreliable(int clientId, byte[] data) {
        WebRTCPeer peer = findPeer(clientId);
        if (peer != null && peer.isConnected()) {
            peer.sendUnreliable(data);
        }
    }

    /**
     * Sends a reliable message to all connected clients.
     *
     * @param data the raw bytes to send
     */
    public void broadcastReliable(byte[] data) {
        for (int i = 0; i < clients.size(); i++) {
            WebRTCPeer peer = (WebRTCPeer) clients.get(i);
            if (peer.isConnected()) {
                peer.sendReliable(data);
            }
        }
    }

    /**
     * Sends an unreliable message to all connected clients.
     *
     * @param data the raw bytes to send
     */
    public void broadcastUnreliable(byte[] data) {
        for (int i = 0; i < clients.size(); i++) {
            WebRTCPeer peer = (WebRTCPeer) clients.get(i);
            if (peer.isConnected()) {
                peer.sendUnreliable(data);
            }
        }
    }

    /**
     * Sends a reliable message to all connected clients except the specified one.
     *
     * @param excludeClientId the signaling ID of the client to exclude
     * @param data            the raw bytes to send
     */
    public void broadcastReliableExcept(int excludeClientId, byte[] data) {
        for (int i = 0; i < clients.size(); i++) {
            WebRTCPeer peer = (WebRTCPeer) clients.get(i);
            if (peer.getId() != excludeClientId && peer.isConnected()) {
                peer.sendReliable(data);
            }
        }
    }

    /**
     * Sends an unreliable message to all connected clients except the specified one.
     *
     * @param excludeClientId the signaling ID of the client to exclude
     * @param data            the raw bytes to send
     */
    public void broadcastUnreliableExcept(int excludeClientId, byte[] data) {
        for (int i = 0; i < clients.size(); i++) {
            WebRTCPeer peer = (WebRTCPeer) clients.get(i);
            if (peer.getId() != excludeClientId && peer.isConnected()) {
                peer.sendUnreliable(data);
            }
        }
    }

    /**
     * Disconnects a specific client.
     *
     * @param clientId the signaling ID of the client to disconnect
     */
    public void disconnectClient(int clientId) {
        WebRTCPeer peer = findPeer(clientId);
        if (peer != null) {
            clients.remove(peer);
            peer.close();
        }
    }

    /**
     * Returns whether the server is running (connected to signaling).
     *
     * @return {@code true} if the server is running
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Returns the server's signaling ID.
     *
     * @return the server ID, or -1 if not yet started
     */
    public int getServerId() {
        return client.getLocalId();
    }

    private WebRTCPeer findPeer(int clientId) {
        for (int i = 0; i < clients.size(); i++) {
            WebRTCPeer peer = (WebRTCPeer) clients.get(i);
            if (peer.getId() == clientId) {
                return peer;
            }
        }
        return null;
    }
}
