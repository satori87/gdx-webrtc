package com.github.satori87.gdx.webrtc;

/**
 * Callback interface for signaling WebSocket connection events.
 *
 * <p>The {@link SignalingProvider} implementation calls these methods when
 * WebSocket lifecycle events occur. The {@link BaseWebRTCClient} creates
 * an instance of this interface during {@link WebRTCClient#connect()} to
 * wire incoming signaling messages to the message dispatch logic.</p>
 *
 * @see SignalingProvider#connect(String, SignalingEventHandler)
 */
public interface SignalingEventHandler {

    /**
     * Called when the WebSocket connection to the signaling server is established.
     */
    void onOpen();

    /**
     * Called when a signaling message is received from the server.
     *
     * <p>The {@link SignalingProvider} is responsible for parsing the raw
     * WebSocket text frame into a {@link SignalMessage} via
     * {@link SignalMessage#fromJson(String)} before calling this method.</p>
     *
     * @param msg the parsed signaling message
     */
    void onMessage(SignalMessage msg);

    /**
     * Called when the WebSocket connection to the signaling server is closed.
     *
     * @param reason a human-readable description of the close reason
     */
    void onClose(String reason);

    /**
     * Called when a WebSocket error occurs.
     *
     * @param error a human-readable description of the error
     */
    void onError(String error);
}
