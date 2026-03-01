package com.github.satori87.gdx.webrtc.teavm;

import com.github.satori87.gdx.webrtc.*;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;
import org.teavm.jso.dom.events.Event;
import org.teavm.jso.dom.events.EventListener;
import org.teavm.jso.typedarrays.ArrayBuffer;
import org.teavm.jso.typedarrays.Int8Array;

import java.util.HashMap;
import java.util.Map;

/**
 * Browser WebRTC client implementation using native RTCPeerConnection via TeaVM JSO.
 */
class TeaVMWebRTCClient implements WebRTCClient, TeaVMSignalingClient.Listener {

    private static final String TAG = "[WebRTC-Browser] ";
    private static final int ICE_RESTART_DELAY_MS = 3500;
    private static final int UNRELIABLE_BUFFER_LIMIT = 65536;

    private final WebRTCConfiguration config;
    private WebRTCClientListener listener;
    private TeaVMSignalingClient signalingClient;
    private int localId = -1;

    private final Map<Integer, BrowserPeerState> peers = new HashMap<Integer, BrowserPeerState>();

    /** Internal state for a single browser peer connection. */
    private class BrowserPeerState implements WebRTCPeer {
        final int peerId;
        JSObject pc;
        JSObject reliableChannel;
        JSObject unreliableChannel;
        boolean connected;
        boolean iceClosedOrFailed;
        int disconnectedTimerId = -1;
        int failedRestartTimerId = -1;
        int iceRestartAttempts;
        boolean isOfferer;

        BrowserPeerState(int peerId) {
            this.peerId = peerId;
        }

        public int getId() { return peerId; }

        public void sendReliable(byte[] data) {
            if (reliableChannel != null && connected) {
                sendChannelData(reliableChannel, toArrayBuffer(data));
            }
        }

        public void sendUnreliable(byte[] data) {
            if (unreliableChannel != null && connected) {
                if (getBufferedAmount(unreliableChannel) > UNRELIABLE_BUFFER_LIMIT) return;
                sendChannelData(unreliableChannel, toArrayBuffer(data));
            } else {
                sendReliable(data); // fallback
            }
        }

        public boolean isConnected() { return connected; }

        public void close() {
            if (disconnectedTimerId != -1) { cancelTimer(disconnectedTimerId); disconnectedTimerId = -1; }
            if (failedRestartTimerId != -1) { cancelTimer(failedRestartTimerId); failedRestartTimerId = -1; }
            connected = false;
            if (pc != null) {
                closePeerConnection(pc);
                pc = null;
            }
            reliableChannel = null;
            unreliableChannel = null;
            peers.remove(peerId);
        }
    }

    TeaVMWebRTCClient(WebRTCConfiguration config, WebRTCClientListener listener) {
        this.config = config;
        this.listener = listener;
    }

    // --- WebRTCClient interface ---

    public void connect() {
        signalingClient = new TeaVMSignalingClient(this);
        signalingClient.connect(config.signalingServerUrl);
    }

