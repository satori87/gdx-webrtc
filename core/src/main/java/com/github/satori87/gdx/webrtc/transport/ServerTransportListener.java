package com.github.satori87.gdx.webrtc.transport;

/**
 * Server-side transport event listener.
 *
 * <p>Implement this interface to receive notifications about client connections,
 * disconnections, and incoming data on a {@link ServerTransport}. All callbacks
 * may fire on internal threads (WebRTC callback thread or scheduler thread)
 * rather than the main/render thread.</p>
 *
 * <p>Each client is identified by a numeric connection ID ({@code connId})
 * assigned by the transport when the client connects.</p>
 *
 * @see ServerTransport#setListener(ServerTransportListener)
 */
public interface ServerTransportListener {

    /**
     * Called when a client connection is fully established and ready for data
     * transfer.
     *
     * <p>For WebRTC transports, this fires when the client's reliable data
     * channel opens after a successful SDP/ICE handshake.</p>
     *
     * @param connId the connection ID assigned to this client
     */
    void onClientConnected(int connId);

    /**
     * Called when a client connection is permanently lost.
     *
     * <p>For WebRTC transports, this only fires after all ICE restart attempts
     * have been exhausted or the client explicitly disconnects. Temporary
     * ICE disconnections are handled internally with automatic restart.</p>
     *
     * @param connId the connection ID of the disconnected client
     */
    void onClientDisconnected(int connId);

    /**
     * Called when data is received from a client.
     *
     * @param connId   the connection ID of the client that sent the data
     * @param data     the raw bytes received
     * @param reliable {@code true} if received on the reliable (ordered) channel,
     *                 {@code false} if received on the unreliable (unordered) channel
     */
    void onClientMessage(int connId, byte[] data, boolean reliable);
}
