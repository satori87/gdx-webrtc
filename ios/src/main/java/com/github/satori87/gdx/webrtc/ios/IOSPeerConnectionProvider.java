package com.github.satori87.gdx.webrtc.ios;

import com.github.satori87.gdx.webrtc.*;
import com.github.satori87.gdx.webrtc.util.Log;
import com.github.satori87.gdx.webrtc.ios.bindings.*;

import org.robovm.apple.foundation.NSArray;
import org.robovm.apple.foundation.NSObject;
import org.robovm.objc.block.VoidBlock1;
import org.robovm.objc.block.VoidBlock2;

/**
 * iOS implementation of PeerConnectionProvider using native WebRTC.framework via RoboVM bindings.
 * Uses a singleton RTCPeerConnectionFactory with synchronized lazy initialization.
 */
class IOSPeerConnectionProvider implements PeerConnectionProvider {

    private static final String TAG = "[WebRTC-iOS] ";

    /** Singleton native peer connection factory, lazily initialized. */
    private static RTCPeerConnectionFactory factory;

    /**
     * Returns the singleton RTCPeerConnectionFactory, creating it on first access.
     *
     * <p>Access is synchronized to ensure thread-safe lazy initialization.
     * If creation fails, subsequent calls will retry.</p>
     *
     * @return the factory instance, or {@code null} if creation failed
     */
    private static synchronized RTCPeerConnectionFactory getFactory() {
        if (factory == null) {
            Log.info(TAG + "Creating RTCPeerConnectionFactory...");
            try {
                factory = RTCPeerConnectionFactory.create();
                Log.info(TAG + "RTCPeerConnectionFactory created OK");
            } catch (Exception e) {
                Log.warn(TAG + "RTCPeerConnectionFactory FAILED: " + e);
                e.printStackTrace();
            }
        }
        return factory;
    }

    /** {@inheritDoc} */
    public boolean initialize() {
        return getFactory() != null;
    }

    /** {@inheritDoc} */
    public Object createPeerConnection(WebRTCConfiguration config, final PeerEventHandler handler) {
        RTCPeerConnectionFactory pcFactory = getFactory();
        if (pcFactory == null) {
            return null;
        }

        RTCConfiguration rtcConfig = buildRtcConfig(config);
        RTCMediaConstraints constraints = RTCMediaConstraints.create();

        RTCPeerConnectionDelegate delegate = new RTCPeerConnectionDelegate() {
            public void didGenerateIceCandidate(RTCPeerConnection peerConnection, RTCIceCandidate candidate) {
                try {
                    String json = SignalMessage.buildIceCandidateJson(
                            candidate.getSdp(), candidate.getSdpMid(), candidate.getSdpMLineIndex());
                    handler.onIceCandidate(json);
                } catch (Exception e) {
                    Log.warn(TAG + "Error sending ICE candidate: " + e);
                }
            }

            public void didOpenDataChannel(RTCPeerConnection peerConnection, RTCDataChannel dataChannel) {
                try {
                    String label = dataChannel.getLabel();
                    handler.onDataChannel(dataChannel, label);
                } catch (Exception e) {
                    Log.warn(TAG + "Error in didOpenDataChannel: " + e);
                }
            }

            public void didChangeConnectionState(RTCPeerConnection peerConnection, int newState) {
                try {
                    handler.onConnectionStateChanged(mapConnectionState(newState));
                } catch (Exception e) {
                    Log.warn(TAG + "Error in didChangeConnectionState: " + e);
                }
            }
        };

        try {
            return pcFactory.createPeerConnection(rtcConfig, constraints, delegate);
        } catch (Exception e) {
            Log.warn(TAG + "createPeerConnection FAILED: " + e);
            return null;
        }
    }

    /** {@inheritDoc} */
    public void createOffer(Object peerConnection, final SdpResultCallback callback) {
        final RTCPeerConnection pc = (RTCPeerConnection) peerConnection;
        pc.createOffer(RTCMediaConstraints.create(), new VoidBlock2<RTCSessionDescription, NSObject>() {
            public void invoke(final RTCSessionDescription sdp, NSObject error) {
                if (error != null) {
                    callback.onFailure("Create offer failed: " + error);
                    return;
                }
                try {
                    pc.setLocalDescription(sdp, new VoidBlock1<NSObject>() {
                        public void invoke(NSObject setError) {
                            if (setError != null) {
                                callback.onFailure("Set local desc failed: " + setError);
                                return;
                            }
                            callback.onSuccess(sdp.getSdp());
                        }
                    });
                } catch (Exception e) {
                    callback.onFailure("Error setting local description: " + e.getMessage());
                }
            }
        });
    }