    public void disconnect() {
        // Close all peer connections
        for (BrowserPeerState peer : peers.values()) {
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

    // --- TeaVMSignalingClient.Listener ---

    public void onSignalingOpen() {
        log("Connected to signaling server");
    }

    public void onSignalingMessage(SignalMessage msg) {
        switch (msg.type) {
            case SignalMessage.TYPE_WELCOME:
                try { localId = Integer.parseInt(msg.data.trim()); } catch (NumberFormatException e) { /* ignore */ }
                log("Assigned peer ID: " + localId);
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
                log("Signaling error: " + msg.data);
                if (listener != null) listener.onError(msg.data);
                break;

            default:
                break;
        }
    }

    public void onSignalingClose(String reason) {
        log("Signaling connection closed: " + reason);
    }

    public void onSignalingError(String error) {
        log("Signaling error: " + error);
        if (listener != null) listener.onError("Signaling: " + error);
    }

    // --- Signaling handlers ---

    private void handleConnectRequest(final int remotePeerId) {
        log("Connect request from peer " + remotePeerId + ", creating offer...");

        final BrowserPeerState peer = new BrowserPeerState(remotePeerId);
        peer.isOfferer = true;
        peers.put(remotePeerId, peer);

        String turnUrl = config.turnServer != null ? config.turnServer : "";
        String turnUser = config.turnUsername != null ? config.turnUsername : "";
        String turnPass = config.turnPassword != null ? config.turnPassword : "";

        peer.pc = createPeerConnectionWithConfig(config.stunServer, turnUrl, turnUser, turnPass, config.forceRelay);

        // Set up ICE candidate handler
        setOnIceCandidate(peer.pc, new IceCallback() {
            public void onIce(String iceJson) {
                if (iceJson != null) {
                    SignalMessage ice = new SignalMessage(SignalMessage.TYPE_ICE, localId, remotePeerId, iceJson);
                    signalingClient.send(ice);
                }
            }
        });

        // Set up connection state handler
        setupConnectionStateHandler(peer);

        // Create data channels (offerer creates them)
        peer.reliableChannel = createDataChannel(peer.pc, "reliable", true);
        peer.unreliableChannel = createDataChannel(peer.pc, "unreliable", false);
        setupChannel(peer, peer.reliableChannel, true);
        setupChannel(peer, peer.unreliableChannel, false);

        // Create offer
        createOfferNative(peer.pc, new StringCallback() {
            public void onResult(String sdp) {
                log("Offer created, sending to peer " + remotePeerId);
                SignalMessage offer = new SignalMessage(SignalMessage.TYPE_OFFER, localId, remotePeerId, sdp);
                signalingClient.send(offer);
            }
        }, new StringCallback() {
            public void onResult(String error) {
                log("Create offer failed: " + error);
                if (listener != null) listener.onError("Create offer failed: " + error);
            }
        });
    }

    private void handleOffer(final int remotePeerId, String sdpOffer) {
        log("Received offer from peer " + remotePeerId);

        final BrowserPeerState peer = new BrowserPeerState(remotePeerId);
        peer.isOfferer = false;
        peers.put(remotePeerId, peer);

        String turnUrl = config.turnServer != null ? config.turnServer : "";
        String turnUser = config.turnUsername != null ? config.turnUsername : "";
        String turnPass = config.turnPassword != null ? config.turnPassword : "";

        peer.pc = createPeerConnectionWithConfig(config.stunServer, turnUrl, turnUser, turnPass, config.forceRelay);

        // Set up ICE candidate handler
        setOnIceCandidate(peer.pc, new IceCallback() {
            public void onIce(String iceJson) {
                if (iceJson != null) {
                    SignalMessage ice = new SignalMessage(SignalMessage.TYPE_ICE, localId, remotePeerId, iceJson);
                    signalingClient.send(ice);
                }
            }
        });

        // Set up connection state handler
        setupConnectionStateHandler(peer);

        // Set up data channel receiver (answerer receives channels)
        setOnDataChannel(peer.pc, new DataChannelCallback() {
            public void onChannel(JSObject channel) {
                String label = getChannelLabel(channel);
                log("Data channel received from peer " + remotePeerId + ": " + label);
                if ("reliable".equals(label)) {
                    peer.reliableChannel = channel;
                    setupChannel(peer, channel, true);
                } else if ("unreliable".equals(label)) {
                    peer.unreliableChannel = channel;
                    setupChannel(peer, channel, false);
                }
            }
        });

        // Set remote description (offer), create answer
        doSignalingHandshake(peer.pc, sdpOffer, new StringCallback() {
            public void onResult(String answerSdp) {
                log("Answer created, sending to peer " + remotePeerId);
                SignalMessage answer = new SignalMessage(SignalMessage.TYPE_ANSWER, localId, remotePeerId, answerSdp);
                signalingClient.send(answer);
            }
        }, new StringCallback() {
            public void onResult(String error) {
                log("Handshake failed: " + error);
                if (listener != null) listener.onError("WebRTC handshake failed: " + error);
            }
        });
    }

    private void handleAnswer(int remotePeerId, String sdpAnswer) {
        BrowserPeerState peer = peers.get(remotePeerId);
        if (peer == null || peer.pc == null) return;

        log("Received answer from peer " + remotePeerId);
        setRemoteAnswer(peer.pc, sdpAnswer, new VoidCallback() {
            public void onComplete() {
                log("Remote description (answer) set OK for peer " + remotePeerId);
            }
        }, new StringCallback() {
            public void onResult(String error) {
                log("Set remote answer failed: " + error);
            }
        });
    }

    private void handleIce(int remotePeerId, String iceJson) {
        BrowserPeerState peer = peers.get(remotePeerId);
        if (peer == null || peer.pc == null) return;
        addIceCandidateNative(peer.pc, iceJson);
    }

    // --- Connection state management ---

    private void setupConnectionStateHandler(final BrowserPeerState peer) {
        setOnConnectionStateChange(peer.pc, new StringCallback() {
            public void onResult(String state) {
                log("Peer " + peer.peerId + " connection state: " + state);
                if ("connected".equals(state)) {
                    peer.iceClosedOrFailed = false;
                    peer.iceRestartAttempts = 0;
                    if (peer.disconnectedTimerId != -1) { cancelTimer(peer.disconnectedTimerId); peer.disconnectedTimerId = -1; }
                    if (peer.failedRestartTimerId != -1) { cancelTimer(peer.failedRestartTimerId); peer.failedRestartTimerId = -1; }
                    if (peer.reliableChannel != null && !"open".equals(getChannelState(peer.reliableChannel))) {
                        log("ICE recovered but reliable channel is " + getChannelState(peer.reliableChannel) + " — disconnecting");
                        peer.connected = false;
                        if (listener != null) listener.onDisconnected(peer);
                    }
                } else if ("disconnected".equals(state)) {
                    log("Peer " + peer.peerId + " temporarily disconnected, will restart ICE in " + ICE_RESTART_DELAY_MS + "ms...");
                    if (peer.disconnectedTimerId != -1) cancelTimer(peer.disconnectedTimerId);
                    peer.disconnectedTimerId = scheduleIceRestart(peer.pc, ICE_RESTART_DELAY_MS);
                } else if ("failed".equals(state)) {
                    if (peer.disconnectedTimerId != -1) { cancelTimer(peer.disconnectedTimerId); peer.disconnectedTimerId = -1; }
                    if (peer.failedRestartTimerId != -1) { cancelTimer(peer.failedRestartTimerId); peer.failedRestartTimerId = -1; }
                    peer.iceClosedOrFailed = true;
                    peer.iceRestartAttempts++;
                    if (peer.iceRestartAttempts > config.maxIceRestartAttempts) {
                        log("Peer " + peer.peerId + " connection failed after " + peer.iceRestartAttempts + " ICE restart attempts, giving up");
                        if (peer.connected) {
                            peer.connected = false;
                            if (listener != null) listener.onDisconnected(peer);
                        }
                    } else {
                        int backoffMs = 2000 * (1 << (peer.iceRestartAttempts - 1));
                        log("Peer " + peer.peerId + " connection failed, ICE restart attempt " + peer.iceRestartAttempts + " in " + backoffMs + "ms...");
                        peer.failedRestartTimerId = scheduleIceRestart(peer.pc, backoffMs);
                    }
                } else if ("closed".equals(state)) {
                    peer.iceClosedOrFailed = true;
                    if (peer.disconnectedTimerId != -1) { cancelTimer(peer.disconnectedTimerId); peer.disconnectedTimerId = -1; }
                    if (peer.connected) {
                        peer.connected = false;
                        if (listener != null) listener.onDisconnected(peer);
                    }
                }
            }
        });
    }

    // --- Data channel setup ---

    private void setupChannel(final BrowserPeerState peer, JSObject channel, final boolean isReliable) {
        setChannelBinaryType(channel, "arraybuffer");

        addChannelListener(channel, "open", new EventListener<Event>() {
            public void handleEvent(Event evt) {
                log("Peer " + peer.peerId + " channel " + (isReliable ? "reliable" : "unreliable") + " OPEN");
                if (isReliable) {
                    peer.connected = true;
                    if (listener != null) listener.onConnected(peer);
                }
            }
        });

        addChannelListener(channel, "close", new EventListener<Event>() {
            public void handleEvent(Event evt) {
                log("Peer " + peer.peerId + " channel " + (isReliable ? "reliable" : "unreliable") + " CLOSED");
                if (isReliable && peer.connected) {
                    peer.connected = false;
                    if (listener != null) listener.onDisconnected(peer);
                }
            }
        });

        addChannelListener(channel, "message", new EventListener<Event>() {
            public void handleEvent(Event evt) {
                ArrayBuffer buffer = getChannelMessageData(evt);
                if (buffer != null) {
                    Int8Array arr = Int8Array.create(buffer);
                    byte[] data = new byte[arr.getLength()];
                    for (int i = 0; i < data.length; i++) {
                        data[i] = arr.get(i);
                    }
                    if (listener != null) listener.onMessage(peer, data, isReliable);
                }
            }
        });
    }

    // --- Helpers ---

    private static ArrayBuffer toArrayBuffer(byte[] data) {
        Int8Array arr = Int8Array.create(data.length);
        for (int i = 0; i < data.length; i++) {
            arr.set(i, data[i]);
        }
        return arr.getBuffer();
    }

    private static void log(String msg) {
        System.out.println(TAG + msg);
    }

    // --- JSFunctor callbacks ---

    @JSFunctor
    public interface IceCallback extends JSObject {
        void onIce(String iceJson);
    }

    @JSFunctor
    public interface DataChannelCallback extends JSObject {
        void onChannel(JSObject channel);
    }

    @JSFunctor
    public interface VoidCallback extends JSObject {
        void onComplete();
    }

    @JSFunctor
    public interface StringCallback extends JSObject {
        void onResult(String result);
    }

    // --- Native methods ---

    @JSBody(params = {"stunUrl", "turnUrl", "turnUser", "turnPass", "forceRelay"}, script =
            "var servers = [{urls: stunUrl}];"
            + "if(turnUrl && turnUrl.length > 0) servers.push({urls: turnUrl, username: turnUser || '', credential: turnPass || ''});"
            + "var cfg = {iceServers: servers};"
            + "if(forceRelay) { cfg.iceTransportPolicy = 'relay'; }"
            + "return new RTCPeerConnection(cfg);")
    private static native JSObject createPeerConnectionWithConfig(String stunUrl, String turnUrl, String turnUser, String turnPass, boolean forceRelay);

    @JSBody(params = {"pc"}, script = "try { pc.close(); } catch(e) {}")
    private static native void closePeerConnection(JSObject pc);

    @JSBody(params = {"pc", "cb"}, script = "pc.onicecandidate = function(e) { if(e.candidate) cb(JSON.stringify(e.candidate)); };")
    private static native void setOnIceCandidate(JSObject pc, IceCallback cb);

    @JSBody(params = {"pc", "cb"}, script = "pc.onconnectionstatechange = function() { cb(pc.connectionState); };")
    private static native void setOnConnectionStateChange(JSObject pc, StringCallback cb);

    @JSBody(params = {"pc", "cb"}, script = "pc.ondatachannel = function(e) { cb(e.channel); };")
    private static native void setOnDataChannel(JSObject pc, DataChannelCallback cb);

    @JSBody(params = {"pc", "name", "reliable"}, script =
            "var opts = reliable ? {ordered: true} : {ordered: false, maxRetransmits: 0};"
            + "return pc.createDataChannel(name, opts);")
    private static native JSObject createDataChannel(JSObject pc, String name, boolean reliable);

    @JSBody(params = {"pc", "successCb", "errorCb"}, script =
            "pc.createOffer()"
            + ".then(function(o){return pc.setLocalDescription(o).then(function(){return o;});})"
            + ".then(function(o){successCb(o.sdp);})"
            + ".catch(function(e){errorCb('' + e);});")
    private static native void createOfferNative(JSObject pc, StringCallback successCb, StringCallback errorCb);

    @JSBody(params = {"pc", "sdp", "successCb", "errorCb"}, script =
            "pc.setRemoteDescription({type:'offer',sdp:sdp})"
            + ".then(function(){return pc.createAnswer();})"
            + ".then(function(a){return pc.setLocalDescription(a).then(function(){return a;});})"
            + ".then(function(a){successCb(a.sdp);})"
            + ".catch(function(e){errorCb('' + e);});")
    private static native void doSignalingHandshake(JSObject pc, String sdp, StringCallback successCb, StringCallback errorCb);

    @JSBody(params = {"pc", "sdp", "successCb", "errorCb"}, script =
            "pc.setRemoteDescription({type:'answer',sdp:sdp})"
            + ".then(function(){successCb();})"
            + ".catch(function(e){errorCb('' + e);});")
    private static native void setRemoteAnswer(JSObject pc, String sdp, VoidCallback successCb, StringCallback errorCb);

    @JSBody(params = {"pc", "iceJson"}, script =
            "try{pc.addIceCandidate(JSON.parse(iceJson)).catch(function(e){});}catch(e){}")
    private static native void addIceCandidateNative(JSObject pc, String iceJson);

    @JSBody(params = {"pc", "delayMs"}, script =
            "return setTimeout(function() {"
            + "  try { if(pc.connectionState==='disconnected'||pc.connectionState==='failed') pc.restartIce(); }"
            + "  catch(e) {}"
            + "}, delayMs);")
    private static native int scheduleIceRestart(JSObject pc, int delayMs);

    @JSBody(params = {"timerId"}, script = "clearTimeout(timerId);")
    private static native void cancelTimer(int timerId);

    @JSBody(params = {"ch"}, script = "return ch.label;")
    private static native String getChannelLabel(JSObject ch);

    @JSBody(params = {"ch", "type"}, script = "ch.binaryType = type;")
    private static native void setChannelBinaryType(JSObject ch, String type);

    @JSBody(params = {"ch", "event", "listener"}, script = "ch.addEventListener(event, listener);")
    private static native void addChannelListener(JSObject ch, String event, EventListener<Event> listener);

    @JSBody(params = {"evt"}, script = "return evt.data;")
    private static native ArrayBuffer getChannelMessageData(Event evt);

    @JSBody(params = {"ch", "data"}, script =
            "try { if(ch.readyState === 'open') ch.send(data); } catch(e) {}")
    private static native void sendChannelData(JSObject ch, ArrayBuffer data);

    @JSBody(params = {"ch"}, script = "return ch.bufferedAmount || 0;")
    private static native int getBufferedAmount(JSObject ch);

    @JSBody(params = {"ch"}, script = "return ch.readyState || 'closed';")
    private static native String getChannelState(JSObject ch);
}
