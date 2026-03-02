package com.github.satori87.gdx.webrtc.transport;

/**
 * Client transport that uses WebRTC with external signaling.
 *
 * <p>This transport is designed for games and applications that have their own
 * signaling mechanism (e.g. a lobby server or matchmaker). The application is
 * responsible for relaying SDP offers/answers and ICE candidates between the
 * client and server — this transport does not use the library's built-in
 * {@link com.github.satori87.gdx.webrtc.SignalingProvider}.</p>
 *
 * <h3>Connection Flow</h3>
 * <ol>
 *   <li>Server creates an SDP offer via
 *       {@link WebRTCServerTransport#createPeerForOffer(WebRTCServerTransport.SignalCallback)}</li>
 *   <li>Application relays the offer to the client (through its own signaling)</li>
 *   <li>Client calls {@link #connectWithOffer(String, SignalCallback)} with the offer</li>
 *   <li>The transport creates an answer and fires
 *       {@link SignalCallback#onAnswer(String)}</li>
 *   <li>Application relays the answer back to the server</li>
 *   <li>Both sides exchange ICE candidates via the callbacks and
 *       {@link #addIceCandidate(String)}</li>
 *   <li>When the reliable data channel opens,
 *       {@link TransportListener#onConnected()} fires</li>
 * </ol>
 *
 * <h3>Usage Example</h3>
 * <pre>
 * WebRTCClientTransport transport = WebRTCTransports.newClientTransport(config);
 * transport.setListener(myListener);
 *
 * // When SDP offer arrives from the server (via your signaling):
 * transport.connectWithOffer(sdpOffer, new WebRTCClientTransport.SignalCallback() {
 *     public void onAnswer(String sdpAnswer) {
 *         myLobby.sendAnswerToServer(sdpAnswer);
 *     }
 *     public void onIceCandidate(String iceJson) {
 *         myLobby.sendIceCandidateToServer(iceJson);
 *     }
 * });
 *
 * // When ICE candidates arrive from the server (via your signaling):
 * transport.addIceCandidate(iceJson);
 * </pre>
 *
 * @see WebRTCServerTransport
 * @see WebRTCTransports#newClientTransport(com.github.satori87.gdx.webrtc.WebRTCConfiguration)
 */
public interface WebRTCClientTransport extends ClientTransport {

    /**
     * Processes an SDP offer from the server and produces an answer.
     *
     * <p>This method initiates the WebRTC handshake by:</p>
     * <ol>
     *   <li>Creating a new RTCPeerConnection (closing any existing one)</li>
     *   <li>Setting the remote SDP offer</li>
     *   <li>Creating a local SDP answer</li>
     *   <li>Calling {@link SignalCallback#onAnswer(String)} with the answer</li>
     * </ol>
     *
     * <p>ICE candidates generated during the handshake are delivered via
     * {@link SignalCallback#onIceCandidate(String)}. The application must relay
     * these to the server through its own signaling channel.</p>
     *
     * <p>If a connection already exists, it is closed before the new handshake
     * begins.</p>
     *
     * @param sdpOffer the SDP offer string from the server
     * @param callback receives the SDP answer and ICE candidates to relay
     *                 back to the server
     */
    void connectWithOffer(String sdpOffer, SignalCallback callback);

    /**
     * Adds an ICE candidate received from the server via external signaling.
     *
     * <p>The candidate JSON should contain {@code candidate}, {@code sdpMid},
     * and {@code sdpMLineIndex} fields, as produced by
     * {@link com.github.satori87.gdx.webrtc.SignalMessage#buildIceCandidateJson(String, String, int)}.</p>
     *
     * <p>This method is a no-op if no peer connection exists (e.g. before
     * {@link #connectWithOffer} is called).</p>
     *
     * @param iceJson the JSON-encoded ICE candidate from the server
     */
    void addIceCandidate(String iceJson);

    /**
     * Callback interface for signaling messages that the application must
     * relay to the server.
     *
     * <p>These callbacks fire on internal threads. If the signaling channel
     * requires main-thread access, wrap the relay calls appropriately.</p>
     */
    interface SignalCallback {

        /**
         * Called when the local SDP answer has been created.
         *
         * <p>The application must relay this answer to the server through its
         * signaling channel. The server should call
         * {@link WebRTCServerTransport#setAnswer(int, String)} with this SDP.</p>
         *
         * @param sdpAnswer the local SDP answer string
         */
        void onAnswer(String sdpAnswer);

        /**
         * Called when a local ICE candidate has been generated.
         *
         * <p>The application must relay this candidate to the server through its
         * signaling channel. The server should call
         * {@link WebRTCServerTransport#addIceCandidate(int, String)} with this JSON.</p>
         *
         * @param iceJson the JSON-encoded ICE candidate
         */
        void onIceCandidate(String iceJson);
    }
}
