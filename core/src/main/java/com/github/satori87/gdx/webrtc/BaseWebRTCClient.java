package com.github.satori87.gdx.webrtc;

import com.github.satori87.gdx.webrtc.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared implementation of {@link WebRTCClient} that delegates platform-specific
 * operations to strategy interfaces.
 *
 * <p>This class contains <em>all</em> shared logic that was previously duplicated
 * across four platform-specific client implementations:</p>
 * <ul>
 *   <li><b>Peer state management</b> - tracking connected peers, their channels,
 *       and connection status</li>
 *   <li><b>Signaling message dispatch</b> - handling all 9 signaling message types
 *       (WELCOME, CONNECT_REQUEST, OFFER, ANSWER, ICE, etc.)</li>
 *   <li><b>ICE state machine</b> - automatic ICE restart on DISCONNECTED with
 *       configurable delay, exponential backoff retry on FAILED, and permanent
 *       disconnect on CLOSED or max retries exceeded</li>
 *   <li><b>Data channel lifecycle</b> - wiring channel open/close/message events
 *       to {@link WebRTCClientListener} callbacks</li>
 *   <li><b>Buffer threshold checking</b> - dropping unreliable packets when the
 *       send buffer exceeds the configured limit</li>
 * </ul>
 *
 * <p>Platform modules do not subclass this class. Instead, they provide
 * implementations of three strategy interfaces and pass them to the constructor
 * via their {@link WebRTCFactory}:</p>
 * <pre>
 * // Example from DesktopWebRTCFactory:
 * public WebRTCClient createClient(WebRTCConfiguration config, WebRTCClientListener listener) {
 *     return new BaseWebRTCClient("[WebRTC-Desktop] ", config, listener,
 *             new DesktopPeerConnectionProvider(),
 *             new DesktopSignalingProvider(),
 *             new ExecutorScheduler());
 * }
 * </pre>
 *
 * @see PeerConnectionProvider
 * @see SignalingProvider
 * @see Scheduler
 * @see WebRTCClient
 */
public class BaseWebRTCClient implements WebRTCClient {

    private final String tag;
    private final WebRTCConfiguration config;
    private WebRTCClientListener listener;
    private final PeerConnectionProvider pcProvider;
    private final SignalingProvider signalingProvider;
    private final Scheduler scheduler;

    private int localId = -1;
    private final Map peers = new HashMap();

    /**
     * Internal state for a single peer connection.
     *
     * <p>Implements {@link WebRTCPeer} to provide the public API for sending data
     * and querying connection status. Tracks the underlying peer connection handle,
     * data channel handles, ICE restart state, and timer handles.</p>
     *
     * <p>Instances are created when a CONNECT_REQUEST or OFFER message is received,
     * and removed from the peer map when {@link #close()} is called.</p>
     */
    class PeerState implements WebRTCPeer {

        /** The remote peer's signaling ID. */
        final int peerId;

        /** Opaque handle to the native RTCPeerConnection. */
        Object peerConnection;

        /** Opaque handle to the reliable data channel. */
        Object reliableChannel;

        /** Opaque handle to the unreliable data channel. */
        Object unreliableChannel;

        /** Whether the reliable data channel is open and ready. */
        volatile boolean connected;

        /** Whether ICE has entered CLOSED or FAILED state (prevents stale timer actions). */
        volatile boolean iceClosedOrFailed;

        /** Timestamp (ms) when ICE DISCONNECTED was first detected, for stale timer detection. */
        volatile long disconnectedAtMs;

        /** Number of ICE restart attempts since the last CONNECTED state. */
        volatile int iceRestartAttempts;

        /** Whether this peer is the SDP offerer (true) or answerer (false). */
        boolean isOfferer;

        /** Handle for the scheduled ICE restart timer on DISCONNECTED. */
        Object disconnectedTimerHandle;

        /** Handle for the scheduled ICE restart timer on FAILED (exponential backoff). */
        Object failedTimerHandle;

        /** Whether a server-reflexive (srflx) ICE candidate was observed. */
        boolean sawSrflx;

        /**
         * Creates a new peer state for the given remote peer ID.
         *
         * @param peerId the remote peer's signaling ID
         */
        PeerState(int peerId) {
            this.peerId = peerId;
        }

        /** {@inheritDoc} */
        public int getId() {
            return peerId;
        }

