package com.github.satori87.gdx.webrtc.android;

import android.content.Context;

import com.github.satori87.gdx.webrtc.*;

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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Android WebRTC client implementation using Google's WebRTC SDK (org.webrtc).
 * Manages signaling via WebSocket and peer connections via native WebRTC.
 */
class AndroidWebRTCClient implements WebRTCClient, AndroidSignalingClient.Listener {

    private static final String TAG = "[WebRTC-Android] ";
    private static final long UNRELIABLE_BUFFER_LIMIT = 65536; // 64 KB

    private static PeerConnectionFactory factory;
    private static boolean factoryInitialized;

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

    private final Context applicationContext;
    private final WebRTCConfiguration config;
    private WebRTCClientListener listener;
    private AndroidSignalingClient signalingClient;
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
        PeerConnection pc;
        DataChannel reliableChannel;
        DataChannel unreliableChannel;
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
                    if (unreliableChannel.bufferedAmount() > UNRELIABLE_BUFFER_LIMIT) return;
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

    AndroidWebRTCClient(Context context, WebRTCConfiguration config, WebRTCClientListener listener) {
        this.applicationContext = context;
        this.config = config;
        this.listener = listener;
    }

    // --- WebRTCClient interface ---

    public void connect() {
        signalingClient = new AndroidSignalingClient(this);
        signalingClient.connect(config.signalingServerUrl);
    }