    /** {@inheritDoc} */
    public void handleOffer(Object peerConnection, String remoteSdp, final SdpResultCallback callback) {
        final RTCPeerConnection pc = (RTCPeerConnection) peerConnection;
        RTCSessionDescription offer = RTCSessionDescription.create(RTCSdpType.OFFER, remoteSdp);

        pc.setRemoteDescription(offer, new VoidBlock1<NSObject>() {
            public void invoke(NSObject error) {
                if (error != null) {
                    callback.onFailure("Set remote desc failed: " + error);
                    return;
                }
                try {
                    pc.createAnswer(RTCMediaConstraints.create(),
                            new VoidBlock2<RTCSessionDescription, NSObject>() {
                        public void invoke(final RTCSessionDescription sdp, NSObject answerError) {
                            if (answerError != null) {
                                callback.onFailure("Create answer failed: " + answerError);
                                return;
                            }
                            try {
                                pc.setLocalDescription(sdp, new VoidBlock1<NSObject>() {
                                    public void invoke(NSObject setError) {
                                        if (setError != null) {
                                            callback.onFailure("Set local desc failed: " + setError);
                                            return;
                                        }
                                        callback.onSuccess(sdp.getSdp());
                                    }
                                });
                            } catch (Exception e) {
                                callback.onFailure("Error setting local description: " + e.getMessage());
                            }
                        }
                    });
                } catch (Exception e) {
                    callback.onFailure("Error creating answer: " + e.getMessage());
                }
            }
        });
    }

    /** {@inheritDoc} */
    public void setRemoteAnswer(Object peerConnection, String sdp) {
        RTCPeerConnection pc = (RTCPeerConnection) peerConnection;
        RTCSessionDescription answer = RTCSessionDescription.create(RTCSdpType.ANSWER, sdp);
        pc.setRemoteDescription(answer, new VoidBlock1<NSObject>() {
            public void invoke(NSObject error) {
                if (error != null) {
                    Log.warn(TAG + "Set remote desc (answer) failed: " + error);
                }
            }
        });
    }

    /** {@inheritDoc} */
    public void addIceCandidate(Object peerConnection, String candidateJson) {
        RTCPeerConnection pc = (RTCPeerConnection) peerConnection;
        String candidate = SignalMessage.extractString(candidateJson, "candidate");
        String sdpMid = SignalMessage.extractString(candidateJson, "sdpMid");
        int sdpMLineIndex = SignalMessage.extractInt(candidateJson, "sdpMLineIndex");

        try {
            RTCIceCandidate iceCandidate = RTCIceCandidate.create(candidate, sdpMLineIndex, sdpMid);
            pc.addIceCandidate(iceCandidate, new VoidBlock1<NSObject>() {
                public void invoke(NSObject error) {
                    if (error != null) {
                        Log.warn(TAG + "addIceCandidate failed: " + error);
                    }
                }
            });
        } catch (Exception e) {
            Log.warn(TAG + "addIceCandidate failed: " + e);
        }
    }

    /** {@inheritDoc} */
    public void restartIce(Object peerConnection) {
        RTCPeerConnection pc = (RTCPeerConnection) peerConnection;
        pc.restartIce();
    }

    /** {@inheritDoc} */
    public void closePeerConnection(Object peerConnection) {
        RTCPeerConnection pc = (RTCPeerConnection) peerConnection;
        pc.close();
    }

    /** {@inheritDoc} */
    public ChannelPair createDataChannels(Object peerConnection, int unreliableMaxRetransmits,
                                           DataChannelEventHandler handler) {
        RTCPeerConnection pc = (RTCPeerConnection) peerConnection;

        RTCDataChannel reliableChannel = pc.createDataChannel("reliable",
                RTCDataChannelConfiguration.createReliable());
        registerChannelDelegate(reliableChannel, true, handler);

        RTCDataChannelConfiguration unreliableConfig = new RTCDataChannelConfiguration();
        unreliableConfig.setIsOrdered(false);
        unreliableConfig.setMaxRetransmits(unreliableMaxRetransmits);
        RTCDataChannel unreliableChannel = pc.createDataChannel("unreliable", unreliableConfig);
        registerChannelDelegate(unreliableChannel, false, handler);

        return new ChannelPair(reliableChannel, unreliableChannel);
    }

    /** {@inheritDoc} */
    public void setupReceivedChannel(Object channel, boolean reliable,
                                      DataChannelEventHandler handler) {
        RTCDataChannel dc = (RTCDataChannel) channel;
        registerChannelDelegate(dc, reliable, handler);
    }

