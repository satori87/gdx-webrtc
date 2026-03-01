package com.github.satori87.gdx.webrtc;

/**
 * Callback interface for asynchronous SDP (Session Description Protocol) operations.
 *
 * <p>Used by {@link PeerConnectionProvider#createOffer(Object, SdpResultCallback)}
 * and {@link PeerConnectionProvider#handleOffer(Object, String, SdpResultCallback)}
 * to deliver the result of SDP offer/answer creation.</p>
 *
 * @see PeerConnectionProvider
 */
public interface SdpResultCallback {

    /**
     * Called when SDP creation succeeds.
     *
     * @param sdp the SDP string (offer or answer) to send to the remote peer
     *            via the signaling server
     */
    void onSuccess(String sdp);

    /**
     * Called when SDP creation fails.
     *
     * @param error a human-readable description of the failure
     */
    void onFailure(String error);
}