    public void disconnect() {
        for (PeerState peer : peers.values()) {
            peer.close();
        }
        peers.clear();

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

    // --- AndroidSignalingClient.Listener ---

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
                handleConnectRequest(msg.source);
                break;

            case SignalMessage.TYPE_OFFER:
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

    private void handleConnectRequest(final int remotePeerId) {
        System.out.println(TAG + "Connect request from peer " + remotePeerId + ", creating offer...");

        PeerConnectionFactory pcFactory = getFactory(applicationContext);
        if (pcFactory == null) {
            if (listener != null) listener.onError("WebRTC factory initialization failed");
            return;
        }

        final PeerState peer = new PeerState(remotePeerId);
        peer.isOfferer = true;
        peers.put(remotePeerId, peer);

        PeerConnection.RTCConfiguration rtcConfig = buildRtcConfig();

        try {
            peer.pc = pcFactory.createPeerConnection(rtcConfig, createObserver(peer));
        } catch (Exception e) {
            System.err.println(TAG + "createPeerConnection FAILED: " + e);
            if (listener != null) listener.onError("Failed to create peer connection: " + e.getMessage());
            return;
        }

        // Create data channels (offerer creates them)
        DataChannel.Init reliableInit = new DataChannel.Init();
        reliableInit.ordered = true;
        peer.reliableChannel = peer.pc.createDataChannel("reliable", reliableInit);
        setupChannel(peer, peer.reliableChannel, true);

        DataChannel.Init unreliableInit = new DataChannel.Init();
        unreliableInit.ordered = false;
        unreliableInit.maxRetransmits = 0;
        peer.unreliableChannel = peer.pc.createDataChannel("unreliable", unreliableInit);
        setupChannel(peer, peer.unreliableChannel, false);

        // Create offer
        MediaConstraints constraints = new MediaConstraints();
        peer.pc.createOffer(new CreateSdpObserver() {
            public void onCreateSuccess(SessionDescription sdp) {
                try {
                    peer.pc.setLocalDescription(new SetSdpObserver() {
                        public void onSetSuccess() {
                            System.out.println(TAG + "Offer created, sending to peer " + remotePeerId);
                            SignalMessage offer = new SignalMessage(
                                    SignalMessage.TYPE_OFFER, localId, remotePeerId, sdp.description);
                            signalingClient.send(offer);
                        }
                        public void onSetFailure(String error) {
                            System.err.println(TAG + "Set local desc failed: " + error);
                        }
                    }, sdp);
                } catch (Exception e) {
                    System.err.println(TAG + "Error in createOffer.onSuccess: " + e);
                }
            }
            public void onCreateFailure(String error) {
                System.err.println(TAG + "Create offer failed: " + error);
                if (listener != null) listener.onError("Create offer failed: " + error);
            }
        }, constraints);
    }

    private void handleOffer(final int remotePeerId, String sdpOffer) {
        System.out.println(TAG + "Received offer from peer " + remotePeerId);

        PeerConnectionFactory pcFactory = getFactory(applicationContext);
        if (pcFactory == null) {
            if (listener != null) listener.onError("WebRTC factory initialization failed");
            return;
        }

        final PeerState peer = new PeerState(remotePeerId);
        peer.isOfferer = false;
        peers.put(remotePeerId, peer);

        PeerConnection.RTCConfiguration rtcConfig = buildRtcConfig();

        try {
            peer.pc = pcFactory.createPeerConnection(rtcConfig, createObserver(peer));
        } catch (Exception e) {
            System.err.println(TAG + "createPeerConnection FAILED: " + e);
            if (listener != null) listener.onError("Failed to create peer connection: " + e.getMessage());
            return;
        }

        // Set remote description (offer) and create answer
        SessionDescription offer = new SessionDescription(SessionDescription.Type.OFFER, sdpOffer);
        peer.pc.setRemoteDescription(new SetSdpObserver() {
            public void onSetSuccess() {
                try {
                    MediaConstraints answerConstraints = new MediaConstraints();
                    peer.pc.createAnswer(new CreateSdpObserver() {
                        public void onCreateSuccess(SessionDescription sdp) {
                            try {
                                peer.pc.setLocalDescription(new SetSdpObserver() {
                                    public void onSetSuccess() {
                                        System.out.println(TAG + "Answer created, sending to peer " + remotePeerId);
                                        SignalMessage answer = new SignalMessage(
                                                SignalMessage.TYPE_ANSWER, localId, remotePeerId, sdp.description);
                                        signalingClient.send(answer);
                                    }
                                    public void onSetFailure(String error) {
                                        System.err.println(TAG + "Set local desc failed: " + error);
                                    }
                                }, sdp);
                            } catch (Exception e) {
                                System.err.println(TAG + "Error in createAnswer.onSuccess: " + e);
                            }
                        }
                        public void onCreateFailure(String error) {
                            System.err.println(TAG + "Create answer failed: " + error);
                            if (listener != null) listener.onError("Create answer failed: " + error);
                        }
                    }, answerConstraints);
                } catch (Exception e) {
                    System.err.println(TAG + "Error in setRemoteDescription.onSuccess: " + e);
                }
            }
            public void onSetFailure(String error) {
                System.err.println(TAG + "Set remote desc failed: " + error);
                if (listener != null) listener.onError("Set remote desc failed: " + error);
            }
        }, offer);
    }

    private void handleAnswer(int remotePeerId, String sdpAnswer) {
        final PeerState peer = peers.get(remotePeerId);
        if (peer == null || peer.pc == null) return;

        System.out.println(TAG + "Received answer from peer " + remotePeerId);
        SessionDescription answer = new SessionDescription(SessionDescription.Type.ANSWER, sdpAnswer);
        peer.pc.setRemoteDescription(new SetSdpObserver() {
            public void onSetSuccess() {
                System.out.println(TAG + "Remote description (answer) set OK for peer " + peer.peerId);
            }
            public void onSetFailure(String error) {
                System.err.println(TAG + "Set remote desc (answer) failed: " + error);
            }
        }, answer);
    }

    private void handleIce(int remotePeerId, String iceJson) {
        PeerState peer = peers.get(remotePeerId);
        if (peer == null || peer.pc == null) return;

        String candidate = extractJsonString(iceJson, "candidate");
        String sdpMid = extractJsonString(iceJson, "sdpMid");
        int sdpMLineIndex = extractJsonInt(iceJson, "sdpMLineIndex");

        try {
            peer.pc.addIceCandidate(new IceCandidate(sdpMid, sdpMLineIndex, candidate));
        } catch (Exception e) {
            System.err.println(TAG + "addIceCandidate failed: " + e);
        }
    }

    // --- PeerConnection observer ---

    private PeerConnection.Observer createObserver(final PeerState peer) {
        return new PeerConnection.Observer() {
            @Override
            public void onIceCandidate(IceCandidate candidate) {
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
            public void onDataChannel(DataChannel channel) {
                try {
                    String label = channel.label();
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
            public void onConnectionChange(PeerConnection.PeerConnectionState state) {
                try {
                    System.out.println(TAG + "Peer " + peer.peerId + " connection state: " + state);
                    if (state == PeerConnection.PeerConnectionState.CONNECTED) {
                        peer.iceClosedOrFailed = false;
                        peer.disconnectedAtMs = 0;
                        peer.iceRestartAttempts = 0;
                    } else if (state == PeerConnection.PeerConnectionState.DISCONNECTED) {
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
                    } else if (state == PeerConnection.PeerConnectionState.FAILED) {
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
                    } else if (state == PeerConnection.PeerConnectionState.CLOSED) {
                        peer.iceClosedOrFailed = true;
                        peer.disconnectedAtMs = 0;
                        peer.connected = false;
                        if (listener != null) listener.onDisconnected(peer);
                    }
                } catch (Exception e) {
                    System.err.println(TAG + "Error in onConnectionChange: " + e);
                }
            }

            // --- Stub methods required by PeerConnection.Observer ---

            @Override
            public void onSignalingChange(PeerConnection.SignalingState state) {}

            @Override
            public void onIceConnectionChange(PeerConnection.IceConnectionState state) {}

            @Override
            public void onIceConnectionReceivingChange(boolean receiving) {}

            @Override
            public void onIceGatheringChange(PeerConnection.IceGatheringState state) {}

            @Override
            public void onIceCandidatesRemoved(IceCandidate[] candidates) {}

            @Override
            public void onAddStream(MediaStream stream) {}

            @Override
            public void onRemoveStream(MediaStream stream) {}

            @Override
            public void onRenegotiationNeeded() {}

            @Override
            public void onAddTrack(RtpReceiver receiver, MediaStream[] streams) {}
        };
    }

    // --- Data channel setup ---

    private void setupChannel(final PeerState peer, DataChannel channel, final boolean isReliable) {
        channel.registerObserver(new DataChannel.Observer() {
            public void onBufferedAmountChange(long previousAmount) {}

            public void onStateChange() {
                try {
                    DataChannel.State state = channel.state();
                    System.out.println(TAG + "Peer " + peer.peerId + " channel "
                            + (isReliable ? "reliable" : "unreliable") + " state: " + state);
                    if (state == DataChannel.State.OPEN && isReliable) {
                        peer.connected = true;
                        if (listener != null) listener.onConnected(peer);
                    } else if (state == DataChannel.State.CLOSED && isReliable) {
                        peer.connected = false;
                        if (listener != null) listener.onDisconnected(peer);
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
                    if (listener != null) listener.onMessage(peer, data, isReliable);
                } catch (Exception e) {
                    System.err.println(TAG + "Error in channel onMessage: " + e);
                }
            }
        });
    }

    // --- SDP observer adapters ---

    /** Adapter for createOffer/createAnswer — stubs the set methods. */
    private static abstract class CreateSdpObserver implements SdpObserver {
        public void onSetSuccess() {}
        public void onSetFailure(String error) {}
    }

    /** Adapter for setLocalDescription/setRemoteDescription — stubs the create methods. */
    private static abstract class SetSdpObserver implements SdpObserver {
        public void onCreateSuccess(SessionDescription sdp) {}
        public void onCreateFailure(String error) {}
    }

    // --- Helpers ---

    private PeerConnection.RTCConfiguration buildRtcConfig() {
        List<PeerConnection.IceServer> iceServers = new ArrayList<PeerConnection.IceServer>();

        iceServers.add(PeerConnection.IceServer.builder(config.stunServer).createIceServer());

        if (config.turnServer != null && !config.turnServer.isEmpty()) {
            PeerConnection.IceServer.Builder turnBuilder = PeerConnection.IceServer.builder(config.turnServer);
            if (config.turnUsername != null) turnBuilder.setUsername(config.turnUsername);
            if (config.turnPassword != null) turnBuilder.setPassword(config.turnPassword);
            iceServers.add(turnBuilder.createIceServer());
        }

        PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(iceServers);

        if (config.forceRelay) {
            rtcConfig.iceTransportsType = PeerConnection.IceTransportsType.RELAY;
        }

        return rtcConfig;
    }

    private static void sendToChannel(DataChannel channel, byte[] data) {
        try {
            ByteBuffer buf = ByteBuffer.wrap(data);
            channel.send(new DataChannel.Buffer(buf, true));
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