        /**
         * {@inheritDoc}
         *
         * <p>Sends via the platform's {@link PeerConnectionProvider#sendData(Object, byte[])}
         * on the reliable channel. No-op if not connected or the reliable channel is null.</p>
         */
        public void sendReliable(byte[] data) {
            if (reliableChannel != null && connected) {
                pcProvider.sendData(reliableChannel, data);
            }
        }

        /**
         * {@inheritDoc}
         *
         * <p>Checks the channel's buffered amount against
         * {@link WebRTCConfiguration#unreliableBufferLimit} before sending.
         * If the buffer is full, the packet is silently dropped. If the unreliable
         * channel is unavailable (null or not connected), falls back to
         * {@link #sendReliable(byte[])}.</p>
         */
        public void sendUnreliable(byte[] data) {
            if (unreliableChannel != null && connected) {
                try {
                    if (pcProvider.getBufferedAmount(unreliableChannel) > config.unreliableBufferLimit) {
                        return;
                    }
                } catch (Exception e) { /* send anyway */ }
                pcProvider.sendData(unreliableChannel, data);
            } else {
                sendReliable(data);
            }
        }

        /** {@inheritDoc} */
        public boolean isConnected() {
            return connected;
        }

        /**
         * {@inheritDoc}
         *
         * <p>Cancels any pending ICE restart timers, closes the native peer connection
         * via {@link PeerConnectionProvider#closePeerConnection(Object)}, nullifies
         * all handles, and removes this peer from the client's peer map.</p>
         */
        public void close() {
            connected = false;
            cancelTimers();
            if (peerConnection != null) {
                try {
                    pcProvider.closePeerConnection(peerConnection);
                } catch (Exception e) { /* ignore */ }
                peerConnection = null;
            }
            reliableChannel = null;
            unreliableChannel = null;
            peers.remove(Integer.valueOf(peerId));
        }

        /**
         * Cancels both the disconnected and failed ICE restart timers, if any.
         */
        void cancelTimers() {
            if (disconnectedTimerHandle != null) {
                scheduler.cancel(disconnectedTimerHandle);
                disconnectedTimerHandle = null;
            }
            if (failedTimerHandle != null) {
                scheduler.cancel(failedTimerHandle);
                failedTimerHandle = null;
            }
        }
    }

    /**
     * Creates a new {@code BaseWebRTCClient} with the given strategy implementations.
     *
     * <p>The client is not connected after construction; call {@link #connect()}
     * to initiate the signaling server connection.</p>
     *
     * @param tag               a log tag prefix (e.g. {@code "[WebRTC-Desktop] "})
     * @param config            the WebRTC and signaling configuration
     * @param listener          the listener for connection and data events (may be {@code null})
     * @param pcProvider        the platform-specific peer connection provider
     * @param signalingProvider the platform-specific signaling (WebSocket) provider
     * @param scheduler         the platform-specific task scheduler
     */
    public BaseWebRTCClient(String tag, WebRTCConfiguration config,
                             WebRTCClientListener listener,
                             PeerConnectionProvider pcProvider,
                             SignalingProvider signalingProvider,
                             Scheduler scheduler) {
        this.tag = tag;
        this.config = config;
        this.listener = listener;
        this.pcProvider = pcProvider;
        this.signalingProvider = signalingProvider;
        this.scheduler = scheduler;
    }

    // --- WebRTCClient interface ---

    /** {@inheritDoc} */
    public void connect() {
        String url = config.signalingServerUrl;
        if (config.room != null && !config.room.isEmpty()) {
            url += (url.contains("?") ? "&" : "?") + "room=" + config.room;
        }
        log("Connecting to signaling: " + url);
        signalingProvider.connect(url, new SignalingEventHandler() {
            public void onOpen() {
                log("Signaling WebSocket opened");
            }
            public void onMessage(SignalMessage msg) {
                handleSignalingMessage(msg);
            }
            public void onClose(String reason) {
                log("Signaling WebSocket closed: " + reason);
            }
            public void onError(String error) {
                if (listener != null) {
                    listener.onError("Signaling: " + error);
                }
            }
        });
    }

    /** {@inheritDoc} */
    public void disconnect() {
        List peerList = new ArrayList(peers.values());
        for (int i = 0; i < peerList.size(); i++) {
            ((PeerState) peerList.get(i)).close();
        }
        peers.clear();
        signalingProvider.close();
        scheduler.shutdown();
        localId = -1;
    }

