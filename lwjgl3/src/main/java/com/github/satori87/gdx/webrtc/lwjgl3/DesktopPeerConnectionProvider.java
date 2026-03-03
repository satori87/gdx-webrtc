package com.github.satori87.gdx.webrtc.lwjgl3;

import com.github.satori87.gdx.webrtc.*;
import dev.onvoid.webrtc.*;
import dev.onvoid.webrtc.media.audio.AudioDeviceModule;
import dev.onvoid.webrtc.media.audio.AudioLayer;

import java.nio.ByteBuffer;

/**
 * Desktop/JVM implementation of {@link PeerConnectionProvider} using the
 * {@code dev.onvoid.webrtc} (webrtc-java) native library.
 *
 * <p>This provider wraps the webrtc-java API to manage native RTCPeerConnection
 * instances on the desktop platform. It uses a singleton
 * {@link dev.onvoid.webrtc.PeerConnectionFactory} with synchronized lazy
 * initialization, meaning the native WebRTC runtime is loaded only once and
 * shared across all peer connections.</p>
 *
 * <h3>Key implementation details:</h3>
 * <ul>
 *   <li>The underlying {@link dev.onvoid.webrtc.PeerConnectionFactory} is created
 *       lazily on first use and reused for all subsequent peer connections.</li>
 *   <li>ICE server configuration (STUN/TURN) is translated from
 *       {@link WebRTCConfiguration} to webrtc-java's {@link dev.onvoid.webrtc.RTCConfiguration}.</li>
 *   <li>Connection state changes from {@link dev.onvoid.webrtc.RTCPeerConnectionState}
 *       are mapped to the platform-agnostic {@link ConnectionState} constants.</li>
 *   <li>Data channels are created and observed via
 *       {@link dev.onvoid.webrtc.RTCDataChannelObserver} callbacks that delegate
 *       to the provided {@link DataChannelEventHandler}.</li>
 *   <li>All data is sent as binary ({@code RTCDataChannelBuffer} with
 *       {@code binary=true}).</li>
 * </ul>
 *
 * <p>This class is package-private and is instantiated by
 * {@link DesktopWebRTCFactory} when creating a new {@link BaseWebRTCClient}.</p>
 *
 * @see PeerConnectionProvider
 * @see DesktopWebRTCFactory
 * @see BaseWebRTCClient
 */
class DesktopPeerConnectionProvider implements PeerConnectionProvider {

    /** Log tag prefix used for all diagnostic messages from this provider. */
    private static final String TAG = "[WebRTC-Desktop] ";

    /**
     * Whether to use a dummy audio device module for headless server environments.
     * Must be set before the first call to {@link #getFactory()}.
     */
    private static boolean headless;

    /**
     * Configures headless mode for server environments without audio hardware.
     *
     * <p>When {@code true}, the {@link PeerConnectionFactory} is created with a
     * dummy {@link AudioDeviceModule} ({@link AudioLayer#kDummyAudio}), preventing
     * failures on machines without audio hardware (e.g., dedicated game servers).</p>
     *
     * <p>Must be called before any WebRTC operations (before the factory is
     * lazily initialized).</p>
     *
     * @param h {@code true} for headless/server mode, {@code false} for desktop
     */
    public static void setHeadless(boolean h) {
        headless = h;
    }

    /**
     * Singleton native peer connection factory instance, lazily initialized.
     * Shared across all {@code DesktopPeerConnectionProvider} instances.
     */
    private static PeerConnectionFactory factory;

