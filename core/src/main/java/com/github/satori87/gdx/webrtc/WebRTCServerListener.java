package com.github.satori87.gdx.webrtc;

/**
 * Listener interface for {@link WebRTCServer} events.
 *
 * <p>All callbacks may fire on internal threads. In a libGDX application,
 * wrap UI-affecting logic in {@code Gdx.app.postRunnable()}.</p>
 *
 * @see WebRTCServer
 */
public interface WebRTCServerListener {

    /**
     * Called when the server has connected to the signaling server and is ready
     * to accept clients.
     *
     * @param serverId the unique ID assigned by the signaling server
     */
    void onStarted(int serverId);

    /**
     * Called when a client has established a peer-to-peer connection to this server.
     *
     * @param clientId the signaling ID of the connected client
     */
    void onClientConnected(int clientId);

    /**
     * Called when a client has disconnected from this server.
     *
     * @param clientId the signaling ID of the disconnected client
     */
    void onClientDisconnected(int clientId);

    /**
     * Called when a message is received from a client.
     *
     * @param clientId the signaling ID of the client that sent the message
     * @param data     the raw bytes received
     * @param reliable {@code true} if received on the reliable channel
     */
    void onClientMessage(int clientId, byte[] data, boolean reliable);

    /**
     * Called when an error occurs.
     *
     * @param error a human-readable error description
     */
    void onError(String error);
}