    /** {@inheritDoc} */
    public boolean isConnectedToSignaling() {
        return signalingProvider.isOpen();
    }

    /** {@inheritDoc} */
    public void connectToPeer(int peerId) {
        SignalMessage req = new SignalMessage(
                SignalMessage.TYPE_CONNECT_REQUEST, localId, peerId, "");
        signalingProvider.send(req);
    }

    /** {@inheritDoc} */
    public void setListener(WebRTCClientListener listener) {
        this.listener = listener;
    }

    /** {@inheritDoc} */
    public int getLocalId() {
        return localId;
    }

    // --- Signaling message dispatch ---

    /**
     * Dispatches an incoming signaling message to the appropriate handler method.
     *
     * <p>Handles 8 of the 9 message types defined in {@link SignalMessage}
     * ({@code TYPE_PEER_LIST} responses are not used by the client and are
     * silently ignored along with any unknown message types).</p>
     *
     * @param msg the signaling message to handle
     */
    void handleSignalingMessage(SignalMessage msg) {
        switch (msg.type) {
            case SignalMessage.TYPE_WELCOME:
                try {
                    localId = Integer.parseInt(msg.data.trim());
                } catch (NumberFormatException e) { /* ignore */ }
                log("WELCOME received, localId=" + localId);
                if (listener != null) {
                    listener.onSignalingConnected(localId);
                }
                break;
            case SignalMessage.TYPE_CONNECT_REQUEST:
                log("CONNECT_REQUEST from peer " + msg.source);
                handleConnectRequest(msg.source);
                break;
            case SignalMessage.TYPE_OFFER:
                log("OFFER from peer " + msg.source + " (sdp length=" + (msg.data != null ? msg.data.length() : 0) + ")");
                handleOffer(msg.source, msg.data);
                break;
            case SignalMessage.TYPE_ANSWER:
                log("ANSWER from peer " + msg.source + " (sdp length=" + (msg.data != null ? msg.data.length() : 0) + ")");
                handleAnswer(msg.source, msg.data);
                break;
            case SignalMessage.TYPE_ICE:
                handleIce(msg.source, msg.data);
                break;
            case SignalMessage.TYPE_ERROR:
                log("ERROR from signaling: " + msg.data);
                if (listener != null) {
                    listener.onError(msg.data);
                }
                break;
            case SignalMessage.TYPE_PEER_JOINED:
                log("PEER_JOINED: peer " + msg.source);
                if (listener != null) {
                    listener.onPeerJoined(msg.source);
                }
                break;
            case SignalMessage.TYPE_PEER_LEFT:
                log("PEER_LEFT: peer " + msg.source);
                if (listener != null) {
                    listener.onPeerLeft(msg.source);
                }
                break;
            default:
                break;
        }
    }

    // --- Connection handlers ---

    /**
     * Handles a CONNECT_REQUEST message by creating an SDP offer.
     *
     * <p>This method:</p>
     * <ol>
     *   <li>Initializes the WebRTC factory via {@link PeerConnectionProvider#initialize()}</li>
     *   <li>Creates a new peer connection and registers event handlers</li>
     *   <li>Creates both data channels (reliable + unreliable)</li>
     *   <li>Creates an SDP offer and sends it to the remote peer via signaling</li>
     * </ol>
     *
     * @param remotePeerId the peer ID of the connection requester
     */
    private void handleConnectRequest(final int remotePeerId) {
        if (!pcProvider.initialize()) {
            if (listener != null) {
                listener.onError("WebRTC factory initialization failed");
            }
            return;
        }

        final PeerState peer = new PeerState(remotePeerId);
        peer.isOfferer = true;
        peers.put(Integer.valueOf(remotePeerId), peer);

        DataChannelEventHandler dcHandler = createDataChannelHandler(peer);
        PeerEventHandler pcHandler = createPeerEventHandler(peer, dcHandler);

        try {
            peer.peerConnection = pcProvider.createPeerConnection(config, pcHandler);
        } catch (Exception e) {
            if (listener != null) {
                listener.onError("Failed to create peer connection: " + e.getMessage());
            }
            return;
        }

        if (peer.peerConnection == null) {
            if (listener != null) {
                listener.onError("Failed to create peer connection");
            }
            return;
        }

        ChannelPair channels = pcProvider.createDataChannels(
                peer.peerConnection, config.unreliableMaxRetransmits, dcHandler);
        peer.reliableChannel = channels.reliableChannel;
        peer.unreliableChannel = channels.unreliableChannel;

        pcProvider.createOffer(peer.peerConnection, new SdpResultCallback() {
            public void onSuccess(String sdp) {
                SignalMessage offer = new SignalMessage(
                        SignalMessage.TYPE_OFFER, localId, remotePeerId, sdp);
                signalingProvider.send(offer);
            }
            public void onFailure(String error) {
                if (listener != null) {
                    listener.onError("Create offer failed: " + error);
                }
            }
        });
    }