    /**
     * Returns the singleton {@link PeerConnectionFactory}, creating it on first call.
     *
     * <p>This method is synchronized to prevent multiple threads from creating
     * duplicate factories. If {@link #headless} is {@code true}, the factory is
     * created with a dummy audio device module for server environments. If factory
     * creation fails, the error is logged and {@code null} is returned.</p>
     *
     * @return the shared {@link PeerConnectionFactory} instance, or {@code null}
     *         if initialization failed
     */
    private static synchronized PeerConnectionFactory getFactory() {
        if (factory == null) {
            try {
                if (headless) {
                    factory = new PeerConnectionFactory(
                            new AudioDeviceModule(AudioLayer.kDummyAudio));
                } else {
                    factory = new PeerConnectionFactory();
                }
            } catch (Exception e) {
                System.err.println(TAG + "PeerConnectionFactory FAILED: " + e);
            }
        }
        return factory;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Triggers lazy initialization of the singleton
     * {@link dev.onvoid.webrtc.PeerConnectionFactory}. Subsequent calls are
     * no-ops that return the cached result.</p>
     *
     * @return {@code true} if the native PeerConnectionFactory was successfully
     *         created (or was already initialized), {@code false} if creation failed
     */
    public boolean initialize() {
        return getFactory() != null;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Creates a native {@link dev.onvoid.webrtc.RTCPeerConnection} using the
     * webrtc-java library. The ICE server configuration (STUN and optional TURN)
     * is built from the provided {@link WebRTCConfiguration}. A
     * {@link dev.onvoid.webrtc.PeerConnectionObserver} is registered to forward
     * ICE candidate events, data channel events, and connection state changes
     * to the given {@link PeerEventHandler}.</p>
     *
     * @param config  the WebRTC configuration containing ICE server settings,
     *                TURN credentials, and relay policy
     * @param handler callbacks for ICE candidates, connection state changes,
     *                and incoming data channels
     * @return an {@link dev.onvoid.webrtc.RTCPeerConnection} instance (as an
     *         opaque {@link Object}), or {@code null} if the factory is not
     *         initialized or creation fails
     */
    public Object createPeerConnection(WebRTCConfiguration config, final PeerEventHandler handler) {
        PeerConnectionFactory pcFactory = getFactory();
        if (pcFactory == null) {
            return null;
        }

        RTCConfiguration rtcConfig = buildRtcConfig(config);

        PeerConnectionObserver observer = new PeerConnectionObserver() {
            public void onIceCandidate(RTCIceCandidate candidate) {
                try {
                    String json = SignalMessage.buildIceCandidateJson(
                            candidate.sdp, candidate.sdpMid, candidate.sdpMLineIndex);
                    handler.onIceCandidate(json);
                } catch (Exception e) {
                    System.err.println(TAG + "Error sending ICE candidate: " + e);
                }
            }

            public void onDataChannel(RTCDataChannel channel) {
                try {
                    String label = channel.getLabel();
                    handler.onDataChannel(channel, label);
                } catch (Exception e) {
                    System.err.println(TAG + "Error in onDataChannel: " + e);
                }
            }

            public void onConnectionChange(RTCPeerConnectionState state) {
                try {
                    handler.onConnectionStateChanged(mapConnectionState(state));
                } catch (Exception e) {
                    System.err.println(TAG + "Error in onConnectionChange: " + e);
                }
            }
        };

        try {
            return pcFactory.createPeerConnection(rtcConfig, observer);
        } catch (Exception e) {
            System.err.println(TAG + "createPeerConnection FAILED: " + e);
            return null;
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Creates an SDP offer on the native {@link dev.onvoid.webrtc.RTCPeerConnection},
     * sets it as the local description, and delivers the resulting SDP string
     * to the callback. Uses default {@link dev.onvoid.webrtc.RTCOfferOptions}.</p>
     *
     * @param peerConnection the {@link dev.onvoid.webrtc.RTCPeerConnection} handle
     * @param callback       callback to receive the offer SDP string on success,
     *                       or an error message on failure
     */
    public void createOffer(Object peerConnection, final SdpResultCallback callback) {
        final RTCPeerConnection pc = (RTCPeerConnection) peerConnection;
        pc.createOffer(new RTCOfferOptions(), new CreateSessionDescriptionObserver() {
            public void onSuccess(final RTCSessionDescription description) {
                try {
                    pc.setLocalDescription(description, new SetSessionDescriptionObserver() {
                        public void onSuccess() {
                            callback.onSuccess(description.sdp);
                        }
                        public void onFailure(String error) {
                            callback.onFailure("Set local desc failed: " + error);
                        }
                    });
                } catch (Exception e) {
                    callback.onFailure("Error setting local description: " + e.getMessage());
                }
            }
            public void onFailure(String error) {
                callback.onFailure("Create offer failed: " + error);
            }
        });
    }

    /**
     * {@inheritDoc}
     *
     * <p>Sets the remote SDP offer on the native peer connection, generates an
     * SDP answer using default {@link dev.onvoid.webrtc.RTCAnswerOptions}, sets
     * the answer as the local description, and delivers the answer SDP string
     * to the callback.</p>
     *
     * @param peerConnection the {@link dev.onvoid.webrtc.RTCPeerConnection} handle
     * @param remoteSdp      the remote SDP offer string received from the signaling server
     * @param callback       callback to receive the answer SDP string on success,
     *                       or an error message on failure at any stage
     */
    public void handleOffer(Object peerConnection, String remoteSdp, final SdpResultCallback callback) {
        final RTCPeerConnection pc = (RTCPeerConnection) peerConnection;
        RTCSessionDescription offer = new RTCSessionDescription(RTCSdpType.OFFER, remoteSdp);

        pc.setRemoteDescription(offer, new SetSessionDescriptionObserver() {
            public void onSuccess() {
                try {
                    pc.createAnswer(new RTCAnswerOptions(), new CreateSessionDescriptionObserver() {
                        public void onSuccess(final RTCSessionDescription description) {
                            try {
                                pc.setLocalDescription(description, new SetSessionDescriptionObserver() {
                                    public void onSuccess() {
                                        callback.onSuccess(description.sdp);
                                    }
                                    public void onFailure(String error) {
                                        callback.onFailure("Set local desc failed: " + error);
                                    }
                                });
                            } catch (Exception e) {
                                callback.onFailure("Error setting local description: " + e.getMessage());
                            }
                        }
                        public void onFailure(String error) {
                            callback.onFailure("Create answer failed: " + error);
                        }
                    });
                } catch (Exception e) {
                    callback.onFailure("Error creating answer: " + e.getMessage());
                }
            }
            public void onFailure(String error) {
                callback.onFailure("Set remote desc failed: " + error);
            }
        });
    }

    /**
     * {@inheritDoc}
     *
     * <p>Sets the remote SDP answer on the native peer connection. This is called
     * on the offerer side after receiving the answerer's SDP response via signaling.
     * Errors during the set operation are logged to {@code System.err}.</p>
     *
     * @param peerConnection the {@link dev.onvoid.webrtc.RTCPeerConnection} handle
     * @param sdp            the remote SDP answer string
     */
    public void setRemoteAnswer(Object peerConnection, String sdp) {
        RTCPeerConnection pc = (RTCPeerConnection) peerConnection;
        RTCSessionDescription answer = new RTCSessionDescription(RTCSdpType.ANSWER, sdp);
        pc.setRemoteDescription(answer, new SetSessionDescriptionObserver() {
            public void onSuccess() {
                // OK
            }
            public void onFailure(String error) {
                System.err.println(TAG + "Set remote desc (answer) failed: " + error);
            }
        });
    }

    /**
     * {@inheritDoc}
     *
     * <p>Parses the ICE candidate JSON string using {@link SignalMessage} helper
     * methods to extract the {@code candidate}, {@code sdpMid}, and
     * {@code sdpMLineIndex} fields, then adds the resulting
     * {@link dev.onvoid.webrtc.RTCIceCandidate} to the native peer connection.
     * Errors are logged to {@code System.err} and silently swallowed.</p>
     *
     * @param peerConnection the {@link dev.onvoid.webrtc.RTCPeerConnection} handle
     * @param candidateJson  the JSON-encoded ICE candidate string
     */
    public void addIceCandidate(Object peerConnection, String candidateJson) {
        RTCPeerConnection pc = (RTCPeerConnection) peerConnection;
        String candidate = SignalMessage.extractString(candidateJson, "candidate");
        String sdpMid = SignalMessage.extractString(candidateJson, "sdpMid");
        int sdpMLineIndex = SignalMessage.extractInt(candidateJson, "sdpMLineIndex");

        try {
            pc.addIceCandidate(new RTCIceCandidate(sdpMid, sdpMLineIndex, candidate));
        } catch (Exception e) {
            System.err.println(TAG + "addIceCandidate failed: " + e);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Calls {@link dev.onvoid.webrtc.RTCPeerConnection#restartIce()} on the
     * native peer connection to trigger an ICE restart. This causes the next
     * {@code createOffer} call to generate a new set of ICE candidates.</p>
     *
     * @param peerConnection the {@link dev.onvoid.webrtc.RTCPeerConnection} handle
     */
    public void restartIce(Object peerConnection) {
        RTCPeerConnection pc = (RTCPeerConnection) peerConnection;
        pc.restartIce();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Closes the native {@link dev.onvoid.webrtc.RTCPeerConnection}, releasing
     * all associated native resources including data channels and ICE agents.</p>
     *
     * @param peerConnection the {@link dev.onvoid.webrtc.RTCPeerConnection} handle
     */
    public void closePeerConnection(Object peerConnection) {
        RTCPeerConnection pc = (RTCPeerConnection) peerConnection;
        pc.close();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Creates two {@link dev.onvoid.webrtc.RTCDataChannel} instances on the
     * native peer connection:</p>
     * <ul>
     *   <li><b>"reliable"</b> -- configured with {@code ordered=true} and default
     *       (unlimited) retransmits for guaranteed, in-order delivery.</li>
     *   <li><b>"unreliable"</b> -- configured with {@code ordered=false} and the
     *       specified {@code unreliableMaxRetransmits} value (typically 0 for
     *       fire-and-forget semantics).</li>
     * </ul>
     *
     * <p>An {@link dev.onvoid.webrtc.RTCDataChannelObserver} is registered on each
     * channel to forward open, close, and message events to the provided handler.</p>
     *
     * @param peerConnection         the {@link dev.onvoid.webrtc.RTCPeerConnection} handle
     * @param unreliableMaxRetransmits the maximum number of retransmission attempts for
     *                                 the unreliable channel (0 for fire-and-forget)
     * @param handler                 callbacks for channel open, close, and message events
     * @return a {@link ChannelPair} containing the reliable and unreliable
     *         {@link dev.onvoid.webrtc.RTCDataChannel} handles
     */
    public ChannelPair createDataChannels(Object peerConnection, int unreliableMaxRetransmits,
                                           DataChannelEventHandler handler) {
        RTCPeerConnection pc = (RTCPeerConnection) peerConnection;

        RTCDataChannelInit reliableInit = new RTCDataChannelInit();
        reliableInit.ordered = true;
        RTCDataChannel reliableChannel = pc.createDataChannel("reliable", reliableInit);
        registerChannelObserver(reliableChannel, true, handler);

        RTCDataChannelInit unreliableInit = new RTCDataChannelInit();
        unreliableInit.ordered = false;
        unreliableInit.maxRetransmits = unreliableMaxRetransmits;
        RTCDataChannel unreliableChannel = pc.createDataChannel("unreliable", unreliableInit);
        registerChannelObserver(unreliableChannel, false, handler);

        return new ChannelPair(reliableChannel, unreliableChannel);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Registers an {@link dev.onvoid.webrtc.RTCDataChannelObserver} on a data
     * channel received from the remote peer (via the
     * {@link dev.onvoid.webrtc.PeerConnectionObserver#onDataChannel} callback).
     * The observer forwards open, close, and message events to the appropriate
     * reliable or unreliable methods on the handler.</p>
     *
     * @param channel  the {@link dev.onvoid.webrtc.RTCDataChannel} handle received
     *                 from the remote peer
     * @param reliable {@code true} if this is the reliable channel, {@code false}
     *                 if unreliable
     * @param handler  callbacks for channel open, close, and message events
     */
    public void setupReceivedChannel(Object channel, boolean reliable,
                                      DataChannelEventHandler handler) {
        RTCDataChannel dc = (RTCDataChannel) channel;
        registerChannelObserver(dc, reliable, handler);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Wraps the byte array in a {@link java.nio.ByteBuffer} and sends it as a
     * binary {@link dev.onvoid.webrtc.RTCDataChannelBuffer} on the native data
     * channel. Exceptions (e.g., if the channel is closing) are silently caught.</p>
     *
     * @param channel the {@link dev.onvoid.webrtc.RTCDataChannel} handle
     * @param data    the raw bytes to send
     */
    public void sendData(Object channel, byte[] data) {
        RTCDataChannel dc = (RTCDataChannel) channel;
        try {
            ByteBuffer buf = ByteBuffer.wrap(data);
            dc.send(new RTCDataChannelBuffer(buf, true));
        } catch (Exception e) {
            // Channel may be closing
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Queries the native {@link dev.onvoid.webrtc.RTCDataChannel} for its
     * current buffered amount. This value is checked before sending unreliable
     * packets to avoid exceeding the configured buffer limit.</p>
     *
     * @param channel the {@link dev.onvoid.webrtc.RTCDataChannel} handle
     * @return the number of bytes currently queued for sending
     */
    public long getBufferedAmount(Object channel) {
        RTCDataChannel dc = (RTCDataChannel) channel;
        return dc.getBufferedAmount();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Checks whether the native {@link dev.onvoid.webrtc.RTCDataChannel} is
     * currently in the {@link dev.onvoid.webrtc.RTCDataChannelState#OPEN} state.
     * This is used by {@link BaseWebRTCClient} to determine whether a channel is
     * ready to send data.</p>
     *
     * @param channel the {@link dev.onvoid.webrtc.RTCDataChannel} handle
     * @return {@code true} if the channel state is OPEN, {@code false} otherwise
     */
    public boolean isChannelOpen(Object channel) {
        RTCDataChannel dc = (RTCDataChannel) channel;
        return dc.getState() == RTCDataChannelState.OPEN;
    }

    // --- Private helpers ---

    /**
     * Translates the platform-agnostic {@link WebRTCConfiguration} into a
     * webrtc-java {@link dev.onvoid.webrtc.RTCConfiguration}.
     *
     * <p>Configures the following ICE servers:</p>
     * <ul>
     *   <li>STUN servers (always present, from {@link WebRTCConfiguration#stunServers}).</li>
     *   <li>An optional TURN server (from {@link WebRTCConfiguration#turnServer}),
     *       with username and password credentials if provided.</li>
     * </ul>
     *
     * <p>If {@link WebRTCConfiguration#forceRelay} is {@code true}, the ICE
     * transport policy is set to {@link dev.onvoid.webrtc.RTCIceTransportPolicy#RELAY},
     * forcing all traffic through a TURN server.</p>
     *
     * @param config the platform-agnostic WebRTC configuration
     * @return a fully configured {@link dev.onvoid.webrtc.RTCConfiguration} ready
     *         for use with the native peer connection factory
     */
    private static RTCConfiguration buildRtcConfig(WebRTCConfiguration config) {
        RTCConfiguration rtcConfig = new RTCConfiguration();

        RTCIceServer stunServer = new RTCIceServer();
        String[] stunUrls = config.stunServers != null ? config.stunServers : new String[] { config.stunServer };
        for (int i = 0; i < stunUrls.length; i++) {
            stunServer.urls.add(stunUrls[i]);
        }
        rtcConfig.iceServers.add(stunServer);

        if (config.turnServer != null && !config.turnServer.isEmpty()) {
            RTCIceServer turnServer = new RTCIceServer();
            turnServer.urls.add(config.turnServer);
            turnServer.username = config.turnUsername != null ? config.turnUsername : "";
            turnServer.password = config.turnPassword != null ? config.turnPassword : "";
            rtcConfig.iceServers.add(turnServer);
        }

        if (config.forceRelay) {
            rtcConfig.iceTransportPolicy = RTCIceTransportPolicy.RELAY;
        }

        return rtcConfig;
    }

    /**
     * Registers an {@link dev.onvoid.webrtc.RTCDataChannelObserver} on the given
     * native data channel to forward state changes and incoming messages to the
     * provided {@link DataChannelEventHandler}.
     *
     * <p>The observer handles three events:</p>
     * <ul>
     *   <li><b>onStateChange</b> -- dispatches to the handler's reliable or unreliable
     *       open/close methods based on the {@code reliable} flag and the channel's
     *       current {@link dev.onvoid.webrtc.RTCDataChannelState}.</li>
     *   <li><b>onMessage</b> -- extracts raw bytes from the
     *       {@link dev.onvoid.webrtc.RTCDataChannelBuffer} and delivers them to the
     *       handler's {@link DataChannelEventHandler#onMessage(byte[], boolean)} method.</li>
     *   <li><b>onBufferedAmountChange</b> -- currently a no-op.</li>
     * </ul>
     *
     * <p>All callback methods are wrapped in try-catch blocks to prevent native
     * exceptions from propagating into the WebRTC stack.</p>
     *
     * @param channel  the native {@link dev.onvoid.webrtc.RTCDataChannel} to observe
     * @param reliable {@code true} if this channel is the reliable (ordered) channel,
     *                 {@code false} if it is the unreliable channel
     * @param handler  the event handler to receive open, close, and message callbacks
     */
    private static void registerChannelObserver(final RTCDataChannel channel,
                                                 final boolean reliable,
                                                 final DataChannelEventHandler handler) {
        channel.registerObserver(new RTCDataChannelObserver() {
            public void onBufferedAmountChange(long previousAmount) {
            }

            public void onStateChange() {
                try {
                    RTCDataChannelState state = channel.getState();
                    if (state == RTCDataChannelState.OPEN) {
                        if (reliable) {
                            handler.onReliableOpen();
                        } else {
                            handler.onUnreliableOpen();
                        }
                    } else if (state == RTCDataChannelState.CLOSED) {
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

            public void onMessage(RTCDataChannelBuffer buffer) {
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
     * Maps a native {@link dev.onvoid.webrtc.RTCPeerConnectionState} to the
     * platform-agnostic {@link ConnectionState} integer constants.
     *
     * <p>The mapping is straightforward:</p>
     * <ul>
     *   <li>{@code NEW} to {@link ConnectionState#NEW}</li>
     *   <li>{@code CONNECTING} to {@link ConnectionState#CONNECTING}</li>
     *   <li>{@code CONNECTED} to {@link ConnectionState#CONNECTED}</li>
     *   <li>{@code DISCONNECTED} to {@link ConnectionState#DISCONNECTED}</li>
     *   <li>{@code FAILED} to {@link ConnectionState#FAILED}</li>
     *   <li>{@code CLOSED} to {@link ConnectionState#CLOSED}</li>
     * </ul>
     *
     * <p>Any unrecognized state defaults to {@link ConnectionState#NEW}.</p>
     *
     * @param state the native WebRTC peer connection state
     * @return the corresponding {@link ConnectionState} integer constant
     */
    private static int mapConnectionState(RTCPeerConnectionState state) {
        if (state == RTCPeerConnectionState.NEW) {
            return ConnectionState.NEW;
        } else if (state == RTCPeerConnectionState.CONNECTING) {
            return ConnectionState.CONNECTING;
        } else if (state == RTCPeerConnectionState.CONNECTED) {
            return ConnectionState.CONNECTED;
        } else if (state == RTCPeerConnectionState.DISCONNECTED) {
            return ConnectionState.DISCONNECTED;
        } else if (state == RTCPeerConnectionState.FAILED) {
            return ConnectionState.FAILED;
        } else if (state == RTCPeerConnectionState.CLOSED) {
            return ConnectionState.CLOSED;
        }
        return ConnectionState.NEW;
    }
}
