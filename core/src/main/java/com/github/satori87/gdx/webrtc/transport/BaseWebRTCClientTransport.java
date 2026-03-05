package com.github.satori87.gdx.webrtc.transport;

import com.github.satori87.gdx.webrtc.util.Log;
import com.github.satori87.gdx.webrtc.ConnectionState;
import com.github.satori87.gdx.webrtc.DataChannelEventHandler;
import com.github.satori87.gdx.webrtc.PeerConnectionProvider;
import com.github.satori87.gdx.webrtc.PeerEventHandler;
import com.github.satori87.gdx.webrtc.Scheduler;
import com.github.satori87.gdx.webrtc.SdpResultCallback;
import com.github.satori87.gdx.webrtc.WebRTCConfiguration;

/**
 * Platform-agnostic WebRTC client transport implementation.
 *
 * <p>Manages a single peer connection to a server using external signaling.
 * This transport is the <em>answerer</em> side — it receives an SDP offer from the
 * server, creates an answer, and receives data channels created by the server.</p>
 *
 * <p>This class does <strong>not</strong> use the library's
 * {@link com.github.satori87.gdx.webrtc.SignalingProvider}. All signaling (SDP/ICE
 * exchange) is handled externally by the application through the
 * {@link WebRTCClientTransport.SignalCallback}.</p>
 *
 * <p>The ICE state machine is replicated from
 * {@link com.github.satori87.gdx.webrtc.BaseWebRTCClient} and provides identical
 * behavior: automatic ICE restart on DISCONNECTED with configurable delay,
 * exponential backoff retry on FAILED, and permanent disconnect on CLOSED or
 * max retries exceeded.</p>
 *
 * <p>Instances are created by platform factories via
 * {@link com.github.satori87.gdx.webrtc.WebRTCFactory#createClientTransport(WebRTCConfiguration)}
 * or directly:</p>
 * <pre>
 * BaseWebRTCClientTransport transport = new BaseWebRTCClientTransport(
 *         "[WebRTC-Desktop] ", config,
 *         new DesktopPeerConnectionProvider(),
 *         new ExecutorScheduler());
 * </pre>
 *
 * @see WebRTCClientTransport
 * @see BaseWebRTCServerTransport
 * @see WebRTCTransports
 */
public class BaseWebRTCClientTransport implements WebRTCClientTransport {

    private final String tag;
    private final WebRTCConfiguration config;
    private final PeerConnectionProvider pcProvider;
    private final Scheduler scheduler;

    private TransportListener listener;

    /** Opaque handle to the native RTCPeerConnection. */
    private Object peerConnection;

    /** Opaque handle to the reliable data channel. */
    private Object reliableChannel;

    /** Opaque handle to the unreliable data channel. */
    private Object unreliableChannel;

    /** Whether the reliable data channel is open and ready. */
    private volatile boolean connected;

    // --- ICE state machine fields ---

    /** Whether ICE has entered CLOSED or FAILED state (prevents stale timer actions). */
    private volatile boolean iceClosedOrFailed;

    /** Timestamp (ms) when ICE DISCONNECTED was first detected, for stale timer detection. */
    private volatile long disconnectedAtMs;

    /** Number of ICE restart attempts since the last CONNECTED state. */
    private volatile int iceRestartAttempts;

    /** Handle for the scheduled ICE restart timer on DISCONNECTED. */
    private Object disconnectedTimerHandle;

    /** Handle for the scheduled ICE restart timer on FAILED (exponential backoff). */
    private Object failedTimerHandle;

    /** Whether a server-reflexive (srflx) ICE candidate was observed. */
    private boolean sawSrflx;

    /**
     * Creates a new client transport with the given strategy implementations.
     *
     * <p>The transport is not connected after construction. Call
     * {@link #connectWithOffer(String, SignalCallback)} to initiate the
     * WebRTC handshake.</p>
     *
     * @param tag        a log tag prefix (e.g. {@code "[WebRTC-Desktop] "})
     * @param config     the WebRTC configuration containing ICE server settings
     *                   and connection parameters
     * @param pcProvider the platform-specific peer connection provider
     * @param scheduler  the platform-specific task scheduler for ICE restart timers
     */
    public BaseWebRTCClientTransport(String tag, WebRTCConfiguration config,
                                      PeerConnectionProvider pcProvider,
                                      Scheduler scheduler) {
        this.tag = tag;
        this.config = config;
        this.pcProvider = pcProvider;
        this.scheduler = scheduler;
    }

    // --- WebRTCClientTransport ---