    /**
     * Handles a received SDP offer by creating an answer.
     *
     * <p>This method:</p>
     * <ol>
     *   <li>Initializes the WebRTC factory via {@link PeerConnectionProvider#initialize()}</li>
     *   <li>Creates a new peer connection and registers event handlers</li>
     *   <li>Sets the remote offer and creates a local answer</li>
     *   <li>Sends the answer to the remote peer via signaling</li>
     * </ol>
     *
     * <p>Note: data channels are <em>not</em> created here. On the answerer side,
     * channels are received via {@link PeerEventHandler#onDataChannel(Object, String)}.</p>
     *
     * @param remotePeerId the peer ID of the offerer
     * @param sdpOffer     the remote SDP offer string
     */
    private void handleOffer(final int remotePeerId, String sdpOffer) {
        if (!pcProvider.initialize()) {
            if (listener != null) {
                listener.onError("WebRTC factory initialization failed");
            }
            return;
        }

        final PeerState peer = new PeerState(remotePeerId);
        peer.isOfferer = false;
        peers.put(Integer.valueOf(remotePeerId), peer);

        DataChannelEventHandler dcHandler = createDataChannelHandler(peer);
        PeerEventHandler pcHandler = createPeerEventHandler(peer, dcHandler);

        try {
            peer.peerConnection = pcProvider.createPeerConnection(config, pcHandler);
        } catch (Exception e) {
            if (listener != null) {
                listener.onError("Failed to create peer connection: " + e.getMessage());
            }
            return;
        }

        if (peer.peerConnection == null) {
            if (listener != null) {
                listener.onError("Failed to create peer connection");
            }
            return;
        }

        pcProvider.handleOffer(peer.peerConnection, sdpOffer, new SdpResultCallback() {
            public void onSuccess(String answerSdp) {
                SignalMessage answer = new SignalMessage(
                        SignalMessage.TYPE_ANSWER, localId, remotePeerId, answerSdp);
                signalingProvider.send(answer);
            }
            public void onFailure(String error) {
                if (listener != null) {
                    listener.onError("WebRTC handshake failed: " + error);
                }
            }
        });
    }

    /**
     * Handles a received SDP answer by setting it as the remote description.
     *
     * <p>Ignored if no peer state exists for the given remote peer ID
     * (e.g. if the connection was closed before the answer arrived).</p>
     *
     * @param remotePeerId the peer ID of the answerer
     * @param sdpAnswer    the remote SDP answer string
     */
    private void handleAnswer(int remotePeerId, String sdpAnswer) {
        PeerState peer = (PeerState) peers.get(Integer.valueOf(remotePeerId));
        if (peer == null || peer.peerConnection == null) {
            return;
        }
        pcProvider.setRemoteAnswer(peer.peerConnection, sdpAnswer);
    }

    /**
     * Handles a received ICE candidate by adding it to the peer connection.
     *
     * <p>Ignored if no peer state exists for the given remote peer ID.</p>
     *
     * @param remotePeerId the peer ID of the candidate sender
     * @param iceJson      the JSON-encoded ICE candidate
     */
    private void handleIce(int remotePeerId, String iceJson) {
        PeerState peer = (PeerState) peers.get(Integer.valueOf(remotePeerId));
        if (peer == null || peer.peerConnection == null) {
            return;
        }
        pcProvider.addIceCandidate(peer.peerConnection, iceJson);
    }

    // --- ICE state machine ---

