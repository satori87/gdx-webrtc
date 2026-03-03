package com.github.satori87.gdx.webrtc.transport;

import com.github.satori87.gdx.webrtc.ChannelPair;
import com.github.satori87.gdx.webrtc.ConnectionState;
import com.github.satori87.gdx.webrtc.DataChannelEventHandler;
import com.github.satori87.gdx.webrtc.PeerConnectionProvider;
import com.github.satori87.gdx.webrtc.PeerEventHandler;
import com.github.satori87.gdx.webrtc.Scheduler;
import com.github.satori87.gdx.webrtc.SdpResultCallback;
import com.github.satori87.gdx.webrtc.WebRTCConfiguration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Platform-agnostic WebRTC server transport implementation.
 *
 * <p>Manages multiple client peer connections using external signaling. This
 * transport is the <em>offerer</em> side — it creates SDP offers for incoming
 * clients, creates both data channels, and receives answers.</p>
 *
 * <p>Each client is assigned a unique connection ID ({@code connId}) starting
 * from 1 and incrementing monotonically. The server can send data to individual
 * clients by connId or broadcast to all connected clients.</p>
 *
 * <p>This class does <strong>not</strong> use the library's
 * {@link com.github.satori87.gdx.webrtc.SignalingProvider}. All signaling (SDP/ICE
 * exchange) is handled externally by the application through the
 * {@link WebRTCServerTransport.SignalCallback}.</p>
 *
 * <p>The ICE state machine is replicated from
 * {@link com.github.satori87.gdx.webrtc.BaseWebRTCClient} and provides identical
 * behavior per-peer: automatic ICE restart on DISCONNECTED with configurable delay,
 * exponential backoff retry on FAILED, and permanent disconnect on CLOSED or
 * max retries exceeded.</p>
 *
 * <p>Instances are created by platform factories via
 * {@link com.github.satori87.gdx.webrtc.WebRTCFactory#createServerTransport(WebRTCConfiguration)}
 * or directly:</p>
 * <pre>
 * BaseWebRTCServerTransport transport = new BaseWebRTCServerTransport(
 *         "[WebRTC-Server] ", config,
 *         new DesktopPeerConnectionProvider(),
 *         new ExecutorScheduler());
 * </pre>
 *
 * @see WebRTCServerTransport
 * @see BaseWebRTCClientTransport
 * @see WebRTCTransports
 */
public class BaseWebRTCServerTransport implements WebRTCServerTransport {

    private final String tag;
    private final WebRTCConfiguration config;
    private final PeerConnectionProvider pcProvider;
    private final Scheduler scheduler;

    private ServerTransportListener listener;
    private int nextConnId = 1;

    // Dynamic TURN override (applied on top of config)
    private String turnUrl;
    private String turnUsername;
    private String turnPassword;

    /**
     * Internal state for a single client peer connection.
     *
     * <p>Tracks the native peer connection handle, data channel handles,
     * connection readiness, and ICE restart state. Instances are created by
     * {@link #createPeerForOffer(SignalCallback)} and stored in the
     * {@link #peers} map.</p>
     */
    static class PeerState {

        /** The connection ID assigned to this client. */
        final int connId;

        /** Opaque handle to the native RTCPeerConnection. */
        Object peerConnection;

        /** Opaque handle to the reliable data channel. */
        Object reliableChannel;

        /** Opaque handle to the unreliable data channel. */
        Object unreliableChannel;

        /** Whether the reliable data channel is open and ready. */
        volatile boolean connected;

        /** Whether the unreliable data channel is currently open. */
        volatile boolean unreliableOpen;

        /** Whether ICE has entered CLOSED or FAILED state. */
        volatile boolean iceClosedOrFailed;

        /** Timestamp (ms) when ICE DISCONNECTED was first detected. */
        volatile long disconnectedAtMs;

        /** Number of ICE restart attempts since the last CONNECTED state. */
        volatile int iceRestartAttempts;

        /** Handle for the scheduled ICE restart timer on DISCONNECTED. */
        Object disconnectedTimerHandle;

        /** Handle for the scheduled ICE restart timer on FAILED. */
        Object failedTimerHandle;

        /** Whether a server-reflexive (srflx) ICE candidate was observed. */
        boolean sawSrflx;

        /**
         * Creates a new peer state for the given connection ID.
         *
         * @param connId the connection ID assigned to this client
         */
        PeerState(int connId) {
            this.connId = connId;
        }
    }

    /** Map of connection ID to peer state. */
    private final Map peers = new HashMap();

    /**
     * Creates a new server transport with the given strategy implementations.
     *
     * <p>The transport is ready to accept clients immediately. Call
     * {@link #createPeerForOffer(SignalCallback)} to create a peer connection
     * for each incoming client.</p>
     *
     * @param tag        a log tag prefix (e.g. {@code "[WebRTC-Server] "})
     * @param config     the WebRTC configuration containing ICE server settings
     *                   and connection parameters
     * @param pcProvider the platform-specific peer connection provider
     * @param scheduler  the platform-specific task scheduler for ICE restart timers
     */
    public BaseWebRTCServerTransport(String tag, WebRTCConfiguration config,
                                      PeerConnectionProvider pcProvider,
                                      Scheduler scheduler) {
        this.tag = tag;
        this.config = config;
        this.pcProvider = pcProvider;
        this.scheduler = scheduler;
    }

    // --- WebRTCServerTransport ---

    /** {@inheritDoc} */
    public void setTurnServer(String url, String username, String password) {
        this.turnUrl = url;
        this.turnUsername = username;
        this.turnPassword = password;
    }

    /** {@inheritDoc} */
    public int createPeerForOffer(final SignalCallback callback) {
        final int connId = nextConnId++;

        if (!pcProvider.initialize()) {
            log("WebRTC factory initialization failed for connId " + connId);
            return connId;
        }

        final PeerState peer = new PeerState(connId);
        peers.put(Integer.valueOf(connId), peer);

        DataChannelEventHandler dcHandler = createDataChannelHandler(peer);
        PeerEventHandler pcHandler = createPeerEventHandler(peer, dcHandler, callback);

        WebRTCConfiguration effectiveConfig = getEffectiveConfig();

        try {
            peer.peerConnection = pcProvider.createPeerConnection(effectiveConfig, pcHandler);
        } catch (Exception e) {
            log("createPeerConnection FAILED for connId " + connId + ": " + e);
            peers.remove(Integer.valueOf(connId));
            return connId;
        }

        if (peer.peerConnection == null) {
            log("createPeerConnection returned null for connId " + connId);
            peers.remove(Integer.valueOf(connId));
            return connId;
        }

        ChannelPair channels = pcProvider.createDataChannels(
                peer.peerConnection, config.unreliableMaxRetransmits, dcHandler);
        peer.reliableChannel = channels.reliableChannel;
        peer.unreliableChannel = channels.unreliableChannel;

        pcProvider.createOffer(peer.peerConnection, new SdpResultCallback() {
            public void onSuccess(String sdp) {
                log("Offer created for connId " + connId);
                callback.onOffer(connId, sdp);
            }
            public void onFailure(String error) {
                log("Create offer failed for connId " + connId + ": " + error);
            }
        });

        return connId;
    }

    /** {@inheritDoc} */
    public void setAnswer(int connId, String sdpAnswer) {
        PeerState peer = (PeerState) peers.get(Integer.valueOf(connId));
        if (peer == null || peer.peerConnection == null) {
            return;
        }
        log("Setting answer for connId " + connId);
        pcProvider.setRemoteAnswer(peer.peerConnection, sdpAnswer);
    }

    /** {@inheritDoc} */
    public void addIceCandidate(int connId, String iceJson) {
        PeerState peer = (PeerState) peers.get(Integer.valueOf(connId));
        if (peer == null || peer.peerConnection == null) {
            return;
        }
        pcProvider.addIceCandidate(peer.peerConnection, iceJson);
    }

    // --- ServerTransport ---

    /** {@inheritDoc} */
    public void stop() {
        List peerList = new ArrayList(peers.values());
        for (int i = 0; i < peerList.size(); i++) {
            PeerState peer = (PeerState) peerList.get(i);
            closePeer(peer);
        }
        peers.clear();
        scheduler.shutdown();
    }

    /** {@inheritDoc} */
    public void sendReliable(int connId, byte[] data) {
        PeerState peer = (PeerState) peers.get(Integer.valueOf(connId));
        if (peer != null && peer.reliableChannel != null && peer.connected) {
            pcProvider.sendData(peer.reliableChannel, data);
        }
    }

    /** {@inheritDoc} */
    public void sendUnreliable(int connId, byte[] data) {
        PeerState peer = (PeerState) peers.get(Integer.valueOf(connId));
        if (peer == null) {
            return;
        }
        if (peer.unreliableChannel != null && peer.connected) {
            try {
                if (pcProvider.getBufferedAmount(peer.unreliableChannel) > config.unreliableBufferLimit) {
                    return;
                }
            } catch (Exception e) { /* send anyway */ }
            pcProvider.sendData(peer.unreliableChannel, data);
        } else if (peer.reliableChannel != null && peer.connected) {
            pcProvider.sendData(peer.reliableChannel, data);
        }
    }

    /** {@inheritDoc} */
    public void broadcastReliable(byte[] data) {
        List peerList = new ArrayList(peers.values());
        for (int i = 0; i < peerList.size(); i++) {
            PeerState peer = (PeerState) peerList.get(i);
            if (peer.reliableChannel != null && peer.connected) {
                pcProvider.sendData(peer.reliableChannel, data);
            }
        }
    }

    /** {@inheritDoc} */
    public void broadcastUnreliable(byte[] data) {
        List peerList = new ArrayList(peers.values());
        for (int i = 0; i < peerList.size(); i++) {
            PeerState peer = (PeerState) peerList.get(i);
            if (peer.unreliableChannel != null && peer.connected) {
                try {
                    if (pcProvider.getBufferedAmount(peer.unreliableChannel) > config.unreliableBufferLimit) {
                        continue;
                    }
                } catch (Exception e) { /* send anyway */ }
                pcProvider.sendData(peer.unreliableChannel, data);
            } else if (peer.reliableChannel != null && peer.connected) {
                pcProvider.sendData(peer.reliableChannel, data);
            }
        }
    }

    /** {@inheritDoc} */
    public void disconnect(int connId) {
        PeerState peer = (PeerState) peers.remove(Integer.valueOf(connId));
        if (peer != null) {
            closePeer(peer);
        }
    }

    /** {@inheritDoc} */
    public int getConnectionCount() {
        int count = 0;
        List peerList = new ArrayList(peers.values());
        for (int i = 0; i < peerList.size(); i++) {
            if (((PeerState) peerList.get(i)).connected) {
                count++;
            }
        }
        return count;
    }

    /** {@inheritDoc} */
    public void setListener(ServerTransportListener listener) {
        this.listener = listener;
    }

    // --- ICE state machine (per-peer) ---

    /**
     * Handles a connection state change for a specific peer, implementing
     * the full ICE restart state machine.
     *
     * <p>Behavior is identical to
     * {@link com.github.satori87.gdx.webrtc.BaseWebRTCClient#handleConnectionStateChanged}
     * but adapted for the server transport's multi-peer model: listener
     * callbacks include the peer's {@code connId}.</p>
     *
     * @param peer  the peer whose connection state changed
     * @param state the new connection state (one of the {@link ConnectionState} constants)
     */
    void handleConnectionStateChanged(final PeerState peer, int state) {
        log("Peer " + peer.connId + " connection state: " + state);

        if (state == ConnectionState.CONNECTED) {
            peer.iceClosedOrFailed = false;
            peer.disconnectedAtMs = 0;
            peer.iceRestartAttempts = 0;
            cancelTimers(peer);

            if (!peer.sawSrflx) {
                log("WARNING: No server-reflexive candidates for peer "
                        + peer.connId + " — STUN servers may be unreachable");
            }

            if (peer.reliableChannel != null
                    && !pcProvider.isChannelOpen(peer.reliableChannel)) {
                log("Peer " + peer.connId
                        + " ICE recovered but reliable channel is not open — disconnecting");
                peer.connected = false;
                if (listener != null) {
                    listener.onClientDisconnected(peer.connId);
                }
            }

        } else if (state == ConnectionState.DISCONNECTED) {
            peer.disconnectedAtMs = System.currentTimeMillis();
            final long stamp = peer.disconnectedAtMs;
            log("Peer " + peer.connId + " temporarily disconnected, will restart ICE in "
                    + config.iceRestartDelayMs + "ms...");

            if (peer.disconnectedTimerHandle != null) {
                scheduler.cancel(peer.disconnectedTimerHandle);
            }
            peer.disconnectedTimerHandle = scheduler.schedule(new Runnable() {
                public void run() {
                    try {
                        if (peer.disconnectedAtMs == stamp
                                && !peer.iceClosedOrFailed
                                && peer.peerConnection != null) {
                            log("Peer " + peer.connId
                                    + " still disconnected, restarting ICE");
                            pcProvider.restartIce(peer.peerConnection);
                        }
                    } catch (Exception e) {
                        log("Delayed ICE restart failed for peer "
                                + peer.connId + ": " + e);
                    }
                }
            }, config.iceRestartDelayMs);

        } else if (state == ConnectionState.FAILED) {
            peer.disconnectedAtMs = 0;
            peer.iceRestartAttempts++;
            peer.iceClosedOrFailed = true;
            cancelTimers(peer);

            if (peer.iceRestartAttempts > config.maxIceRestartAttempts) {
                log("Peer " + peer.connId + " connection failed after "
                        + peer.iceRestartAttempts
                        + " ICE restart attempts, giving up");
                peer.connected = false;
                if (listener != null) {
                    listener.onClientDisconnected(peer.connId);
                }
            } else {
                long backoffMs = (long) config.iceBackoffBaseMs
                        * (1L << (peer.iceRestartAttempts - 1));
                log("Peer " + peer.connId
                        + " connection failed, ICE restart attempt "
                        + peer.iceRestartAttempts + " in " + backoffMs + "ms...");

                peer.failedTimerHandle = scheduler.schedule(new Runnable() {
                    public void run() {
                        try {
                            if (peer.peerConnection != null
                                    && peer.iceClosedOrFailed) {
                                pcProvider.restartIce(peer.peerConnection);
                            }
                        } catch (Exception e) {
                            log("ICE restart failed for peer "
                                    + peer.connId + ": " + e);
                            peer.connected = false;
                            if (listener != null) {
                                listener.onClientDisconnected(peer.connId);
                            }
                        }
                    }
                }, backoffMs);
            }

        } else if (state == ConnectionState.CLOSED) {
            peer.iceClosedOrFailed = true;
            peer.disconnectedAtMs = 0;
            cancelTimers(peer);
            peer.connected = false;
            if (listener != null) {
                listener.onClientDisconnected(peer.connId);
            }
        }
    }

    // --- Internal helpers ---

    /**
     * Closes a peer connection and cancels its ICE restart timers.
     *
     * @param peer the peer to close
     */
    private void closePeer(PeerState peer) {
        peer.connected = false;
        cancelTimers(peer);
        if (peer.peerConnection != null) {
            try {
                pcProvider.closePeerConnection(peer.peerConnection);
            } catch (Exception e) { /* ignore */ }
            peer.peerConnection = null;
        }
        peer.reliableChannel = null;
        peer.unreliableChannel = null;
    }

    /**
     * Cancels both ICE restart timers for a peer.
     *
     * @param peer the peer whose timers to cancel
     */
    private void cancelTimers(PeerState peer) {
        if (peer.disconnectedTimerHandle != null) {
            scheduler.cancel(peer.disconnectedTimerHandle);
            peer.disconnectedTimerHandle = null;
        }
        if (peer.failedTimerHandle != null) {
            scheduler.cancel(peer.failedTimerHandle);
            peer.failedTimerHandle = null;
        }
    }

    /**
     * Creates a {@link PeerEventHandler} that wires platform peer connection
     * events to this transport's ICE state machine and signaling callback.
     *
     * @param peer      the peer state to associate with this handler
     * @param dcHandler the data channel event handler (not used on server side
     *                  since channels are created locally, but kept for
     *                  ondatachannel events from remote)
     * @param callback  the signaling callback for ICE candidates
     * @return a new peer event handler
     */
    private PeerEventHandler createPeerEventHandler(
            final PeerState peer,
            final DataChannelEventHandler dcHandler,
            final SignalCallback callback) {
        return new PeerEventHandler() {
            public void onIceCandidate(String candidateJson) {
                if (candidateJson != null && candidateJson.contains("srflx")) {
                    peer.sawSrflx = true;
                }
                callback.onIceCandidate(peer.connId, candidateJson);
            }

            public void onConnectionStateChanged(int state) {
                handleConnectionStateChanged(peer, state);
            }

            public void onDataChannel(Object channel, String label) {
                log("Peer " + peer.connId
                        + " received data channel (unexpected on server): " + label);
            }
        };
    }

    /**
     * Creates a {@link DataChannelEventHandler} that wires data channel lifecycle
     * events to the peer's connection state and the server transport listener.
     *
     * @param peer the peer state to associate with this handler
     * @return a new data channel event handler
     */
    private DataChannelEventHandler createDataChannelHandler(final PeerState peer) {
        return new DataChannelEventHandler() {
            public void onReliableOpen() {
                log("Peer " + peer.connId + " reliable channel OPEN");
                peer.connected = true;
                if (listener != null) {
                    listener.onClientConnected(peer.connId);
                }
            }
            public void onReliableClose() {
                log("Peer " + peer.connId + " reliable channel CLOSED");
                peer.connected = false;
                if (listener != null) {
                    listener.onClientDisconnected(peer.connId);
                }
            }
            public void onUnreliableOpen() {
                log("Peer " + peer.connId + " unreliable channel OPEN");
                peer.unreliableOpen = true;
            }
            public void onUnreliableClose() {
                log("Peer " + peer.connId + " unreliable channel CLOSED");
                peer.unreliableOpen = false;
            }
            public void onMessage(byte[] data, boolean reliable) {
                if (listener != null) {
                    listener.onClientMessage(peer.connId, data, reliable);
                }
            }
        };
    }

    /**
     * Returns the effective configuration, applying any dynamic TURN override.
     *
     * <p>If {@link #setTurnServer(String, String, String)} was called, returns
     * a copy of the base configuration with the TURN fields replaced. Otherwise,
     * returns the base configuration as-is.</p>
     *
     * @return the effective WebRTC configuration
     */
    private WebRTCConfiguration getEffectiveConfig() {
        if (turnUrl == null) {
            return config;
        }
        WebRTCConfiguration effective = new WebRTCConfiguration();
        effective.stunServer = config.stunServer;
        effective.stunServers = config.stunServers;
        effective.turnServer = turnUrl;
        effective.turnUsername = turnUsername;
        effective.turnPassword = turnPassword;
        effective.forceRelay = config.forceRelay;
        effective.iceRestartDelayMs = config.iceRestartDelayMs;
        effective.maxIceRestartAttempts = config.maxIceRestartAttempts;
        effective.unreliableBufferLimit = config.unreliableBufferLimit;
        effective.iceBackoffBaseMs = config.iceBackoffBaseMs;
        effective.unreliableMaxRetransmits = config.unreliableMaxRetransmits;
        return effective;
    }

    /**
     * Logs a message to standard output with the transport's tag prefix.
     *
     * @param msg the message to log
     */
    private void log(String msg) {
        System.out.println(tag + msg);
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
    ServerTransportListener getListener() {
        return listener;
    }

    /**
     * Returns the peer map (connId to PeerState). Package-private for unit testing.
     *
     * @return the peer map
     */
    Map getPeers() {
        return peers;
    }

    /**
     * Returns the next connection ID that will be assigned. Package-private for testing.
     *
     * @return the next connId
     */
    int getNextConnId() {
        return nextConnId;
    }
}
