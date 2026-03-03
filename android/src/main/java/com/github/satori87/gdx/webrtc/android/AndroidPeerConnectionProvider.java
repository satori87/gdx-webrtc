package com.github.satori87.gdx.webrtc.android;

import android.content.Context;

import com.github.satori87.gdx.webrtc.ChannelPair;
import com.github.satori87.gdx.webrtc.ConnectionState;
import com.github.satori87.gdx.webrtc.DataChannelEventHandler;
import com.github.satori87.gdx.webrtc.PeerConnectionProvider;
import com.github.satori87.gdx.webrtc.PeerEventHandler;
import com.github.satori87.gdx.webrtc.SdpResultCallback;
import com.github.satori87.gdx.webrtc.SignalMessage;
import com.github.satori87.gdx.webrtc.WebRTCConfiguration;

import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpReceiver;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Android implementation of {@link PeerConnectionProvider} using Google's WebRTC SDK
 * ({@code org.webrtc} package).
 *
 * <p>This class manages the full lifecycle of WebRTC peer connections on Android,
 * including factory initialization, peer connection creation, SDP offer/answer
 * negotiation, ICE candidate handling, and data channel management. It delegates
 * all native WebRTC operations to the {@code org.webrtc.PeerConnectionFactory} and
 * {@code org.webrtc.PeerConnection} classes from the Google WebRTC Android SDK
 * ({@code io.github.webrtc-sdk:android}).</p>
 *
 * <h3>PeerConnectionFactory Singleton</h3>
 * <p>The native {@link PeerConnectionFactory} is created lazily as a singleton via
 * {@link #getFactory(Context)} and shared across all peer connections. Initialization
 * is synchronized to ensure thread safety. The factory is never disposed during the
 * application lifecycle -- it persists for the duration of the process.</p>
 *
 * <h3>Context Dependency</h3>
 * <p>An Android {@link Context} is required for initializing the
 * {@link PeerConnectionFactory}. The context is provided at construction time
 * by {@link AndroidWebRTCFactory} and should be the application context to
 * avoid leaking Activity references.</p>
 *
 * <h3>Data Channels</h3>
 * <p>Each peer connection creates two data channels: a reliable channel (ordered,
 * unlimited retransmits) and an unreliable channel (unordered, configurable
 * {@code maxRetransmits}). Channel events (open, close, message) are forwarded
 * to the {@link DataChannelEventHandler} provided by {@link com.github.satori87.gdx.webrtc.BaseWebRTCClient}.</p>
 *
 * <h3>SDP Observer Adapters</h3>
 * <p>The inner classes {@link CreateSdpObserver} and {@link SetSdpObserver} are
 * abstract adapters for the {@link SdpObserver} interface. They stub out the
 * unused callback methods so that callers only need to implement the relevant
 * ones (create callbacks or set callbacks, but not both).</p>
 *
 * @see PeerConnectionProvider
 * @see AndroidWebRTCFactory
 * @see com.github.satori87.gdx.webrtc.BaseWebRTCClient
 */
class AndroidPeerConnectionProvider implements PeerConnectionProvider {

    /** Log tag prefix used for all console output from this provider. */
    private static final String TAG = "[WebRTC-Android] ";

    /**
     * Singleton instance of the native {@link PeerConnectionFactory}.
     * Created lazily by {@link #getFactory(Context)} and shared across all peer connections.
     */
    private static PeerConnectionFactory factory;

    /**
     * Tracks whether {@link PeerConnectionFactory#initialize(PeerConnectionFactory.InitializationOptions)}
     * has been called. This is separate from {@link #factory} because initialization
     * and factory creation are two distinct steps in the Google WebRTC SDK.
     */
    private static boolean factoryInitialized;

    /**
     * The Android application context, used for initializing the
     * {@link PeerConnectionFactory}. Stored as the application context
     * to avoid leaking Activity references.
     */
    private final Context applicationContext;

    /**
     * Creates a new Android peer connection provider.
     *
     * @param context the Android context, used for initializing the native
     *                {@link PeerConnectionFactory}. Should be the application context
     *                to avoid leaking Activity references.
     */
    AndroidPeerConnectionProvider(Context context) {
        this.applicationContext = context;
    }

    /**
     * Returns the singleton {@link PeerConnectionFactory}, creating and initializing it
     * if necessary.
     *
     * <p>This method is synchronized to ensure thread-safe lazy initialization.
     * The initialization consists of two steps:</p>
     * <ol>
     *   <li>Calling {@link PeerConnectionFactory#initialize(PeerConnectionFactory.InitializationOptions)}
     *       (done only once, tracked by {@link #factoryInitialized})</li>
     *   <li>Creating the {@link PeerConnectionFactory} instance via its builder</li>
     * </ol>
     *
     * @param context the Android context required for SDK initialization
     * @return the shared {@link PeerConnectionFactory} instance, or {@code null} if
     *         initialization failed
     */
    private static synchronized PeerConnectionFactory getFactory(Context context) {
        if (factory == null) {
            System.out.println(TAG + "Creating PeerConnectionFactory...");
            try {
                if (!factoryInitialized) {
                    PeerConnectionFactory.InitializationOptions options =
                            PeerConnectionFactory.InitializationOptions.builder(context)
                                    .createInitializationOptions();
                    PeerConnectionFactory.initialize(options);
                    factoryInitialized = true;
                }
                factory = PeerConnectionFactory.builder().createPeerConnectionFactory();
                System.out.println(TAG + "PeerConnectionFactory created OK");
            } catch (Exception e) {
                System.err.println(TAG + "PeerConnectionFactory FAILED: " + e);
                e.printStackTrace();
            }
        }
        return factory;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Initializes the Google WebRTC SDK by calling {@link #getFactory(Context)}
     * to ensure the singleton {@link PeerConnectionFactory} is created and ready.
     * This method is idempotent -- subsequent calls return immediately if the
     * factory has already been successfully created.</p>
     *
     * @return {@code true} if the {@link PeerConnectionFactory} was successfully
     *         created, {@code false} if initialization failed
     */
    public boolean initialize() {
        return getFactory(applicationContext) != null;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Creates an {@link org.webrtc.PeerConnection} using the singleton
     * {@link PeerConnectionFactory} and the ICE server configuration from
     * the provided {@link WebRTCConfiguration}. Registers a
     * {@link PeerConnection.Observer} that forwards ICE candidate events,
     * connection state changes, and incoming data channels to the given
     * {@link PeerEventHandler}.</p>
     *
     * @param config  the WebRTC configuration containing STUN/TURN server settings
     *                and the {@code forceRelay} flag
     * @param handler callbacks for ICE candidates, connection state changes,
     *                and incoming data channels
     * @return an {@link org.webrtc.PeerConnection} instance (as an opaque {@link Object}),
     *         or {@code null} if the {@link PeerConnectionFactory} is not initialized
     */
    public Object createPeerConnection(WebRTCConfiguration config, final PeerEventHandler handler) {
        PeerConnectionFactory pcFactory = getFactory(applicationContext);
        if (pcFactory == null) {
            return null;
        }

        PeerConnection.RTCConfiguration rtcConfig = buildRtcConfig(config);

        PeerConnection.Observer observer = new PeerConnection.Observer() {
            public void onIceCandidate(IceCandidate candidate) {
                try {
                    String json = SignalMessage.buildIceCandidateJson(
                            candidate.sdp, candidate.sdpMid, candidate.sdpMLineIndex);
                    handler.onIceCandidate(json);
                } catch (Exception e) {
                    System.err.println(TAG + "Error sending ICE candidate: " + e);
                }
            }

            public void onDataChannel(DataChannel channel) {
                try {
                    String label = channel.label();
                    handler.onDataChannel(channel, label);
                } catch (Exception e) {
                    System.err.println(TAG + "Error in onDataChannel: " + e);
                }
            }

            public void onConnectionChange(PeerConnection.PeerConnectionState state) {
                try {
                    handler.onConnectionStateChanged(mapConnectionState(state));
                } catch (Exception e) {
                    System.err.println(TAG + "Error in onConnectionChange: " + e);
                }
            }

            public void onSignalingChange(PeerConnection.SignalingState state) {}
            public void onIceConnectionChange(PeerConnection.IceConnectionState state) {}
            public void onIceConnectionReceivingChange(boolean receiving) {}
            public void onIceGatheringChange(PeerConnection.IceGatheringState state) {}
            public void onIceCandidatesRemoved(IceCandidate[] candidates) {}
            public void onAddStream(MediaStream stream) {}
            public void onRemoveStream(MediaStream stream) {}
            public void onRenegotiationNeeded() {}
            public void onAddTrack(RtpReceiver receiver, MediaStream[] streams) {}
        };

        return pcFactory.createPeerConnection(rtcConfig, observer);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Creates an SDP offer on the given {@link PeerConnection}, then sets the
     * resulting {@link SessionDescription} as the local description. Uses
     * {@link CreateSdpObserver} for the offer creation callback and
     * {@link SetSdpObserver} for the local description callback. The SDP string
     * is delivered to the callback on the WebRTC signaling thread.</p>
     *
     * @param peerConnection the {@link PeerConnection} handle (cast internally)
     * @param callback       callback to receive the offer SDP string on success,
     *                       or an error message on failure
     */
    public void createOffer(Object peerConnection, final SdpResultCallback callback) {
        final PeerConnection pc = (PeerConnection) peerConnection;
        MediaConstraints constraints = new MediaConstraints();
        pc.createOffer(new CreateSdpObserver() {
            public void onCreateSuccess(SessionDescription sdp) {
                try {
                    pc.setLocalDescription(new SetSdpObserver() {
                        public void onSetSuccess() {
                            callback.onSuccess(sdp.description);
                        }
                        public void onSetFailure(String error) {
                            callback.onFailure("Set local description failed: " + error);
                        }
                    }, sdp);
                } catch (Exception e) {
                    callback.onFailure("Error setting local description: " + e);
                }
            }
            public void onCreateFailure(String error) {
                callback.onFailure(error);
            }
        }, constraints);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Processes a received SDP offer by performing three steps in sequence:</p>
     * <ol>
     *   <li>Sets the remote SDP offer as the remote description on the
     *       {@link PeerConnection}</li>
     *   <li>Creates an SDP answer using the peer connection's
     *       {@link PeerConnection#createAnswer(SdpObserver, MediaConstraints)} method</li>
     *   <li>Sets the generated answer as the local description</li>
     * </ol>
     * <p>If any step fails, the error is reported through
     * {@link SdpResultCallback#onFailure(String)}.</p>
     *
     * @param peerConnection the {@link PeerConnection} handle (cast internally)
     * @param remoteSdp      the remote SDP offer string received from the signaling server
     * @param callback       callback to receive the answer SDP string on success,
     *                       or an error message on failure
     */
    public void handleOffer(Object peerConnection, String remoteSdp, final SdpResultCallback callback) {
        final PeerConnection pc = (PeerConnection) peerConnection;
        SessionDescription offer = new SessionDescription(SessionDescription.Type.OFFER, remoteSdp);
        pc.setRemoteDescription(new SetSdpObserver() {
            public void onSetSuccess() {
                try {
                    MediaConstraints answerConstraints = new MediaConstraints();
                    pc.createAnswer(new CreateSdpObserver() {
                        public void onCreateSuccess(SessionDescription sdp) {
                            try {
                                pc.setLocalDescription(new SetSdpObserver() {
                                    public void onSetSuccess() {
                                        callback.onSuccess(sdp.description);
                                    }
                                    public void onSetFailure(String error) {
                                        callback.onFailure("Set local description failed: " + error);
                                    }
                                }, sdp);
                            } catch (Exception e) {
                                callback.onFailure("Error setting local description: " + e);
                            }
                        }
                        public void onCreateFailure(String error) {
                            callback.onFailure("Create answer failed: " + error);
                        }
                    }, answerConstraints);
                } catch (Exception e) {
                    callback.onFailure("Error creating answer: " + e);
                }
            }
            public void onSetFailure(String error) {
                callback.onFailure("Set remote description failed: " + error);
            }
        }, offer);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Sets the received SDP answer as the remote description on the
     * {@link PeerConnection}. This is called on the offerer side after the
     * answerer has responded with its SDP. Errors are logged to stderr but
     * not propagated, as the connection may still recover via ICE restart.</p>
     *
     * @param peerConnection the {@link PeerConnection} handle (cast internally)
     * @param sdp            the remote SDP answer string
     */
    public void setRemoteAnswer(Object peerConnection, String sdp) {
        PeerConnection pc = (PeerConnection) peerConnection;
        SessionDescription answer = new SessionDescription(SessionDescription.Type.ANSWER, sdp);
        pc.setRemoteDescription(new SetSdpObserver() {
            public void onSetSuccess() {
                // OK
            }
            public void onSetFailure(String error) {
                System.err.println(TAG + "Set remote answer failed: " + error);
            }
        }, answer);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Parses the JSON-encoded ICE candidate using
     * {@link SignalMessage#extractString(String, String)} and
     * {@link SignalMessage#extractInt(String, String)}, then adds it to the
     * {@link PeerConnection}. Errors are caught and logged to stderr.</p>
     *
     * @param peerConnection the {@link PeerConnection} handle (cast internally)
     * @param candidateJson  JSON string containing {@code candidate}, {@code sdpMid},
     *                       and {@code sdpMLineIndex} fields
     */
    public void addIceCandidate(Object peerConnection, String candidateJson) {
        PeerConnection pc = (PeerConnection) peerConnection;
        String candidate = SignalMessage.extractString(candidateJson, "candidate");
        String sdpMid = SignalMessage.extractString(candidateJson, "sdpMid");
        int sdpMLineIndex = SignalMessage.extractInt(candidateJson, "sdpMLineIndex");
        try {
            pc.addIceCandidate(new IceCandidate(sdpMid, sdpMLineIndex, candidate));
        } catch (Exception e) {
            System.err.println(TAG + "addIceCandidate failed: " + e);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Triggers an ICE restart by calling {@link PeerConnection#restartIce()}.
     * This causes the peer connection to gather new ICE candidates and
     * renegotiate the connection without creating a new peer connection.</p>
     *
     * @param peerConnection the {@link PeerConnection} handle (cast internally)
     */
    public void restartIce(Object peerConnection) {
        PeerConnection pc = (PeerConnection) peerConnection;
        pc.restartIce();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Closes the {@link PeerConnection}, releasing its associated native
     * resources. After this call, the peer connection handle should not be
     * used again.</p>
     *
     * @param peerConnection the {@link PeerConnection} handle (cast internally)
     */
    public void closePeerConnection(Object peerConnection) {
        PeerConnection pc = (PeerConnection) peerConnection;
        pc.close();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Creates two {@link DataChannel} instances on the given {@link PeerConnection}:</p>
     * <ul>
     *   <li><b>"reliable"</b> -- configured with {@code ordered=true} and default
     *       (unlimited) retransmits for guaranteed, in-order delivery</li>
     *   <li><b>"unreliable"</b> -- configured with {@code ordered=false} and the
     *       specified {@code maxRetransmits} value for fire-and-forget delivery</li>
     * </ul>
     * <p>Both channels are registered with a {@link DataChannel.Observer} that
     * forwards open, close, and message events to the provided
     * {@link DataChannelEventHandler}.</p>
     *
     * @param peerConnection         the {@link PeerConnection} handle (cast internally)
     * @param unreliableMaxRetransmits the maximum number of retransmission attempts for
     *                                 the unreliable channel (typically 0 for fire-and-forget)
     * @param handler                 callbacks for channel open/close/message events
     * @return a {@link ChannelPair} containing the reliable and unreliable
     *         {@link DataChannel} handles
     */
    public ChannelPair createDataChannels(Object peerConnection, int unreliableMaxRetransmits,
                                           DataChannelEventHandler handler) {
        PeerConnection pc = (PeerConnection) peerConnection;

        DataChannel.Init reliableInit = new DataChannel.Init();
        reliableInit.ordered = true;
        DataChannel reliableChannel = pc.createDataChannel("reliable", reliableInit);
        setupChannelObserver(reliableChannel, true, handler);

        DataChannel.Init unreliableInit = new DataChannel.Init();
        unreliableInit.ordered = false;
        unreliableInit.maxRetransmits = unreliableMaxRetransmits;
        DataChannel unreliableChannel = pc.createDataChannel("unreliable", unreliableInit);
        setupChannelObserver(unreliableChannel, false, handler);

        return new ChannelPair(reliableChannel, unreliableChannel);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Registers a {@link DataChannel.Observer} on a data channel received from
     * the remote peer (via the {@code ondatachannel} event). The observer forwards
     * open, close, and message events to the appropriate reliable or unreliable
     * methods on the {@link DataChannelEventHandler} based on the {@code reliable}
     * flag.</p>
     *
     * @param channel  the {@link DataChannel} handle received from the remote peer
     *                 (cast internally)
     * @param reliable {@code true} if this is the reliable channel,
     *                 {@code false} if unreliable
     * @param handler  callbacks for channel open/close/message events
     */
    public void setupReceivedChannel(Object channel, boolean reliable, DataChannelEventHandler handler) {
        DataChannel dc = (DataChannel) channel;
        setupChannelObserver(dc, reliable, handler);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Wraps the byte array in a {@link ByteBuffer} and sends it as a binary
     * message on the {@link DataChannel}. Exceptions are silently caught because
     * the channel may be in the process of closing.</p>
     *
     * @param channel the {@link DataChannel} handle (cast internally)
     * @param data    the raw bytes to send
     */
    public void sendData(Object channel, byte[] data) {
        DataChannel dc = (DataChannel) channel;
        try {
            ByteBuffer buf = ByteBuffer.wrap(data);
            dc.send(new DataChannel.Buffer(buf, true));
        } catch (Exception e) {
            // Channel may be closing
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Returns the number of bytes currently queued for sending on the
     * {@link DataChannel}. This is checked by the unreliable send logic
     * to avoid exceeding the buffer limit (typically 64KB).</p>
     *
     * @param channel the {@link DataChannel} handle (cast internally)
     * @return the number of buffered bytes
     */
    public long getBufferedAmount(Object channel) {
        DataChannel dc = (DataChannel) channel;
        return dc.bufferedAmount();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Checks whether the {@link DataChannel} is in the
     * {@link DataChannel.State#OPEN} state.</p>
     *
     * @param channel the {@link DataChannel} handle (cast internally)
     * @return {@code true} if the channel state is {@link DataChannel.State#OPEN}
     */
    public boolean isChannelOpen(Object channel) {
        DataChannel dc = (DataChannel) channel;
        return dc.state() == DataChannel.State.OPEN;
    }

    // --- Private helpers ---

    /**
     * Registers a {@link DataChannel.Observer} on the given data channel to forward
     * open, close, and message events to the {@link DataChannelEventHandler}.
     *
     * <p>The observer dispatches to the appropriate reliable or unreliable methods
     * on the handler depending on the {@code reliable} flag. Incoming binary messages
     * are extracted from the {@link DataChannel.Buffer} into a byte array before
     * being passed to the handler.</p>
     *
     * @param channel  the data channel to observe
     * @param reliable {@code true} to forward events to the reliable handler methods
     *                 ({@link DataChannelEventHandler#onReliableOpen()},
     *                 {@link DataChannelEventHandler#onReliableClose()}),
     *                 {@code false} to forward to the unreliable methods
     * @param handler  the event handler to receive channel events
     */
    private void setupChannelObserver(final DataChannel channel, final boolean reliable,
                                       final DataChannelEventHandler handler) {
        channel.registerObserver(new DataChannel.Observer() {
            public void onBufferedAmountChange(long previousAmount) {}

            public void onStateChange() {
                try {
                    DataChannel.State state = channel.state();
                    if (state == DataChannel.State.OPEN) {
                        if (reliable) {
                            handler.onReliableOpen();
                        } else {
                            handler.onUnreliableOpen();
                        }
                    } else if (state == DataChannel.State.CLOSED) {
                        if (reliable) {
                            handler.onReliableClose();
                        } else {
                            handler.onUnreliableClose();
                        }
                    }
                } catch (Exception e) {
                    System.err.println(TAG + "Error in channel onStateChange: " + e);
                }
            }

            public void onMessage(DataChannel.Buffer buffer) {
                try {
                    ByteBuffer bb = buffer.data;
                    byte[] data = new byte[bb.remaining()];
                    bb.get(data);
                    handler.onMessage(data, reliable);
                } catch (Exception e) {
                    System.err.println(TAG + "Error in channel onMessage: " + e);
                }
            }
        });
    }

    /**
     * Maps a Google WebRTC SDK {@link PeerConnection.PeerConnectionState} enum value
     * to the platform-agnostic {@link ConnectionState} integer constant.
     *
     * <p>Unknown or unmapped states default to {@link ConnectionState#NEW}.</p>
     *
     * @param state the Android SDK peer connection state
     * @return the corresponding {@link ConnectionState} constant
     * @see ConnectionState
     */
    private static int mapConnectionState(PeerConnection.PeerConnectionState state) {
        if (state == PeerConnection.PeerConnectionState.NEW) {
            return ConnectionState.NEW;
        } else if (state == PeerConnection.PeerConnectionState.CONNECTING) {
            return ConnectionState.CONNECTING;
        } else if (state == PeerConnection.PeerConnectionState.CONNECTED) {
            return ConnectionState.CONNECTED;
        } else if (state == PeerConnection.PeerConnectionState.DISCONNECTED) {
            return ConnectionState.DISCONNECTED;
        } else if (state == PeerConnection.PeerConnectionState.FAILED) {
            return ConnectionState.FAILED;
        } else if (state == PeerConnection.PeerConnectionState.CLOSED) {
            return ConnectionState.CLOSED;
        }
        return ConnectionState.NEW;
    }

    /**
     * Builds an {@link PeerConnection.RTCConfiguration} from the platform-agnostic
     * {@link WebRTCConfiguration}.
     *
     * <p>Configures the STUN server, optional TURN server (with credentials), and
     * the {@code iceTransportsType} (set to {@link PeerConnection.IceTransportsType#RELAY}
     * if {@link WebRTCConfiguration#forceRelay} is {@code true}).</p>
     *
     * @param config the platform-agnostic WebRTC configuration
     * @return the Android SDK RTCConfiguration ready for peer connection creation
     */
    private static PeerConnection.RTCConfiguration buildRtcConfig(WebRTCConfiguration config) {
        List<PeerConnection.IceServer> iceServers = new ArrayList<PeerConnection.IceServer>();

        String[] stunUrls = config.stunServers != null ? config.stunServers : new String[] { config.stunServer };
        for (int i = 0; i < stunUrls.length; i++) {
            iceServers.add(PeerConnection.IceServer.builder(stunUrls[i]).createIceServer());
        }

        if (config.turnServer != null && !config.turnServer.isEmpty()) {
            PeerConnection.IceServer.Builder turnBuilder =
                    PeerConnection.IceServer.builder(config.turnServer);
            if (config.turnUsername != null) {
                turnBuilder.setUsername(config.turnUsername);
            }
            if (config.turnPassword != null) {
                turnBuilder.setPassword(config.turnPassword);
            }
            iceServers.add(turnBuilder.createIceServer());
        }

        PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(iceServers);

        if (config.forceRelay) {
            rtcConfig.iceTransportsType = PeerConnection.IceTransportsType.RELAY;
        }

        return rtcConfig;
    }

    // --- SDP observer adapters ---

    /**
     * Abstract adapter for {@link SdpObserver} used with
     * {@link PeerConnection#createOffer(SdpObserver, MediaConstraints)} and
     * {@link PeerConnection#createAnswer(SdpObserver, MediaConstraints)}.
     *
     * <p>Provides no-op implementations of {@link #onSetSuccess()} and
     * {@link #onSetFailure(String)} since these methods are only relevant when
     * setting a local/remote description, not when creating an offer or answer.
     * Subclasses must implement {@link #onCreateSuccess(SessionDescription)} and
     * {@link #onCreateFailure(String)}.</p>
     */
    private static abstract class CreateSdpObserver implements SdpObserver {
        /** No-op -- not applicable during offer/answer creation. */
        public void onSetSuccess() {}

        /** No-op -- not applicable during offer/answer creation. */
        public void onSetFailure(String error) {}
    }

    /**
     * Abstract adapter for {@link SdpObserver} used with
     * {@link PeerConnection#setLocalDescription(SdpObserver, SessionDescription)} and
     * {@link PeerConnection#setRemoteDescription(SdpObserver, SessionDescription)}.
     *
     * <p>Provides no-op implementations of {@link #onCreateSuccess(SessionDescription)}
     * and {@link #onCreateFailure(String)} since these methods are only relevant when
     * creating an offer or answer, not when setting a description. Subclasses must
     * implement {@link #onSetSuccess()} and {@link #onSetFailure(String)}.</p>
     */
    private static abstract class SetSdpObserver implements SdpObserver {
        /** No-op -- not applicable during description setting. */
        public void onCreateSuccess(SessionDescription sdp) {}

        /** No-op -- not applicable during description setting. */
        public void onCreateFailure(String error) {}
    }
}