    /**
     * Handles a connection state change for a peer, implementing the full ICE
     * restart state machine.
     *
     * <p>State transitions:</p>
     * <ul>
     *   <li><b>CONNECTED</b> - Resets ICE restart counters and cancels timers.
     *       If the reliable channel is closed despite ICE recovery, fires disconnect.</li>
     *   <li><b>DISCONNECTED</b> - Records the disconnection timestamp and schedules
     *       an ICE restart after {@link WebRTCConfiguration#iceRestartDelayMs}. If a
     *       previous disconnected timer exists, it is cancelled first.</li>
     *   <li><b>FAILED</b> - Increments the restart attempt counter. If under the
     *       maximum ({@link WebRTCConfiguration#maxIceRestartAttempts}), schedules an
     *       ICE restart with exponential backoff. Otherwise, fires permanent disconnect.</li>
     *   <li><b>CLOSED</b> - Cancels all timers and fires permanent disconnect.</li>
     * </ul>
     *
     * @param peer  the peer whose connection state changed
     * @param state the new connection state (one of the {@link ConnectionState} constants)
     */
    void handleConnectionStateChanged(final PeerState peer, int state) {
        log("Peer " + peer.peerId + " connection state: " + ConnectionState.toString(state)
                + " (connected=" + peer.connected + ", reliableChannel="
                + (peer.reliableChannel != null ? "set" : "null")
                + ", channelOpen=" + (peer.reliableChannel != null && pcProvider.isChannelOpen(peer.reliableChannel)) + ")");

        if (state == ConnectionState.CONNECTED) {
            peer.iceClosedOrFailed = false;
            peer.disconnectedAtMs = 0;
            peer.iceRestartAttempts = 0;
            peer.cancelTimers();

            if (!peer.sawSrflx) {
                log("WARNING: No server-reflexive candidates for peer "
                        + peer.peerId + " — STUN servers may be unreachable");
            }

            // On ICE restart recovery, check if data channel survived.
            // Skip during initial connection: channel is created but not yet
            // open (DTLS completes after ICE CONNECTED).
            if (peer.connected && peer.reliableChannel != null
                    && !pcProvider.isChannelOpen(peer.reliableChannel)) {
                log("Peer " + peer.peerId + ": data channel lost after ICE restart, disconnecting");
                peer.connected = false;
                if (listener != null) {
                    listener.onDisconnected(peer);
                }
            }

        } else if (state == ConnectionState.DISCONNECTED) {
            peer.disconnectedAtMs = System.currentTimeMillis();
            final long stamp = peer.disconnectedAtMs;

            if (peer.disconnectedTimerHandle != null) {
                scheduler.cancel(peer.disconnectedTimerHandle);
            }
            peer.disconnectedTimerHandle = scheduler.schedule(new Runnable() {
                public void run() {
                    try {
                        if (peer.disconnectedAtMs == stamp
                                && !peer.iceClosedOrFailed
                                && peer.peerConnection != null) {
                            pcProvider.restartIce(peer.peerConnection);
                        }
                    } catch (Exception e) {
                        /* ICE restart failed */
                    }
                }
            }, config.iceRestartDelayMs);

        } else if (state == ConnectionState.FAILED) {
            peer.disconnectedAtMs = 0;
            peer.iceRestartAttempts++;
            peer.iceClosedOrFailed = true;
            peer.cancelTimers();

            if (peer.iceRestartAttempts > config.maxIceRestartAttempts) {
                log("Peer " + peer.peerId + ": ICE FAILED, max restart attempts ("
                        + config.maxIceRestartAttempts + ") exceeded — disconnecting");
                peer.connected = false;
                if (listener != null) {
                    listener.onDisconnected(peer);
                }
            } else {
                long backoffMs = (long) config.iceBackoffBaseMs
                        * (1L << (peer.iceRestartAttempts - 1));

                peer.failedTimerHandle = scheduler.schedule(new Runnable() {
                    public void run() {
                        try {
                            if (peer.peerConnection != null && peer.iceClosedOrFailed) {
                                pcProvider.restartIce(peer.peerConnection);
                            }
                        } catch (Exception e) {
                            peer.connected = false;
                            if (listener != null) {
                                listener.onDisconnected(peer);
                            }
                        }
                    }
                }, backoffMs);
            }

        } else if (state == ConnectionState.CLOSED) {
            peer.iceClosedOrFailed = true;
            peer.disconnectedAtMs = 0;
            peer.cancelTimers();
            peer.connected = false;
            if (listener != null) {
                listener.onDisconnected(peer);
            }
        }
    }

