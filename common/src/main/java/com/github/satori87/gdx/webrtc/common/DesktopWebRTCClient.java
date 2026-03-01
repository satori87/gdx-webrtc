package com.github.satori87.gdx.webrtc.common;

import com.github.satori87.gdx.webrtc.*;
import dev.onvoid.webrtc.*;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Desktop WebRTC client implementation using webrtc-java (dev.onvoid.webrtc).
 * Manages signaling via WebSocket and peer connections via native WebRTC.
 */
class DesktopWebRTCClient implements WebRTCClient, DesktopSignalingClient.Listener {

    private static final String TAG = "[WebRTC-Desktop] ";
    private static final long UNRELIABLE_BUFFER_LIMIT = 65536; // 64 KB

    private static PeerConnectionFactory factory;

    private static synchronized PeerConnectionFactory getFactory() {
        if (factory == null) {
            System.out.println(TAG + "Creating PeerConnectionFactory...");
            try {
                factory = new PeerConnectionFactory();
                System.out.println(TAG + "PeerConnectionFactory created OK");
            } catch (Exception e) {
                System.err.println(TAG + "PeerConnectionFactory FAILED: " + e);
                e.printStackTrace();
            }
        }
        return factory;
    }

    private final WebRTCConfiguration config;
    private WebRTCClientListener listener;
    private DesktopSignalingClient signalingClient;
    private int localId = -1;

