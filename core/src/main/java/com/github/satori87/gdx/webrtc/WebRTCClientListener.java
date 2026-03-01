package com.github.satori87.gdx.webrtc;

/**
 * Listener for WebRTC peer connection events.
 * Callbacks fire on internal threads — use Gdx.app.postRunnable() for UI updates.
 */
public interface WebRTCClientListener {

    /** Called when a peer connection is fully established (data channels open). */
    void onConnected(WebRTCPeer peer);

    /** Called on permanent disconnect only (after ICE retry exhaustion or explicit close). */
    void onDisconnected(WebRTCPeer peer);

    /**
     * Called when data is received from a peer.
     * @param peer the peer that sent the message
     * @param data the raw bytes received
     * @param reliable true if received on the reliable channel, false if unreliable
     */
    void onMessage(WebRTCPeer peer, byte[] data, boolean reliable);

    /** Called when an error occurs. */
    void onError(String error);
}