    /** {@inheritDoc} */
    public void connectWithOffer(String sdpOffer, final SignalCallback callback) {
        // Clean up any existing connection
        if (peerConnection != null) {
            closeCurrentConnection();
        }

        if (!pcProvider.initialize()) {
            if (listener != null) {
                listener.onError("WebRTC factory initialization failed");
            }
            return;
        }

        final DataChannelEventHandler dcHandler = createDataChannelHandler();
        PeerEventHandler pcHandler = createPeerEventHandler(dcHandler, callback);

        try {
            peerConnection = pcProvider.createPeerConnection(config, pcHandler);
        } catch (Exception e) {
            if (listener != null) {
                listener.onError("Failed to create peer connection: " + e.getMessage());
            }
            return;
        }

        if (peerConnection == null) {
            if (listener != null) {
                listener.onError("Failed to create peer connection");
            }
            return;
        }

        pcProvider.handleOffer(peerConnection, sdpOffer, new SdpResultCallback() {
            public void onSuccess(String answerSdp) {
                callback.onAnswer(answerSdp);
            }
            public void onFailure(String error) {
                if (listener != null) {
                    listener.onError("WebRTC handshake failed: " + error);
                }
            }
        });
    }

    /** {@inheritDoc} */
    public void addIceCandidate(String iceJson) {
        if (peerConnection != null) {
            pcProvider.addIceCandidate(peerConnection, iceJson);
        }
    }

    // --- ClientTransport ---

    /** {@inheritDoc} */
    public void disconnect() {
        closeCurrentConnection();
        scheduler.shutdown();
    }

    /** {@inheritDoc} */
    public boolean isConnected() {
        return connected;
    }

    /** {@inheritDoc} */
    public void sendReliable(byte[] data) {
        if (reliableChannel != null && connected) {
            pcProvider.sendData(reliableChannel, data);
        }
    }

    /** {@inheritDoc} */
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
    public void setListener(TransportListener listener) {
        this.listener = listener;
    }

    // --- ICE state machine ---

    /**
     * Handles a connection state change, implementing the full ICE restart
     * state machine.
     *
     * <p>State transitions:</p>
     * <ul>
     *   <li><b>CONNECTED</b> — resets ICE restart counters and cancels timers.
     *       If the reliable channel is closed despite ICE recovery, fires disconnect.</li>
     *   <li><b>DISCONNECTED</b> — records timestamp and schedules ICE restart
     *       after {@link WebRTCConfiguration#iceRestartDelayMs}.</li>
     *   <li><b>FAILED</b> — increments restart counter. If under the maximum,
     *       schedules restart with exponential backoff. Otherwise, fires permanent
     *       disconnect.</li>
     *   <li><b>CLOSED</b> — cancels all timers and fires permanent disconnect.</li>
     * </ul>
     *
     * @param state the new connection state (one of the {@link ConnectionState} constants)
     */
    void handleConnectionStateChanged(int state) {
        if (state == ConnectionState.CONNECTED) {
            iceClosedOrFailed = false;
            disconnectedAtMs = 0;
            iceRestartAttempts = 0;
            cancelTimers();

            if (!sawSrflx) {
                log("WARNING: No server-reflexive candidates — STUN servers may be unreachable");
            }

            if (reliableChannel != null
                    && !pcProvider.isChannelOpen(reliableChannel)) {
                connected = false;
                if (listener != null) {
                    listener.onDisconnected();
                }
            }

        } else if (state == ConnectionState.DISCONNECTED) {
            disconnectedAtMs = System.currentTimeMillis();
            final long stamp = disconnectedAtMs;

            if (disconnectedTimerHandle != null) {
                scheduler.cancel(disconnectedTimerHandle);
            }
            disconnectedTimerHandle = scheduler.schedule(new Runnable() {
                public void run() {
                    try {
                        if (disconnectedAtMs == stamp
                                && !iceClosedOrFailed
                                && peerConnection != null) {
                            pcProvider.restartIce(peerConnection);
                        }
                    } catch (Exception e) {
                        /* ICE restart failed */
                    }
                }
            }, config.iceRestartDelayMs);

        } else if (state == ConnectionState.FAILED) {
            disconnectedAtMs = 0;
            iceRestartAttempts++;
            iceClosedOrFailed = true;
            cancelTimers();

            if (iceRestartAttempts > config.maxIceRestartAttempts) {
                connected = false;
                if (listener != null) {
                    listener.onDisconnected();
                }
            } else {
                long backoffMs = (long) config.iceBackoffBaseMs
                        * (1L << (iceRestartAttempts - 1));

                failedTimerHandle = scheduler.schedule(new Runnable() {
                    public void run() {
                        try {
                            if (peerConnection != null && iceClosedOrFailed) {
                                pcProvider.restartIce(peerConnection);
                            }
                        } catch (Exception e) {
                            connected = false;
                            if (listener != null) {
                                listener.onDisconnected();
                            }
                        }
                    }
                }, backoffMs);
            }

        } else if (state == ConnectionState.CLOSED) {
            iceClosedOrFailed = true;
            disconnectedAtMs = 0;
            cancelTimers();
            connected = false;
            if (listener != null) {
                listener.onDisconnected();
            }
        }
    }