    private final Map<Integer, PeerState> peers = new ConcurrentHashMap<Integer, PeerState>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
            new java.util.concurrent.ThreadFactory() {
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "webrtc-scheduler");
                    t.setDaemon(true);
                    return t;
                }
            });

    /** Internal state for a single peer connection. */
    private class PeerState implements WebRTCPeer {
        final int peerId;
        RTCPeerConnection pc;
        RTCDataChannel reliableChannel;
        RTCDataChannel unreliableChannel;
        volatile boolean connected;
        volatile boolean iceClosedOrFailed;
        volatile long disconnectedAtMs;
        volatile int iceRestartAttempts;
        boolean isOfferer;

        PeerState(int peerId) {
            this.peerId = peerId;
        }

        public int getId() { return peerId; }

        public void sendReliable(byte[] data) {
            if (reliableChannel != null && connected) {
                sendToChannel(reliableChannel, data);
            }
        }

        public void sendUnreliable(byte[] data) {
            if (unreliableChannel != null && connected) {
                try {
                    if (unreliableChannel.getBufferedAmount() > UNRELIABLE_BUFFER_LIMIT) return;
                } catch (Exception e) { /* send anyway */ }
                sendToChannel(unreliableChannel, data);
            } else {
                sendReliable(data); // fallback
            }
        }

        public boolean isConnected() { return connected; }

        public void close() {
            connected = false;
            if (pc != null) {
                try { pc.close(); } catch (Exception e) { /* ignore */ }
                pc = null;
            }
            reliableChannel = null;
            unreliableChannel = null;
            peers.remove(peerId);
        }
    }

    DesktopWebRTCClient(WebRTCConfiguration config, WebRTCClientListener listener) {
        this.config = config;
        this.listener = listener;
    }

    // --- WebRTCClient interface ---

    public void connect() {
        signalingClient = new DesktopSignalingClient(this);
        signalingClient.connect(config.signalingServerUrl);
    }

    public void disconnect() {
        // Close all peer connections
        for (PeerState peer : peers.values()) {
            peer.close();
        }
        peers.clear();

        // Close signaling
        if (signalingClient != null) {
            signalingClient.close();
            signalingClient = null;
        }
        localId = -1;
    }

    public boolean isConnectedToSignaling() {
        return signalingClient != null && signalingClient.isOpen();
    }

    public void connectToPeer(int peerId) {
        SignalMessage req = new SignalMessage(SignalMessage.TYPE_CONNECT_REQUEST, localId, peerId, "");
        signalingClient.send(req);
    }

    public void setListener(WebRTCClientListener listener) {
        this.listener = listener;
    }

    public int getLocalId() { return localId; }

    // --- DesktopSignalingClient.Listener ---

    public void onSignalingOpen() {
        System.out.println(TAG + "Connected to signaling server");
    }

    public void onSignalingMessage(SignalMessage msg) {
        switch (msg.type) {
            case SignalMessage.TYPE_WELCOME:
                localId = Integer.parseInt(msg.data);
                System.out.println(TAG + "Assigned peer ID: " + localId);
                if (listener != null) listener.onSignalingConnected(localId);
                break;

            case SignalMessage.TYPE_CONNECT_REQUEST:
                // We received a connect request — we become the offerer
                handleConnectRequest(msg.source);
                break;

            case SignalMessage.TYPE_OFFER:
                // We received an offer — we become the answerer
                handleOffer(msg.source, msg.data);
                break;

            case SignalMessage.TYPE_ANSWER:
                handleAnswer(msg.source, msg.data);
                break;

            case SignalMessage.TYPE_ICE:
                handleIce(msg.source, msg.data);
                break;

            case SignalMessage.TYPE_ERROR:
                System.err.println(TAG + "Signaling error: " + msg.data);
                if (listener != null) listener.onError(msg.data);
                break;

            case SignalMessage.TYPE_PEER_JOINED:
                if (listener != null) listener.onPeerJoined(msg.source);
                break;

            case SignalMessage.TYPE_PEER_LEFT:
                if (listener != null) listener.onPeerLeft(msg.source);
                break;

            case SignalMessage.TYPE_PEER_LIST:
                break;
        }
    }

    public void onSignalingClose(String reason) {
        System.out.println(TAG + "Signaling connection closed: " + reason);
    }

    public void onSignalingError(String error) {
        System.err.println(TAG + "Signaling error: " + error);
        if (listener != null) listener.onError("Signaling: " + error);
    }

    // --- Signaling handlers ---

    private void handleConnectRequest(int remotePeerId) {
        System.out.println(TAG + "Connect request from peer " + remotePeerId + ", creating offer...");

        PeerConnectionFactory pcFactory = getFactory();
        if (pcFactory == null) {
            if (listener != null) listener.onError("WebRTC factory initialization failed");
            return;
        }

        final PeerState peer = new PeerState(remotePeerId);
        peer.isOfferer = true;
        peers.put(remotePeerId, peer);

        RTCConfiguration rtcConfig = buildRtcConfig();

        try {
            peer.pc = pcFactory.createPeerConnection(rtcConfig, createObserver(peer));
        } catch (Exception e) {
            System.err.println(TAG + "createPeerConnection FAILED: " + e);
            if (listener != null) listener.onError("Failed to create peer connection: " + e.getMessage());
            return;
        }

        // Create data channels (offerer creates them)
        RTCDataChannelInit reliableInit = new RTCDataChannelInit();
        reliableInit.ordered = true;
        peer.reliableChannel = peer.pc.createDataChannel("reliable", reliableInit);
        setupChannel(peer, peer.reliableChannel, true);

        RTCDataChannelInit unreliableInit = new RTCDataChannelInit();
        unreliableInit.ordered = false;
        unreliableInit.maxRetransmits = 0;
        peer.unreliableChannel = peer.pc.createDataChannel("unreliable", unreliableInit);
        setupChannel(peer, peer.unreliableChannel, false);

        // Create offer
        peer.pc.createOffer(new RTCOfferOptions(), new CreateSessionDescriptionObserver() {
            public void onSuccess(RTCSessionDescription description) {
                try {
                    peer.pc.setLocalDescription(description, new SetSessionDescriptionObserver() {
                        public void onSuccess() {
                            System.out.println(TAG + "Offer created, sending to peer " + remotePeerId);
                            SignalMessage offer = new SignalMessage(
                                    SignalMessage.TYPE_OFFER, localId, remotePeerId, description.sdp);
                            signalingClient.send(offer);
                        }
                        public void onFailure(String error) {
                            System.err.println(TAG + "Set local desc failed: " + error);
                        }
                    });
                } catch (Exception e) {
                    System.err.println(TAG + "Error in createOffer.onSuccess: " + e);
                }
            }
            public void onFailure(String error) {
                System.err.println(TAG + "Create offer failed: " + error);
                if (listener != null) listener.onError("Create offer failed: " + error);
            }
        });
    }

    private void handleOffer(final int remotePeerId, String sdpOffer) {
        System.out.println(TAG + "Received offer from peer " + remotePeerId);

        PeerConnectionFactory pcFactory = getFactory();
        if (pcFactory == null) {
            if (listener != null) listener.onError("WebRTC factory initialization failed");
            return;
        }

        final PeerState peer = new PeerState(remotePeerId);
        peer.isOfferer = false;
        peers.put(remotePeerId, peer);

        RTCConfiguration rtcConfig = buildRtcConfig();

        try {
            peer.pc = pcFactory.createPeerConnection(rtcConfig, createObserver(peer));
        } catch (Exception e) {
            System.err.println(TAG + "createPeerConnection FAILED: " + e);
            if (listener != null) listener.onError("Failed to create peer connection: " + e.getMessage());
            return;
        }

        // Set remote description (offer) and create answer
        RTCSessionDescription offer = new RTCSessionDescription(RTCSdpType.OFFER, sdpOffer);
        peer.pc.setRemoteDescription(offer, new SetSessionDescriptionObserver() {
            public void onSuccess() {
                try {
                    peer.pc.createAnswer(new RTCAnswerOptions(), new CreateSessionDescriptionObserver() {
                        public void onSuccess(RTCSessionDescription description) {
                            try {
                                peer.pc.setLocalDescription(description, new SetSessionDescriptionObserver() {
                                    public void onSuccess() {
                                        System.out.println(TAG + "Answer created, sending to peer " + remotePeerId);
                                        SignalMessage answer = new SignalMessage(
                                                SignalMessage.TYPE_ANSWER, localId, remotePeerId, description.sdp);
                                        signalingClient.send(answer);
                                    }
                                    public void onFailure(String error) {
                                        System.err.println(TAG + "Set local desc failed: " + error);
                                    }
                                });
                            } catch (Exception e) {
                                System.err.println(TAG + "Error in createAnswer.onSuccess: " + e);
                            }
                        }
                        public void onFailure(String error) {
                            System.err.println(TAG + "Create answer failed: " + error);
                            if (listener != null) listener.onError("Create answer failed: " + error);
                        }
                    });
                } catch (Exception e) {
                    System.err.println(TAG + "Error in setRemoteDescription.onSuccess: " + e);
                }
            }
            public void onFailure(String error) {
                System.err.println(TAG + "Set remote desc failed: " + error);
                if (listener != null) listener.onError("Set remote desc failed: " + error);
            }
        });
    }

    private void handleAnswer(int remotePeerId, String sdpAnswer) {
        PeerState peer = peers.get(remotePeerId);
        if (peer == null || peer.pc == null) return;

        System.out.println(TAG + "Received answer from peer " + remotePeerId);
        RTCSessionDescription answer = new RTCSessionDescription(RTCSdpType.ANSWER, sdpAnswer);
        peer.pc.setRemoteDescription(answer, new SetSessionDescriptionObserver() {
            public void onSuccess() {
                System.out.println(TAG + "Remote description (answer) set OK for peer " + remotePeerId);
            }
            public void onFailure(String error) {
                System.err.println(TAG + "Set remote desc (answer) failed: " + error);
            }
        });
    }

    private void handleIce(int remotePeerId, String iceJson) {
        PeerState peer = peers.get(remotePeerId);
        if (peer == null || peer.pc == null) return;

        String candidate = extractJsonString(iceJson, "candidate");
        String sdpMid = extractJsonString(iceJson, "sdpMid");
        int sdpMLineIndex = extractJsonInt(iceJson, "sdpMLineIndex");

        try {
            peer.pc.addIceCandidate(new RTCIceCandidate(sdpMid, sdpMLineIndex, candidate));
        } catch (Exception e) {
            System.err.println(TAG + "addIceCandidate failed: " + e);
        }
    }

    // --- PeerConnection observer ---

    private PeerConnectionObserver createObserver(final PeerState peer) {
        return new PeerConnectionObserver() {
            @Override
            public void onIceCandidate(RTCIceCandidate candidate) {
                try {
                    String json = "{\"candidate\":\"" + escapeJson(candidate.sdp) + "\","
                            + "\"sdpMid\":\"" + escapeJson(candidate.sdpMid) + "\","
                            + "\"sdpMLineIndex\":" + candidate.sdpMLineIndex + "}";
                    SignalMessage ice = new SignalMessage(SignalMessage.TYPE_ICE, localId, peer.peerId, json);
                    signalingClient.send(ice);
                } catch (Exception e) {
                    System.err.println(TAG + "Error sending ICE candidate: " + e);
                }
            }

            @Override
            public void onDataChannel(RTCDataChannel channel) {
                try {
                    String label = channel.getLabel();
                    System.out.println(TAG + "Data channel received from peer " + peer.peerId + ": " + label);
                    if ("reliable".equals(label)) {
                        peer.reliableChannel = channel;
                        setupChannel(peer, channel, true);
                    } else if ("unreliable".equals(label)) {
                        peer.unreliableChannel = channel;
                        setupChannel(peer, channel, false);
                    }
                } catch (Exception e) {
                    System.err.println(TAG + "Error in onDataChannel: " + e);
                }
            }

            @Override
            public void onConnectionChange(RTCPeerConnectionState state) {
                try {
                    System.out.println(TAG + "Peer " + peer.peerId + " connection state: " + state);
                    if (state == RTCPeerConnectionState.CONNECTED) {
                        peer.iceClosedOrFailed = false;
                        peer.disconnectedAtMs = 0;
                        peer.iceRestartAttempts = 0;
                        if (peer.reliableChannel != null
                                && peer.reliableChannel.getState() != RTCDataChannelState.OPEN) {
                            System.out.println(TAG + "ICE recovered but reliable channel is "
                                    + peer.reliableChannel.getState() + " — disconnecting");
                            peer.connected = false;
                            if (listener != null) listener.onDisconnected(peer);
                        }
                    } else if (state == RTCPeerConnectionState.DISCONNECTED) {
                        peer.disconnectedAtMs = System.currentTimeMillis();
                        final long stamp = peer.disconnectedAtMs;
                        System.out.println(TAG + "Peer " + peer.peerId
                                + " temporarily disconnected, will restart ICE in "
                                + config.iceRestartDelayMs + "ms...");
                        scheduler.schedule(new Runnable() {
                            public void run() {
                                try {
                                    if (peer.disconnectedAtMs == stamp
                                            && !peer.iceClosedOrFailed && peer.pc != null) {
                                        System.out.println(TAG + "Still disconnected, restarting ICE for peer " + peer.peerId);
                                        peer.pc.restartIce();
                                    }
                                } catch (Exception e) {
                                    System.err.println(TAG + "Delayed ICE restart failed: " + e);
                                }
                            }
                        }, config.iceRestartDelayMs, TimeUnit.MILLISECONDS);
                    } else if (state == RTCPeerConnectionState.FAILED) {
                        peer.disconnectedAtMs = 0;
                        peer.iceRestartAttempts++;
                        peer.iceClosedOrFailed = true;
                        if (peer.iceRestartAttempts > config.maxIceRestartAttempts) {
                            System.out.println(TAG + "Peer " + peer.peerId
                                    + " connection failed after " + peer.iceRestartAttempts
                                    + " ICE restart attempts, giving up");
                            peer.connected = false;
                            if (listener != null) listener.onDisconnected(peer);
                        } else {
                            long backoffMs = 2000L * (1L << (peer.iceRestartAttempts - 1));
                            System.out.println(TAG + "Peer " + peer.peerId
                                    + " connection failed, ICE restart attempt "
                                    + peer.iceRestartAttempts + " in " + backoffMs + "ms...");
                            scheduler.schedule(new Runnable() {
                                public void run() {
                                    try {
                                        if (peer.pc != null && peer.iceClosedOrFailed) {
                                            peer.pc.restartIce();
                                        }
                                    } catch (Exception e) {
                                        System.err.println(TAG + "ICE restart failed: " + e);
                                        peer.connected = false;
                                        if (listener != null) listener.onDisconnected(peer);
                                    }
                                }
                            }, backoffMs, TimeUnit.MILLISECONDS);
                        }
                    } else if (state == RTCPeerConnectionState.CLOSED) {
                        peer.iceClosedOrFailed = true;
                        peer.disconnectedAtMs = 0;
                        peer.connected = false;
                        if (listener != null) listener.onDisconnected(peer);
                    }
                } catch (Exception e) {
                    System.err.println(TAG + "Error in onConnectionChange: " + e);
                }
            }
        };
    }

    // --- Data channel setup ---

    private void setupChannel(final PeerState peer, RTCDataChannel channel, final boolean isReliable) {
        channel.registerObserver(new RTCDataChannelObserver() {
            public void onBufferedAmountChange(long previousAmount) {}

            public void onStateChange() {
                try {
                    RTCDataChannelState state = channel.getState();
                    System.out.println(TAG + "Peer " + peer.peerId + " channel "
                            + (isReliable ? "reliable" : "unreliable") + " state: " + state);
                    if (state == RTCDataChannelState.OPEN && isReliable) {
                        peer.connected = true;
                        if (listener != null) listener.onConnected(peer);
                    } else if (state == RTCDataChannelState.CLOSED && isReliable) {
                        peer.connected = false;
                        if (listener != null) listener.onDisconnected(peer);
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
                    if (listener != null) listener.onMessage(peer, data, isReliable);
                } catch (Exception e) {
                    System.err.println(TAG + "Error in channel onMessage: " + e);
                }
            }
        });
    }

    // --- Helpers ---

    private RTCConfiguration buildRtcConfig() {
        RTCConfiguration rtcConfig = new RTCConfiguration();

        RTCIceServer stunServer = new RTCIceServer();
        stunServer.urls.add(config.stunServer);
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

    private static void sendToChannel(RTCDataChannel channel, byte[] data) {
        try {
            ByteBuffer buf = ByteBuffer.wrap(data);
            channel.send(new RTCDataChannelBuffer(buf, true));
        } catch (Exception e) {
            // Channel may be closing
        }
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }

    private static String extractJsonString(String json, String key) {
        String search = "\"" + key + "\":\"";
        int start = json.indexOf(search);
        if (start < 0) return "";
        start += search.length();
        int end = json.indexOf("\"", start);
        if (end < 0) return "";
        return json.substring(start, end).replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private static int extractJsonInt(String json, String key) {
        String search = "\"" + key + "\":";
        int start = json.indexOf(search);
        if (start < 0) return 0;
        start += search.length();
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) {
            end++;
        }
        try {
            return Integer.parseInt(json.substring(start, end));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