    /** {@inheritDoc} */
    public void sendData(Object channel, byte[] data) {
        RTCDataChannel dc = (RTCDataChannel) channel;
        try {
            RTCDataBuffer buffer = RTCDataBuffer.create(data);
            dc.sendData(buffer);
        } catch (Exception e) {
            // Channel may be closing
        }
    }

    /** {@inheritDoc} */
    public long getBufferedAmount(Object channel) {
        RTCDataChannel dc = (RTCDataChannel) channel;
        return dc.getBufferedAmount();
    }

    /** {@inheritDoc} */
    public boolean isChannelOpen(Object channel) {
        RTCDataChannel dc = (RTCDataChannel) channel;
        return dc.getReadyState() == RTCDataChannelState.OPEN;
    }

    // --- Private helpers ---

    /**
     * Builds a native RTCConfiguration from the platform-agnostic WebRTCConfiguration.
     *
     * <p>Configures STUN and TURN servers from the provided configuration, and sets
     * the ICE transport policy to RELAY if {@code config.forceRelay} is {@code true}.</p>
     *
     * @param config the platform-agnostic WebRTC configuration
     * @return a native RTCConfiguration ready for peer connection creation
     */
    private static RTCConfiguration buildRtcConfig(WebRTCConfiguration config) {
        RTCConfiguration rtcConfig = RTCConfiguration.create();

        String[] stunUrls = config.stunServers != null ? config.stunServers : new String[] { config.stunServer };
        RTCIceServer stunServer = RTCIceServer.create(stunUrls);

        if (config.turnServer != null && !config.turnServer.isEmpty()) {
            RTCIceServer turnServer = RTCIceServer.create(config.turnServer,
                    config.turnUsername != null ? config.turnUsername : "",
                    config.turnPassword != null ? config.turnPassword : "");
            rtcConfig.setIceServers(new NSArray<RTCIceServer>(stunServer, turnServer));
        } else {
            rtcConfig.setIceServers(new NSArray<RTCIceServer>(stunServer));
        }

        if (config.forceRelay) {
            rtcConfig.setIceTransportPolicy(RTCIceTransportPolicy.RELAY);
        }

        return rtcConfig;
    }

    /**
     * Registers an RTCDataChannelDelegate on the given channel that forwards
     * state change and message events to the provided handler.
     *
     * <p>The delegate routes open/close events to the appropriate reliable or
     * unreliable handler methods based on the {@code reliable} flag, and
     * forwards incoming messages with the reliability indicator.</p>
     *
     * @param channel  the data channel to register the delegate on
     * @param reliable {@code true} if this is the reliable channel,
     *                 {@code false} if unreliable
     * @param handler  the event handler to receive channel callbacks
     */
    private static void registerChannelDelegate(final RTCDataChannel channel,
                                                 final boolean reliable,
                                                 final DataChannelEventHandler handler) {
        channel.setDelegate(new RTCDataChannelDelegate() {
            public void dataChannelDidChangeState(RTCDataChannel dataChannel) {
                try {
                    int state = dataChannel.getReadyState();
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
                    Log.warn(TAG + "Error in dataChannelDidChangeState: " + e);
                }
            }

            public void didReceiveMessage(RTCDataChannel dataChannel, RTCDataBuffer buffer) {
                try {
                    byte[] data = buffer.getBytes();
                    handler.onMessage(data, reliable);
                } catch (Exception e) {
                    Log.warn(TAG + "Error in didReceiveMessage: " + e);
                }
            }
        });
    }

    /**
     * Maps a native {@link RTCPeerConnectionState} ordinal to a platform-agnostic
     * {@code ConnectionState} constant.
     *
     * @param nativeState the native connection state ordinal
     * @return the corresponding platform-agnostic connection state constant
     */
    private static int mapConnectionState(int nativeState) {
        if (nativeState == RTCPeerConnectionState.NEW) {
            return ConnectionState.NEW;
        } else if (nativeState == RTCPeerConnectionState.CONNECTING) {
            return ConnectionState.CONNECTING;
        } else if (nativeState == RTCPeerConnectionState.CONNECTED) {
            return ConnectionState.CONNECTED;
        } else if (nativeState == RTCPeerConnectionState.DISCONNECTED) {
            return ConnectionState.DISCONNECTED;
        } else if (nativeState == RTCPeerConnectionState.FAILED) {
            return ConnectionState.FAILED;
        } else if (nativeState == RTCPeerConnectionState.CLOSED) {
            return ConnectionState.CLOSED;
        }
        return ConnectionState.NEW;
    }
}
