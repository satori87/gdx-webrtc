package com.github.satori87.gdx.webrtc.transport;

/**
 * Abstract server transport managing multiple client connections.
 *
 * <p>Each connected client is identified by a numeric connection ID ({@code connId})
 * assigned by the transport. The server can send data to individual clients or
 * broadcast to all connected clients.</p>
 *
 * <p>This interface does not include a {@code start(int port)} method because
 * WebRTC servers do not bind a port. Clients connect through signaling (SDP/ICE
 * exchange). See {@link WebRTCServerTransport} for the signaling methods.</p>
 *
 * @see WebRTCServerTransport
 * @see ServerTransportListener
 */
public interface ServerTransport {

    /**
     * Stops the server and disconnects all clients.
     *
     * <p>Closes all peer connections, cancels all pending timers, and shuts
     * down the internal scheduler. After calling this method, the transport
     * should not be reused.</p>
     */
    void stop();

    /**
     * Sends data reliably to a specific client.
     *
     * <p>Data is delivered in order and retransmitted until acknowledged.
     * This method is a no-op if the given {@code connId} does not correspond
     * to a connected client.</p>
     *
     * @param connId the connection ID of the target client
     * @param data   the raw bytes to send
     */
    void sendReliable(int connId, byte[] data);

    /**
     * Sends data unreliably to a specific client.
     *
     * <p>Packets are silently dropped if the client's send buffer exceeds the
     * configured limit. Falls back to reliable delivery if the unreliable
     * channel is not available for the given client.</p>
     *
     * <p>This method is a no-op if the given {@code connId} does not correspond
     * to a connected client.</p>
     *
     * @param connId the connection ID of the target client
     * @param data   the raw bytes to send
     * @see com.github.satori87.gdx.webrtc.WebRTCConfiguration#unreliableBufferLimit
     */
    void sendUnreliable(int connId, byte[] data);

    /**
     * Sends data reliably to all connected clients.
     *
     * @param data the raw bytes to broadcast
     */
    void broadcastReliable(byte[] data);

    /**
     * Sends data unreliably to all connected clients.
     *
     * <p>Per-client buffer threshold checks apply: packets may be dropped for
     * clients whose send buffers are congested. Falls back to reliable delivery
     * for clients whose unreliable channel is not available.</p>
     *
     * @param data the raw bytes to broadcast
     */
    void broadcastUnreliable(byte[] data);

    /**
     * Disconnects a specific client.
     *
     * <p>Closes the client's peer connection, cancels any pending ICE restart
     * timers, and removes the client from the internal peer map. This method
     * is a no-op if the given {@code connId} does not correspond to a known
     * client.</p>
     *
     * @param connId the connection ID of the client to disconnect
     */
    void disconnect(int connId);

    /**
     * Returns the number of currently connected clients.
     *
     * <p>A client is counted as connected when its reliable data channel is
     * open. Clients in the process of connecting (SDP/ICE handshake in progress)
     * are not counted.</p>
     *
     * @return the number of connected clients
     */
    int getConnectionCount();

    /**
     * Sets the event listener for this transport.
     *
     * <p>The listener receives client connection, disconnection, and message
     * events. Pass {@code null} to disable callbacks.</p>
     *
     * @param listener the listener to set, or {@code null} to disable callbacks
     */
    void setListener(ServerTransportListener listener);
}