    // --- Internal helpers ---

    /**
     * Closes the current peer connection and resets all state.
     */
    private void closeCurrentConnection() {
        connected = false;
        cancelTimers();
        iceClosedOrFailed = false;
        disconnectedAtMs = 0;
        iceRestartAttempts = 0;
        sawSrflx = false;
        if (peerConnection != null) {
            try {
                pcProvider.closePeerConnection(peerConnection);
            } catch (Exception e) { /* ignore */ }
            peerConnection = null;
        }
        reliableChannel = null;
        unreliableChannel = null;
    }

    /**
     * Cancels both the disconnected and failed ICE restart timers, if any.
     */
    private void cancelTimers() {
        if (disconnectedTimerHandle != null) {
            scheduler.cancel(disconnectedTimerHandle);
            disconnectedTimerHandle = null;
        }
        if (failedTimerHandle != null) {
            scheduler.cancel(failedTimerHandle);
            failedTimerHandle = null;
        }
    }

    /**
     * Creates a {@link PeerEventHandler} that wires platform peer connection
     * events to this transport's ICE state machine and signaling callback.
     *
     * @param dcHandler the data channel event handler for incoming channels
     * @param callback  the signaling callback for ICE candidates
     * @return a new peer event handler
     */
    private PeerEventHandler createPeerEventHandler(
            final DataChannelEventHandler dcHandler,
            final SignalCallback callback) {
        return new PeerEventHandler() {
            public void onIceCandidate(String candidateJson) {
                if (candidateJson != null && candidateJson.contains("srflx")) {
                    sawSrflx = true;
                }
                callback.onIceCandidate(candidateJson);
            }

            public void onConnectionStateChanged(int state) {
                handleConnectionStateChanged(state);
            }

            public void onDataChannel(Object channel, String label) {
                if ("reliable".equals(label)) {
                    reliableChannel = channel;
                    pcProvider.setupReceivedChannel(channel, true, dcHandler);
                } else if ("unreliable".equals(label)) {
                    unreliableChannel = channel;
                    pcProvider.setupReceivedChannel(channel, false, dcHandler);
                }
            }
        };
    }

    /**
     * Creates a {@link DataChannelEventHandler} that wires data channel lifecycle
     * events to the transport's connection state and listener.
     *
     * @return a new data channel event handler
     */
    private DataChannelEventHandler createDataChannelHandler() {
        return new DataChannelEventHandler() {
            public void onReliableOpen() {
                connected = true;
                if (listener != null) {
                    listener.onConnected();
                }
            }
            public void onReliableClose() {
                connected = false;
                if (listener != null) {
                    listener.onDisconnected();
                }
            }
            public void onUnreliableOpen() {
            }
            public void onUnreliableClose() {
            }
            public void onMessage(byte[] data, boolean reliable) {
                if (listener != null) {
                    listener.onMessage(data, reliable);
                }
            }
        };
    }

    /**
     * Logs a message to standard output with the transport's tag prefix.
     *
     * @param msg the message to log
     */
    private void log(String msg) {
        Log.debug(tag + msg);
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
    TransportListener getListener() {
        return listener;
    }

    /**
     * Returns the peer connection handle. Package-private for unit testing.
     *
     * @return the opaque peer connection handle, or {@code null} if not connected
     */
    Object getPeerConnection() {
        return peerConnection;
    }

    /**
     * Returns the ICE closed/failed flag. Package-private for unit testing.
     *
     * @return {@code true} if ICE has entered CLOSED or FAILED state
     */
    boolean getIceClosedOrFailed() {
        return iceClosedOrFailed;
    }

    /**
     * Returns the disconnected-at timestamp. Package-private for unit testing.
     *
     * @return the timestamp in milliseconds, or 0 if not disconnected
     */
    long getDisconnectedAtMs() {
        return disconnectedAtMs;
    }

    /**
     * Returns the ICE restart attempt count. Package-private for unit testing.
     *
     * @return the number of ICE restart attempts since last CONNECTED state
     */
    int getIceRestartAttempts() {
        return iceRestartAttempts;
    }
}
