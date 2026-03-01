package com.github.satori87.gdx.webrtc.ios;

import com.github.satori87.gdx.webrtc.*;
import com.github.satori87.gdx.webrtc.ios.bindings.*;

import org.robovm.apple.foundation.NSArray;
import org.robovm.apple.foundation.NSObject;
import org.robovm.objc.block.VoidBlock1;
import org.robovm.objc.block.VoidBlock2;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * iOS WebRTC client implementation using native WebRTC.framework via RoboVM bindings.
 * Manages signaling via WebSocket and peer connections via native WebRTC.
 */
class IOSWebRTCClient implements WebRTCClient, IOSSignalingClient.Listener {

    private static final String TAG = "[WebRTC-iOS] ";
    private static final long UNRELIABLE_BUFFER_LIMIT = 65536; // 64 KB

    private static RTCPeerConnectionFactory factory;

    private static synchronized RTCPeerConnectionFactory getFactory() {
        if (factory == null) {
            System.out.println(TAG + "Creating RTCPeerConnectionFactory...");
            try {
                factory = RTCPeerConnectionFactory.create();
                System.out.println(TAG + "RTCPeerConnectionFactory created OK");
            } catch (Exception e) {
                System.err.println(TAG + "RTCPeerConnectionFactory FAILED: " + e);
                e.printStackTrace();
            }
        }
        return factory;
    }

    private final WebRTCConfiguration config;
    private WebRTCClientListener listener;
    private IOSSignalingClient signalingClient;
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

    IOSWebRTCClient(WebRTCConfiguration config, WebRTCClientListener listener) {
        this.config = config;
        this.listener = listener;
    }

    // --- WebRTCClient interface ---

