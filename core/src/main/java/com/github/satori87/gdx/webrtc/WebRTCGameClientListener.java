package com.github.satori87.gdx.webrtc;

/**
 * Listener interface for {@link WebRTCGameClient} events.
 *
 * <p>All callbacks may fire on internal threads. In a libGDX application,
 * wrap UI-affecting logic in {@code Gdx.app.postRunnable()}.</p>
 *
 * @see WebRTCGameClient
 */
public interface WebRTCGameClientListener {

    /**
     * Called when the client has established a peer-to-peer connection to the server.
     */
    void onConnected();

    /**
     * Called when the client has been disconnected from the server.
     */
    void onDisconnected();

    /**
     * Called when a message is received from the server.
     *
     * @param data     the raw bytes received
     * @param reliable {@code true} if received on the reliable channel
     */
    void onMessage(byte[] data, boolean reliable);

    /**
     * Called when an error occurs.
     *
     * @param error a human-readable error description
     */
    void onError(String error);
}