    // --- Event handler factories ---

    /**
     * Creates a {@link PeerEventHandler} that wires platform peer connection events
     * to the shared logic in this client.
     *
     * <p>The handler:</p>
     * <ul>
     *   <li>Forwards ICE candidates to the remote peer via the signaling server</li>
     *   <li>Delegates connection state changes to {@link #handleConnectionStateChanged}</li>
     *   <li>Routes incoming data channels to the peer state and registers event handlers</li>
     * </ul>
     *
     * @param peer      the peer state to associate with this handler
     * @param dcHandler the data channel event handler for incoming channels
     * @return a new peer event handler
     */
    PeerEventHandler createPeerEventHandler(final PeerState peer,
                                             final DataChannelEventHandler dcHandler) {
        return new PeerEventHandler() {
            public void onIceCandidate(String candidateJson) {
                if (candidateJson != null && candidateJson.contains("srflx")) {
                    peer.sawSrflx = true;
                }
                SignalMessage ice = new SignalMessage(
                        SignalMessage.TYPE_ICE, localId, peer.peerId, candidateJson);
                signalingProvider.send(ice);
            }

            public void onConnectionStateChanged(int state) {
                handleConnectionStateChanged(peer, state);
            }

            public void onDataChannel(Object channel, String label) {
                if ("reliable".equals(label)) {
                    peer.reliableChannel = channel;
                    pcProvider.setupReceivedChannel(channel, true, dcHandler);
                } else if ("unreliable".equals(label)) {
                    peer.unreliableChannel = channel;
                    pcProvider.setupReceivedChannel(channel, false, dcHandler);
                }
            }
        };
    }

    /**
     * Creates a {@link DataChannelEventHandler} that wires data channel lifecycle
     * events to the peer state and {@link WebRTCClientListener}.
     *
     * <p>The handler:</p>
     * <ul>
     *   <li>Sets the peer's connected flag on reliable channel open, and notifies
     *       {@link WebRTCClientListener#onConnected(WebRTCPeer)}</li>
     *   <li>Clears the connected flag on reliable channel close, and notifies
     *       {@link WebRTCClientListener#onDisconnected(WebRTCPeer)}</li>
     *   <li>Logs unreliable channel open/close without affecting connection state</li>
     *   <li>Forwards received messages to
     *       {@link WebRTCClientListener#onMessage(WebRTCPeer, byte[], boolean)}</li>
     * </ul>
     *
     * @param peer the peer state to associate with this handler
     * @return a new data channel event handler
     */
    DataChannelEventHandler createDataChannelHandler(final PeerState peer) {
        return new DataChannelEventHandler() {
            public void onReliableOpen() {
                log("Peer " + peer.peerId + ": reliable data channel OPEN");
                peer.connected = true;
                if (listener != null) {
                    listener.onConnected(peer);
                }
            }
            public void onReliableClose() {
                log("Peer " + peer.peerId + ": reliable data channel CLOSED");
                peer.connected = false;
                if (listener != null) {
                    listener.onDisconnected(peer);
                }
            }
            public void onUnreliableOpen() {
                log("Peer " + peer.peerId + ": unreliable data channel OPEN");
            }
            public void onUnreliableClose() {
                log("Peer " + peer.peerId + ": unreliable data channel CLOSED");
            }
            public void onMessage(byte[] data, boolean reliable) {
                if (listener != null) {
                    listener.onMessage(peer, data, reliable);
                }
            }
        };
    }

    // --- Package-private accessors for testing ---

    /**
     * Returns the WebRTC configuration. Package-private for unit testing.
     *
     * @return the configuration
     */
    WebRTCConfiguration getConfig() {
        return config;
    }

    /**
     * Returns the current listener. Package-private for unit testing.
     *
     * @return the listener, or {@code null} if none is set
     */
    WebRTCClientListener getListener() {
        return listener;
    }

    /**
     * Returns the map of connected peers (peer ID to PeerState).
     * Package-private for unit testing.
     *
     * @return the peer map
     */
    Map getPeers() {
        return peers;
    }

    // --- Logging ---

    /**
     * Logs a message to standard output with the client's tag prefix.
     *
     * @param msg the message to log
     */
    private void log(String msg) {
        Log.debug(tag + msg);
    }
}
