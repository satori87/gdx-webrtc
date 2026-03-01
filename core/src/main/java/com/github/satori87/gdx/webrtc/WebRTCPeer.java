package com.github.satori87.gdx.webrtc;

/**
 * Represents a WebRTC peer connection.
 */
public interface WebRTCPeer {

    /** Unique ID of this peer (assigned by the signaling server). */
    int getId();

    /** Send data reliably (ordered, guaranteed delivery). */
    void sendReliable(byte[] data);

    /**
     * Send data unreliably (unordered, no retransmits).
     * Packets are silently dropped if the send buffer exceeds 64KB.
     * Falls back to reliable if the unreliable channel is unavailable.
     */
    void sendUnreliable(byte[] data);

    /** Whether this peer connection is currently open and ready. */
    boolean isConnected();

    /** Close this peer connection. */
    void close();
}
