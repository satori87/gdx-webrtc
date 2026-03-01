package com.github.satori87.gdx.webrtc;

/**
 * Callback interface for WebRTC peer connection events.
 *
 * <p>The platform {@link PeerConnectionProvider} implementation calls these
 * methods when the corresponding native WebRTC events occur on an
 * RTCPeerConnection. The {@link BaseWebRTCClient} creates instances of this
 * interface to wire platform events to shared connection management logic.</p>
 *
 * @see PeerConnectionProvider#createPeerConnection(WebRTCConfiguration, PeerEventHandler)
 * @see ConnectionState
 */
public interface PeerEventHandler {

    /**
     * Called when a local ICE candidate has been generated.
     *
     * <p>The candidate should be sent to the remote peer via the signaling server
     * so it can be added to their peer connection.</p>
     *
     * @param candidateJson the ICE candidate serialized as a JSON string containing
     *                      {@code candidate}, {@code sdpMid}, and {@code sdpMLineIndex} fields
     * @see SignalMessage#buildIceCandidateJson(String, String, int)
     */
    void onIceCandidate(String candidateJson);

    /**
     * Called when the peer connection's connectivity state changes.
     *
     * <p>The state parameter must be one of the {@link ConnectionState} constants.
     * Platform implementations must map their native state types to these
     * normalized constants.</p>
     *
     * @param state the new connection state, one of {@link ConnectionState#NEW},
     *              {@link ConnectionState#CONNECTING}, {@link ConnectionState#CONNECTED},
     *              {@link ConnectionState#DISCONNECTED}, {@link ConnectionState#FAILED},
     *              or {@link ConnectionState#CLOSED}
     * @see ConnectionState
     */
    void onConnectionStateChanged(int state);

    /**
     * Called when a data channel is received from the remote peer.
     *
     * <p>This occurs on the answerer side of a connection. The offerer creates
     * data channels explicitly; the answerer receives them via this callback.
     * The {@link BaseWebRTCClient} uses the label to determine whether the
     * channel is reliable or unreliable.</p>
     *
     * @param channel the opaque data channel handle
     * @param label   the channel label: {@code "reliable"} or {@code "unreliable"}
     */
    void onDataChannel(Object channel, String label);
}
