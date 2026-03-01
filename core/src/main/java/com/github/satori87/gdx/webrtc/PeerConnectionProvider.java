package com.github.satori87.gdx.webrtc;

/**
 * Strategy interface for platform-specific WebRTC peer connection operations.
 *
 * <p>This is the primary abstraction that separates shared connection management
 * logic (in {@link BaseWebRTCClient}) from platform-specific WebRTC API calls.
 * Each platform provides an implementation:</p>
 * <ul>
 *   <li>{@code DesktopPeerConnectionProvider} - uses webrtc-java (dev.onvoid.webrtc)</li>
 *   <li>{@code TeaVMPeerConnectionProvider} - uses JavaScript WebRTC API via {@code @JSBody}</li>
 *   <li>{@code AndroidPeerConnectionProvider} - uses Google WebRTC SDK (org.webrtc)</li>
 *   <li>{@code IOSPeerConnectionProvider} - uses WebRTC.framework via RoboVM bindings</li>
 * </ul>
 *
 * <p>All handle parameters ({@code peerConnection}, {@code channel}) are opaque
 * {@link Object} references whose concrete types are known only to the platform
 * implementation. The {@link BaseWebRTCClient} stores them and passes them back
 * without casting.</p>
 *
 * @see BaseWebRTCClient
 * @see PeerEventHandler
 * @see DataChannelEventHandler
 */
public interface PeerConnectionProvider {

    /**
     * Initializes the platform WebRTC factory/runtime.
     *
     * <p>Called once before the first peer connection is created. Implementations
     * should initialize the native WebRTC peer connection factory (e.g.
     * {@code PeerConnectionFactory} on Android, {@code RTCPeerConnectionFactory}
     * on iOS). May be called multiple times; subsequent calls should be no-ops
     * returning {@code true}.</p>
     *
     * @return {@code true} if initialization succeeded, {@code false} on failure
     */
    boolean initialize();

    /**
     * Creates a new RTCPeerConnection with the given configuration.
     *
     * <p>The implementation must:</p>
     * <ul>
     *   <li>Configure ICE servers (STUN/TURN) from the provided configuration</li>
     *   <li>Wire {@code onicecandidate} events to
     *       {@link PeerEventHandler#onIceCandidate(String)}</li>
     *   <li>Wire connection state change events to
     *       {@link PeerEventHandler#onConnectionStateChanged(int)}</li>
     *   <li>Wire {@code ondatachannel} events to
     *       {@link PeerEventHandler#onDataChannel(Object, String)}</li>
     * </ul>
     *
     * @param config  the WebRTC configuration containing ICE server settings
     * @param handler callbacks for ICE candidates, connection state changes,
     *                and incoming data channels
     * @return an opaque peer connection handle, or {@code null} on failure
     */
    Object createPeerConnection(WebRTCConfiguration config, PeerEventHandler handler);

    /**
     * Creates an SDP offer on the given peer connection.
     *
     * <p>The implementation should create the offer, set it as the local
     * description, and invoke {@link SdpResultCallback#onSuccess(String)}
     * with the SDP string. On failure, invoke
     * {@link SdpResultCallback#onFailure(String)}.</p>
     *
     * @param peerConnection the opaque peer connection handle
     * @param callback       callback to receive the offer SDP or error
     */
    void createOffer(Object peerConnection, SdpResultCallback callback);

    /**
     * Handles a received SDP offer by setting it as the remote description,
     * creating an answer, and setting the answer as the local description.
     *
     * <p>On success, invokes {@link SdpResultCallback#onSuccess(String)} with
     * the answer SDP string. On failure, invokes
     * {@link SdpResultCallback#onFailure(String)}.</p>
     *
     * @param peerConnection the opaque peer connection handle
     * @param remoteSdp      the remote SDP offer string
     * @param callback       callback to receive the answer SDP or error
     */
    void handleOffer(Object peerConnection, String remoteSdp, SdpResultCallback callback);

