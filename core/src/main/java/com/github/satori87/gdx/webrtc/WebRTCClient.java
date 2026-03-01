package com.github.satori87.gdx.webrtc;

/**
 * WebRTC client. Connects to a signaling server and establishes
 * peer-to-peer connections with other clients.
 */
public interface WebRTCClient {

    /** Connect to the signaling server. */
    void connect();

    /** Disconnect from the signaling server and close all peer connections. */
    void disconnect();

    /** Whether connected to the signaling server. */
    boolean isConnectedToSignaling();

    /**
     * Request a peer connection to the given peer ID.
     * The signaling server relays SDP/ICE to establish the connection.
     * {@link WebRTCClientListener#onConnected(WebRTCPeer)} fires when ready.
     */
    void connectToPeer(int peerId);

    /** Set the listener for connection events. */
    void setListener(WebRTCClientListener listener);

    /** Get the local peer ID assigned by the signaling server. -1 if not yet assigned. */
    int getLocalId();
}