    public void connect() {
        signalingClient = new IOSSignalingClient(this);
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

    // --- IOSSignalingClient.Listener ---

    public void onSignalingOpen() {
        System.out.println(TAG + "Connected to signaling server");
    }

    public void onSignalingMessage(SignalMessage msg) {
        switch (msg.type) {
            case SignalMessage.TYPE_WELCOME:
                localId = Integer.parseInt(msg.data);
                System.out.println(TAG + "Assigned peer ID: " + localId);
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
            case SignalMessage.TYPE_PEER_LEFT:
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

        RTCPeerConnectionFactory pcFactory = getFactory();
        if (pcFactory == null) {
            if (listener != null) listener.onError("WebRTC factory initialization failed");
            return;
        }

        final PeerState peer = new PeerState(remotePeerId);
        peer.isOfferer = true;
        peers.put(remotePeerId, peer);

        RTCConfiguration rtcConfig = buildRtcConfig();
        RTCMediaConstraints constraints = RTCMediaConstraints.create();

        try {
            peer.pc = pcFactory.createPeerConnection(rtcConfig, constraints, createDelegate(peer));
        } catch (Exception e) {
            System.err.println(TAG + "createPeerConnection FAILED: " + e);
            if (listener != null) listener.onError("Failed to create peer connection: " + e.getMessage());
            return;
        }

        // Create data channels (offerer creates them)
        peer.reliableChannel = peer.pc.createDataChannel("reliable",
                RTCDataChannelConfiguration.createReliable());
        setupChannel(peer, peer.reliableChannel, true);

        peer.unreliableChannel = peer.pc.createDataChannel("unreliable",
                RTCDataChannelConfiguration.createUnreliable());
        setupChannel(peer, peer.unreliableChannel, false);

        // Create offer
        peer.pc.createOffer(RTCMediaConstraints.create(), new VoidBlock2<RTCSessionDescription, NSObject>() {
            public void invoke(RTCSessionDescription sdp, NSObject error) {
                if (error != null) {
                    System.err.println(TAG + "Create offer failed: " + error);
                    if (listener != null) listener.onError("Create offer failed");
                    return;
                }
                try {
                    peer.pc.setLocalDescription(sdp, new VoidBlock1<NSObject>() {
                        public void invoke(NSObject setError) {
                            if (setError != null) {
                                System.err.println(TAG + "Set local desc failed: " + setError);
                                return;
                            }
                            System.out.println(TAG + "Offer created, sending to peer " + remotePeerId);
                            SignalMessage offer = new SignalMessage(
                                    SignalMessage.TYPE_OFFER, localId, remotePeerId, sdp.getSdp());
                            signalingClient.send(offer);
                        }
                    });
                } catch (Exception e) {
                    System.err.println(TAG + "Error in createOffer completion: " + e);
                }
            }
        });
    }

    private void handleOffer(final int remotePeerId, String sdpOffer) {
        System.out.println(TAG + "Received offer from peer " + remotePeerId);

        RTCPeerConnectionFactory pcFactory = getFactory();
        if (pcFactory == null) {
            if (listener != null) listener.onError("WebRTC factory initialization failed");
            return;
        }

        final PeerState peer = new PeerState(remotePeerId);
        peer.isOfferer = false;
        peers.put(remotePeerId, peer);

        RTCConfiguration rtcConfig = buildRtcConfig();
        RTCMediaConstraints constraints = RTCMediaConstraints.create();

        try {
            peer.pc = pcFactory.createPeerConnection(rtcConfig, constraints, createDelegate(peer));
        } catch (Exception e) {
            System.err.println(TAG + "createPeerConnection FAILED: " + e);
            if (listener != null) listener.onError("Failed to create peer connection: " + e.getMessage());
            return;
        }

        // Set remote description (offer) and create answer
        RTCSessionDescription offer = RTCSessionDescription.create(RTCSdpType.OFFER, sdpOffer);
        peer.pc.setRemoteDescription(offer, new VoidBlock1<NSObject>() {
            public void invoke(NSObject error) {
                if (error != null) {
                    System.err.println(TAG + "Set remote desc failed: " + error);
                    if (listener != null) listener.onError("Set remote desc failed");
                    return;
                }
                try {
                    peer.pc.createAnswer(RTCMediaConstraints.create(),
                            new VoidBlock2<RTCSessionDescription, NSObject>() {
                        public void invoke(RTCSessionDescription sdp, NSObject answerError) {
                            if (answerError != null) {
                                System.err.println(TAG + "Create answer failed: " + answerError);
                                if (listener != null) listener.onError("Create answer failed");
                                return;
                            }
                            try {
                                peer.pc.setLocalDescription(sdp,
                                        new VoidBlock1<NSObject>() {
                                    public void invoke(NSObject setError) {
                                        if (setError != null) {
                                            System.err.println(TAG + "Set local desc failed: " + setError);
                                            return;
                                        }
                                        System.out.println(TAG + "Answer created, sending to peer " + remotePeerId);
                                        SignalMessage answer = new SignalMessage(
                                                SignalMessage.TYPE_ANSWER, localId, remotePeerId, sdp.getSdp());
                                        signalingClient.send(answer);
                                    }
                                });
                            } catch (Exception e) {
                                System.err.println(TAG + "Error in createAnswer completion: " + e);
                            }
                        }
                    });
                } catch (Exception e) {
                    System.err.println(TAG + "Error in setRemoteDescription completion: " + e);
                }
            }
        });
    }

    private void handleAnswer(final int remotePeerId, String sdpAnswer) {
        final PeerState peer = peers.get(remotePeerId);
        if (peer == null || peer.pc == null) return;

        System.out.println(TAG + "Received answer from peer " + remotePeerId);
        RTCSessionDescription answer = RTCSessionDescription.create(RTCSdpType.ANSWER, sdpAnswer);
        peer.pc.setRemoteDescription(answer, new VoidBlock1<NSObject>() {
            public void invoke(NSObject error) {
                if (error != null) {
                    System.err.println(TAG + "Set remote desc (answer) failed: " + error);
                } else {
                    System.out.println(TAG + "Remote description (answer) set OK for peer " + peer.peerId);
                }
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
            RTCIceCandidate iceCandidate = RTCIceCandidate.create(candidate, sdpMLineIndex, sdpMid);
            peer.pc.addIceCandidate(iceCandidate, new VoidBlock1<NSObject>() {
                public void invoke(NSObject error) {
                    if (error != null) {
                        System.err.println(TAG + "addIceCandidate failed: " + error);
                    }
                }
            });
        } catch (Exception e) {
            System.err.println(TAG + "addIceCandidate failed: " + e);
        }
    }

    // --- PeerConnection delegate ---

    private RTCPeerConnectionDelegate createDelegate(final PeerState peer) {
        return new RTCPeerConnectionDelegate() {
            @Override
            public void didGenerateIceCandidate(RTCPeerConnection peerConnection, RTCIceCandidate candidate) {
                try {
                    String json = "{\"candidate\":\"" + escapeJson(candidate.getSdp()) + "\","
                            + "\"sdpMid\":\"" + escapeJson(candidate.getSdpMid()) + "\","
                            + "\"sdpMLineIndex\":" + candidate.getSdpMLineIndex() + "}";
                    SignalMessage ice = new SignalMessage(SignalMessage.TYPE_ICE, localId, peer.peerId, json);
                    signalingClient.send(ice);
                } catch (Exception e) {
                    System.err.println(TAG + "Error sending ICE candidate: " + e);
                }
            }

            @Override
            public void didOpenDataChannel(RTCPeerConnection peerConnection, RTCDataChannel dataChannel) {
                try {
                    String label = dataChannel.getLabel();
                    System.out.println(TAG + "Data channel received from peer " + peer.peerId + ": " + label);
                    if ("reliable".equals(label)) {
                        peer.reliableChannel = dataChannel;
                        setupChannel(peer, dataChannel, true);
                    } else if ("unreliable".equals(label)) {
                        peer.unreliableChannel = dataChannel;
                        setupChannel(peer, dataChannel, false);
                    }
                } catch (Exception e) {
                    System.err.println(TAG + "Error in didOpenDataChannel: " + e);
                }
            }

            @Override
            public void didChangeConnectionState(RTCPeerConnection peerConnection, int newState) {
                try {
                    System.out.println(TAG + "Peer " + peer.peerId + " connection state: " + newState);
                    if (newState == RTCPeerConnectionState.CONNECTED) {
                        peer.iceClosedOrFailed = false;
                        peer.disconnectedAtMs = 0;
                        peer.iceRestartAttempts = 0;
                    } else if (newState == RTCPeerConnectionState.DISCONNECTED) {
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
                    } else if (newState == RTCPeerConnectionState.FAILED) {
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
                    } else if (newState == RTCPeerConnectionState.CLOSED) {
                        peer.iceClosedOrFailed = true;
                        peer.disconnectedAtMs = 0;
                        peer.connected = false;
                        if (listener != null) listener.onDisconnected(peer);
                    }
                } catch (Exception e) {
                    System.err.println(TAG + "Error in didChangeConnectionState: " + e);
                }
            }
        };
    }

    // --- Data channel setup ---

    private void setupChannel(final PeerState peer, RTCDataChannel channel, final boolean isReliable) {
        channel.setDelegate(new RTCDataChannelDelegate() {
            @Override
            public void dataChannelDidChangeState(RTCDataChannel dataChannel) {
                try {
                    int state = dataChannel.getReadyState();
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
                    System.err.println(TAG + "Error in dataChannelDidChangeState: " + e);
                }
            }

            @Override
            public void didReceiveMessage(RTCDataChannel dataChannel, RTCDataBuffer buffer) {
                try {
                    byte[] data = buffer.getBytes();
                    if (listener != null) listener.onMessage(peer, data, isReliable);
                } catch (Exception e) {
                    System.err.println(TAG + "Error in didReceiveMessage: " + e);
                }
            }
        });
    }

    // --- Helpers ---

    private RTCConfiguration buildRtcConfig() {
        RTCConfiguration rtcConfig = RTCConfiguration.create();

        RTCIceServer stunServer = RTCIceServer.create(config.stunServer);

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

    private static void sendToChannel(RTCDataChannel channel, byte[] data) {
        try {
            RTCDataBuffer buffer = RTCDataBuffer.create(data);
            channel.sendData(buffer);
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