    /**
     * Sets the remote SDP answer on the peer connection.
     *
     * <p>Called on the offerer side after receiving the answerer's SDP response.</p>
     *
     * @param peerConnection the opaque peer connection handle
     * @param sdp            the remote SDP answer string
     */
    void setRemoteAnswer(Object peerConnection, String sdp);

    /**
     * Adds a remote ICE candidate to the peer connection.
     *
     * <p>The candidate is provided as a JSON string containing {@code candidate},
     * {@code sdpMid}, and {@code sdpMLineIndex} fields. Use
     * {@link SignalMessage#extractString(String, String)} and
     * {@link SignalMessage#extractInt(String, String)} to parse.</p>
     *
     * @param peerConnection the opaque peer connection handle
     * @param candidateJson  the JSON-encoded ICE candidate
     * @see SignalMessage#buildIceCandidateJson(String, String, int)
     */
    void addIceCandidate(Object peerConnection, String candidateJson);

    /**
     * Triggers an ICE restart on the peer connection.
     *
     * <p>Called by the ICE state machine when a connection enters the
     * DISCONNECTED or FAILED state and a restart is warranted.</p>
     *
     * @param peerConnection the opaque peer connection handle
     */
    void restartIce(Object peerConnection);

    /**
     * Closes the peer connection and releases associated native resources.
     *
     * @param peerConnection the opaque peer connection handle
     */
    void closePeerConnection(Object peerConnection);

    /**
     * Creates both reliable and unreliable data channels on the peer connection.
     *
     * <p>The reliable channel should be created with {@code ordered=true} and
     * unlimited retransmits. The unreliable channel should be created with
     * {@code ordered=false} and the specified {@code maxRetransmits} value.</p>
     *
     * <p>The implementation must wire {@code onopen}, {@code onclose}, and
     * {@code onmessage} events to the appropriate methods on the provided
     * {@link DataChannelEventHandler}.</p>
     *
     * @param peerConnection         the opaque peer connection handle
     * @param unreliableMaxRetransmits the {@code maxRetransmits} value for the
     *                                 unreliable channel (0 = fire-and-forget)
     * @param handler                 callbacks for channel open/close/message events
     * @return a {@link ChannelPair} containing both channel handles
     */
    ChannelPair createDataChannels(Object peerConnection, int unreliableMaxRetransmits,
                                    DataChannelEventHandler handler);

    /**
     * Registers event handlers on a data channel received from the remote peer.
     *
     * <p>Called on the answerer side when {@code ondatachannel} fires on the
     * RTCPeerConnection. The implementation must wire the channel's events
     * to the correct reliable or unreliable methods on the handler based on
     * the {@code reliable} flag.</p>
     *
     * @param channel  the opaque data channel handle received from the remote peer
     * @param reliable {@code true} if this is the reliable channel,
     *                 {@code false} if unreliable
     * @param handler  callbacks for channel open/close/message events
     */
    void setupReceivedChannel(Object channel, boolean reliable, DataChannelEventHandler handler);

    /**
     * Sends binary data on a data channel.
     *
     * @param channel the opaque data channel handle
     * @param data    the raw bytes to send
     */
    void sendData(Object channel, byte[] data);

    /**
     * Returns the number of bytes currently buffered for sending on a data channel.
     *
     * <p>Used by the unreliable send logic to check whether the buffer exceeds
     * the configured limit before sending.</p>
     *
     * @param channel the opaque data channel handle
     * @return the buffered amount in bytes
     * @see WebRTCConfiguration#unreliableBufferLimit
     */
    long getBufferedAmount(Object channel);

    /**
     * Returns whether a data channel is currently in the OPEN state.
     *
     * @param channel the opaque data channel handle
     * @return {@code true} if the channel is open and ready for data transfer
     */
    boolean isChannelOpen(Object channel);
}
