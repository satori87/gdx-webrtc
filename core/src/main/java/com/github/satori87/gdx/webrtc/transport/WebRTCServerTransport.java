package com.github.satori87.gdx.webrtc.transport;

/**
 * Server transport that uses WebRTC with external signaling.
 *
 * <p>This transport is designed for games and applications that have their own
 * signaling mechanism (e.g. a lobby server or matchmaker). The application is
 * responsible for relaying SDP offers/answers and ICE candidates between the
 * server and each client — this transport does not use the library's built-in
 * {@link com.github.satori87.gdx.webrtc.SignalingProvider}.</p>
 *
 * <h3>Connection Flow (per client)</h3>
 * <ol>
 *   <li>Client requests a connection (via the application's signaling)</li>
 *   <li>Server calls {@link #createPeerForOffer(SignalCallback)} to create
 *       a peer connection and generate an SDP offer</li>
 *   <li>Application relays the offer to the client</li>
 *   <li>Client processes the offer and sends back an SDP answer</li>
 *   <li>Server calls {@link #setAnswer(int, String)} with the answer</li>
 *   <li>Both sides exchange ICE candidates via the callbacks and
 *       {@link #addIceCandidate(int, String)}</li>
 *   <li>When the reliable data channel opens,
 *       {@link ServerTransportListener#onClientConnected(int)} fires</li>
 * </ol>
 *
 * <h3>Usage Example</h3>
 * <pre>
 * WebRTCServerTransport server = WebRTCTransports.newServerTransport(config);
 * server.setTurnServer("turn:myserver.com:3478", "user", "pass");
 * server.setListener(myListener);
 *
 * // When a client requests a connection (via your signaling):
 * int connId = server.createPeerForOffer(new WebRTCServerTransport.SignalCallback() {
 *     public void onOffer(int connId, String sdpOffer) {
 *         myLobby.sendOfferToClient(clientId, sdpOffer);
 *     }
 *     public void onIceCandidate(int connId, String iceJson) {
 *         myLobby.sendIceCandidateToClient(clientId, iceJson);
 *     }
 * });
 *
 * // When the client's answer arrives (via your signaling):
 * server.setAnswer(connId, sdpAnswer);
 *
 * // When ICE candidates arrive from the client:
 * server.addIceCandidate(connId, iceJson);
 *
 * // Send data to a specific client:
 * server.sendReliable(connId, gameStateBytes);
 *
 * // Broadcast to all connected clients:
 * server.broadcastUnreliable(snapshotBytes);
 * </pre>
 *
 * @see WebRTCClientTransport
 * @see WebRTCTransports#newServerTransport(com.github.satori87.gdx.webrtc.WebRTCConfiguration)
 */
public interface WebRTCServerTransport extends ServerTransport {

    /**
     * Configures a TURN server for relay connections.
     *
     * <p>This configuration is applied to all peer connections created after
     * this call via {@link #createPeerForOffer(SignalCallback)}. It overrides
     * any TURN settings in the {@link com.github.satori87.gdx.webrtc.WebRTCConfiguration}
     * that was passed to the constructor.</p>
     *
     * <p>Call this method before creating peer connections, or when TURN
     * credentials are received dynamically (e.g. from a lobby server).</p>
     *
     * @param url      the TURN server URL (e.g. {@code "turn:myserver.com:3478"})
     * @param username the TURN authentication username
     * @param password the TURN authentication password
     */
    void setTurnServer(String url, String username, String password);

    /**
     * Creates a peer connection for an incoming client and generates an SDP offer.
     *
     * <p>This method:</p>
     * <ol>
     *   <li>Assigns a unique connection ID to the new client</li>
     *   <li>Creates a new RTCPeerConnection with both data channels</li>
     *   <li>Generates an SDP offer</li>
     *   <li>Calls {@link SignalCallback#onOffer(int, String)} with the offer</li>
     * </ol>
     *
     * <p>The application must relay the offer to the client through its signaling
     * channel. ICE candidates generated during the handshake are delivered via
     * {@link SignalCallback#onIceCandidate(int, String)}.</p>
     *
     * @param callback receives the SDP offer and ICE candidates to relay to
     *                 the client
     * @return the connection ID assigned to this peer (used in subsequent
     *         calls to {@link #setAnswer}, {@link #addIceCandidate},
     *         {@link #sendReliable}, etc.)
     */
    int createPeerForOffer(SignalCallback callback);

    /**
     * Sets the remote SDP answer received from a client via signaling.
     *
     * <p>This method is a no-op if the given {@code connId} does not correspond
     * to a known peer.</p>
     *
     * @param connId    the connection ID returned by {@link #createPeerForOffer}
     * @param sdpAnswer the client's SDP answer string
     */
    void setAnswer(int connId, String sdpAnswer);

    /**
     * Adds an ICE candidate received from a client via signaling.
     *
     * <p>The candidate JSON should contain {@code candidate}, {@code sdpMid},
     * and {@code sdpMLineIndex} fields.</p>
     *
     * <p>This method is a no-op if the given {@code connId} does not correspond
     * to a known peer.</p>
     *
     * @param connId  the connection ID of the client
     * @param iceJson the JSON-encoded ICE candidate from the client
     */
    void addIceCandidate(int connId, String iceJson);

    /**
     * Callback interface for signaling messages that the application must
     * relay to clients.
     *
     * <p>These callbacks fire on internal threads. If the signaling channel
     * requires main-thread access, wrap the relay calls appropriately.</p>
     */
    interface SignalCallback {

        /**
         * Called when an SDP offer has been created for a client.
         *
         * <p>The application must relay this offer to the client through its
         * signaling channel.</p>
         *
         * @param connId   the connection ID of the peer
         * @param sdpOffer the local SDP offer string
         */
        void onOffer(int connId, String sdpOffer);

        /**
         * Called when a local ICE candidate has been generated for a client.
         *
         * <p>The application must relay this candidate to the client through its
         * signaling channel.</p>
         *
         * @param connId  the connection ID of the peer
         * @param iceJson the JSON-encoded ICE candidate
         */
        void onIceCandidate(int connId, String iceJson);
    }
}
