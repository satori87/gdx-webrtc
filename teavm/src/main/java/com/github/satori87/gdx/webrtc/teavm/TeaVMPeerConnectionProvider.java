package com.github.satori87.gdx.webrtc.teavm;

import com.github.satori87.gdx.webrtc.ChannelPair;
import com.github.satori87.gdx.webrtc.ConnectionState;
import com.github.satori87.gdx.webrtc.DataChannelEventHandler;
import com.github.satori87.gdx.webrtc.PeerConnectionProvider;
import com.github.satori87.gdx.webrtc.PeerEventHandler;
import com.github.satori87.gdx.webrtc.SdpResultCallback;
import com.github.satori87.gdx.webrtc.WebRTCConfiguration;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;
import org.teavm.jso.dom.events.Event;
import org.teavm.jso.dom.events.EventListener;
import org.teavm.jso.typedarrays.ArrayBuffer;
import org.teavm.jso.typedarrays.Int8Array;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Browser implementation of {@link PeerConnectionProvider} using the native
 * {@code RTCPeerConnection} API via TeaVM's JSO (JavaScript Object) interop layer.
 *
 * <p>This class bridges Java code running in the browser (compiled by TeaVM) to the
 * browser's native WebRTC API. All WebRTC operations -- creating peer connections,
 * generating SDP offers/answers, managing ICE candidates, and sending/receiving data
 * over data channels -- are delegated to inline JavaScript via {@code @JSBody}-annotated
 * native methods.</p>
 *
 * <h3>JavaScript Interop</h3>
 * <p>Callback interfaces annotated with {@link org.teavm.jso.JSFunctor @JSFunctor} are used
 * to receive asynchronous results from JavaScript promise chains and event handlers. These
 * functors ({@link IceCallback}, {@link DataChannelCallback}, {@link VoidCallback},
 * {@link StringCallback}, {@link ConnectionStateCallback}) serve as typed bridges between
 * JavaScript callbacks and Java method invocations.</p>
 *
 * <h3>Data Channel Model</h3>
 * <p>Two data channels are created per peer connection:</p>
 * <ul>
 *   <li><b>reliable</b> -- ordered delivery with unlimited retransmits</li>
 *   <li><b>unreliable</b> -- unordered delivery with a configurable {@code maxRetransmits}
 *       value (typically 0 for fire-and-forget UDP-style semantics)</li>
 * </ul>
 *
 * <p>All channel handles and peer connection handles are {@link org.teavm.jso.JSObject JSObject}
 * instances, treated as opaque {@link Object} references by {@link com.github.satori87.gdx.webrtc.BaseWebRTCClient BaseWebRTCClient}.</p>
 *
 * @see PeerConnectionProvider
 * @see com.github.satori87.gdx.webrtc.BaseWebRTCClient
 * @see TeaVMWebRTCFactory
 */
public class TeaVMPeerConnectionProvider implements PeerConnectionProvider {

    /**
     * Prevents TeaVM GC from collecting JSFunctor callback objects that are only
     * referenced from JavaScript. Maps each peer connection to the list of callbacks
     * installed on it and its data channels.
     */
    private final Map<JSObject, List<Object>> callbackRetention = new HashMap<JSObject, List<Object>>();

    private void retainCallback(JSObject pc, Object callback) {
        List<Object> list = callbackRetention.get(pc);
        if (list == null) {
            list = new ArrayList<Object>();
            callbackRetention.put(pc, list);
        }
        list.add(callback);
    }

    // --- JSFunctor callback interfaces ---

    /**
     * JSFunctor callback interface for receiving ICE candidate events from JavaScript.
     *
     * <p>Invoked by the {@code onicecandidate} event handler installed on the native
     * {@code RTCPeerConnection}. The ICE candidate is provided as a JSON string
     * (the result of {@code JSON.stringify(event.candidate)}).</p>
     *
     * @see org.teavm.jso.JSFunctor
     */
    @JSFunctor
    public interface IceCallback extends JSObject {
        /**
         * Called when a new ICE candidate is gathered by the browser.
         *
         * @param iceJson the ICE candidate as a JSON string, or {@code null} when
         *                ICE gathering is complete
         */
        void onIce(String iceJson);
    }

    /**
     * JSFunctor callback interface for receiving remote data channels from JavaScript.
     *
     * <p>Invoked by the {@code ondatachannel} event handler on the native
     * {@code RTCPeerConnection} when the remote peer creates a data channel.</p>
     *
     * @see org.teavm.jso.JSFunctor
     */
    @JSFunctor
    public interface DataChannelCallback extends JSObject {
        /**
         * Called when a remote data channel is received.
         *
         * @param channel the native {@code RTCDataChannel} object as a JSObject
         */
        void onChannel(JSObject channel);
    }

    /**
     * JSFunctor callback interface for receiving completion notifications from JavaScript.
     *
     * <p>Used as a success callback for asynchronous operations that produce no result
     * value, such as setting the remote SDP answer.</p>
     *
     * @see org.teavm.jso.JSFunctor
     */
    @JSFunctor
    public interface VoidCallback extends JSObject {
        /**
         * Called when the asynchronous operation completes successfully.
         */
        void onComplete();
    }

    /**
     * JSFunctor callback interface for receiving string results from JavaScript.
     *
     * <p>Used as both success and error callbacks for asynchronous WebRTC operations.
     * When used as a success callback, the result is typically an SDP string. When used
     * as an error callback, the result is an error description.</p>
     *
     * @see org.teavm.jso.JSFunctor
     */
    @JSFunctor
    public interface StringCallback extends JSObject {
        /**
         * Called with the string result of an asynchronous operation.
         *
         * @param result the result string (SDP content on success, error description on failure)
         */
        void onResult(String result);
    }

    /**
     * JSFunctor callback interface for receiving connection state changes from JavaScript.
     *
     * <p>Invoked by the {@code onconnectionstatechange} event handler on the native
     * {@code RTCPeerConnection}. The state string corresponds to the browser's
     * {@code RTCPeerConnectionState} enum values: {@code "new"}, {@code "connecting"},
     * {@code "connected"}, {@code "disconnected"}, {@code "failed"}, or {@code "closed"}.</p>
     *
     * @see org.teavm.jso.JSFunctor
     */
    @JSFunctor
    public interface ConnectionStateCallback extends JSObject {
        /**
         * Called when the peer connection's state changes.
         *
         * @param state the new connection state as a string (e.g. {@code "connected"},
         *              {@code "disconnected"}, {@code "failed"})
         */
        void onState(String state);
    }

    // --- PeerConnectionProvider interface ---

    /**
     * Initializes the browser WebRTC runtime.
     *
     * <p>This is a no-op for the browser implementation since the native
     * {@code RTCPeerConnection} API is always available in supported browsers
     * and requires no factory initialization.</p>
     *
     * @return always {@code true}
     */
    public boolean initialize() {
        runIceDiagnostic();
        return true;
    }

    @JSBody(script =
            "console.log('[BARE-TEST] Creating independent RTCPeerConnection...');"
            + "var testPc = new RTCPeerConnection({iceServers: [{urls: 'stun:stun.l.google.com:19302'}]});"
            + "testPc.createDataChannel('test');"
            + "testPc.onicecandidate = function(e) {"
            + "  console.log('[BARE-TEST] ICE candidate: ' + (e.candidate ? e.candidate.candidate : 'null (end)'));"
            + "};"
            + "testPc.onicegatheringstatechange = function() {"
            + "  console.log('[BARE-TEST] Gathering state: ' + testPc.iceGatheringState);"
            + "  if(testPc.iceGatheringState === 'complete') {"
            + "    var sdp = testPc.localDescription ? testPc.localDescription.sdp : 'no local desc';"
            + "    console.log('[BARE-TEST] Final candidates in SDP: ' + (sdp.match(/a=candidate/g)||[]).length);"
            + "    testPc.close();"
            + "  }"
            + "};"
            + "testPc.createOffer().then(function(o) {"
            + "  return testPc.setLocalDescription(o);"
            + "}).then(function() {"
            + "  console.log('[BARE-TEST] Local desc set, gathering=' + testPc.iceGatheringState);"
            + "}).catch(function(e) {"
            + "  console.log('[BARE-TEST] Error: ' + e);"
            + "});")
    private static native void runIceDiagnostic();

    /**
     * Creates a native browser {@code RTCPeerConnection} with the given configuration.
     *
     * <p>Constructs ICE server entries from the provided STUN/TURN settings and calls
     * {@code new RTCPeerConnection(config)} via JavaScript. Installs event handlers for
     * ICE candidate gathering, connection state changes, and incoming remote data channels,
     * forwarding events to the provided {@link PeerEventHandler}.</p>
     *
     * <p>If {@link WebRTCConfiguration#forceRelay} is {@code true}, the ICE transport
     * policy is set to {@code "relay"}, forcing all traffic through the TURN server.</p>
     *
     * @param config  the WebRTC configuration containing STUN/TURN server URLs and credentials
     * @param handler callbacks for ICE candidates, connection state changes, and incoming
     *                data channels
     * @return the native {@code RTCPeerConnection} as a {@link JSObject}
     */
    public Object createPeerConnection(WebRTCConfiguration config, final PeerEventHandler handler) {
        String stunUrl = config.stunServer;
        String turnUrl = config.turnServer != null ? config.turnServer : "";
        String turnUser = config.turnUsername != null ? config.turnUsername : "";
        String turnPass = config.turnPassword != null ? config.turnPassword : "";

        JSObject pc = createPeerConnectionNative(stunUrl, turnUrl, turnUser, turnPass, config.forceRelay);

        IceCallback iceCb = new IceCallback() {
            public void onIce(String iceJson) {
                if (iceJson != null) {
                    handler.onIceCandidate(iceJson);
                }
            }
        };
        ConnectionStateCallback stateCb = new ConnectionStateCallback() {
            public void onState(String state) {
                handler.onConnectionStateChanged(mapConnectionState(state));
            }
        };
        DataChannelCallback dcCb = new DataChannelCallback() {
            public void onChannel(JSObject channel) {
                String label = getChannelLabel(channel);
                handler.onDataChannel(channel, label);
            }
        };

        retainCallback(pc, iceCb);
        retainCallback(pc, stateCb);
        retainCallback(pc, dcCb);

        setOnIceCandidate(pc, iceCb);
        setOnConnectionStateChange(pc, stateCb);
        setOnDataChannel(pc, dcCb);

        return pc;
    }

    /**
     * Creates an SDP offer on the given browser peer connection.
     *
     * <p>Calls {@code pc.createOffer()} followed by {@code pc.setLocalDescription(offer)}
     * via a JavaScript promise chain. On success, the offer's SDP string is passed to
     * {@link SdpResultCallback#onSuccess(String)}. On failure, the error is passed to
     * {@link SdpResultCallback#onFailure(String)}.</p>
     *
     * @param peerConnection the native {@code RTCPeerConnection} JSObject handle
     * @param callback       callback to receive the offer SDP string or error
     */
    public void createOffer(Object peerConnection, final SdpResultCallback callback) {
        final JSObject pc = (JSObject) peerConnection;
        StringCallback successCb = new StringCallback() {
            public void onResult(String sdp) {
                callback.onSuccess(sdp);
            }
        };
        StringCallback errorCb = new StringCallback() {
            public void onResult(String error) {
                callback.onFailure(error);
            }
        };
        retainCallback(pc, successCb);
        retainCallback(pc, errorCb);
        createOfferNative(pc, successCb, errorCb);
    }

    /**
     * Handles a received SDP offer by performing the full signaling handshake.
     *
     * <p>Executes a JavaScript promise chain that sets the remote offer as the remote
     * description, creates an SDP answer, and sets it as the local description. On
     * success, the answer SDP string is passed to {@link SdpResultCallback#onSuccess(String)}.
     * On failure, the error is passed to {@link SdpResultCallback#onFailure(String)}.</p>
     *
     * @param peerConnection the native {@code RTCPeerConnection} JSObject handle
     * @param remoteSdp      the remote SDP offer string received from the signaling server
     * @param callback       callback to receive the answer SDP string or error
     */
    public void handleOffer(Object peerConnection, String remoteSdp, final SdpResultCallback callback) {
        final JSObject pc = (JSObject) peerConnection;
        StringCallback successCb = new StringCallback() {
            public void onResult(String answerSdp) {
                callback.onSuccess(answerSdp);
            }
        };
        StringCallback errorCb = new StringCallback() {
            public void onResult(String error) {
                callback.onFailure(error);
            }
        };
        retainCallback(pc, successCb);
        retainCallback(pc, errorCb);
        doSignalingHandshake(pc, remoteSdp, successCb, errorCb);
    }

    /**
     * Sets the remote SDP answer on the peer connection.
     *
     * <p>Called on the offerer side after receiving the answerer's SDP response via
     * the signaling server. Calls {@code pc.setRemoteDescription({type:'answer', sdp})}
     * via JavaScript. Logs a warning to the console on failure.</p>
     *
     * @param peerConnection the native {@code RTCPeerConnection} JSObject handle
     * @param sdp            the remote SDP answer string
     */
    public void setRemoteAnswer(Object peerConnection, String sdp) {
        final JSObject pc = (JSObject) peerConnection;
        VoidCallback successCb = new VoidCallback() {
            public void onComplete() {
                // success — nothing to do
            }
        };
        StringCallback errorCb = new StringCallback() {
            public void onResult(String error) {
                System.out.println("[WebRTC-Browser] Set remote answer failed: " + error);
            }
        };
        retainCallback(pc, successCb);
        retainCallback(pc, errorCb);
        setRemoteAnswerNative(pc, sdp, successCb, errorCb);
    }

    /**
     * Adds a remote ICE candidate to the browser peer connection.
     *
     * <p>Parses the JSON string and calls {@code pc.addIceCandidate()} via JavaScript.
     * Errors are silently caught to avoid disruption from malformed or late-arriving
     * candidates.</p>
     *
     * @param peerConnection the native {@code RTCPeerConnection} JSObject handle
     * @param candidateJson  the JSON-encoded ICE candidate string
     */
    public void addIceCandidate(Object peerConnection, String candidateJson) {
        addIceCandidateNative((JSObject) peerConnection, candidateJson);
    }

    /**
     * Triggers an ICE restart on the browser peer connection.
     *
     * <p>Calls {@code pc.restartIce()} via JavaScript. This causes the browser to
     * re-gather ICE candidates and renegotiate the connection. Errors are silently
     * caught for robustness.</p>
     *
     * @param peerConnection the native {@code RTCPeerConnection} JSObject handle
     */
    public void restartIce(Object peerConnection) {
        restartIceNative((JSObject) peerConnection);
    }

    /**
     * Closes the browser peer connection and releases associated resources.
     *
     * <p>Calls {@code pc.close()} via JavaScript. Errors are silently caught to ensure
     * cleanup is not disrupted by connections that are already closed or in an
     * invalid state.</p>
     *
     * @param peerConnection the native {@code RTCPeerConnection} JSObject handle
     */
    public void closePeerConnection(Object peerConnection) {
        JSObject pc = (JSObject) peerConnection;
        callbackRetention.remove(pc);
        closePeerConnectionNative(pc);
    }

    /**
     * Creates both reliable and unreliable data channels on the browser peer connection.
     *
     * <p>Creates two {@code RTCDataChannel} instances via {@code pc.createDataChannel()}:</p>
     * <ul>
     *   <li><b>"reliable"</b> -- {@code ordered: true}, unlimited retransmits</li>
     *   <li><b>"unreliable"</b> -- {@code ordered: false}, with the specified
     *       {@code maxRetransmits} value</li>
     * </ul>
     *
     * <p>Event handlers ({@code onopen}, {@code onclose}, {@code onmessage}) are installed
     * on both channels, with binary type set to {@code "arraybuffer"} for efficient
     * byte array transfer.</p>
     *
     * @param peerConnection         the native {@code RTCPeerConnection} JSObject handle
     * @param unreliableMaxRetransmits the {@code maxRetransmits} value for the unreliable
     *                                 channel (0 for fire-and-forget semantics)
     * @param handler                 callbacks for channel open, close, and message events
     * @return a {@link ChannelPair} containing the reliable and unreliable channel JSObjects
     */
    public ChannelPair createDataChannels(Object peerConnection, int unreliableMaxRetransmits,
                                           DataChannelEventHandler handler) {
        JSObject pc = (JSObject) peerConnection;
        JSObject reliable = createDataChannel(pc, "reliable", true, 0);
        JSObject unreliable = createDataChannel(pc, "unreliable", false, unreliableMaxRetransmits);
        setupChannelEvents(pc, reliable, true, handler);
        setupChannelEvents(pc, unreliable, false, handler);
        return new ChannelPair(reliable, unreliable);
    }

    /**
     * Sets up event handlers on a remotely-created data channel.
     *
     * <p>Called when a data channel is received from the remote peer via the
     * {@code ondatachannel} event. Delegates to {@link #setupChannelEvents(JSObject, boolean, DataChannelEventHandler)}
     * to install open, close, and message listeners on the channel.</p>
     *
     * @param channel  the native {@code RTCDataChannel} JSObject received from the remote peer
     * @param reliable {@code true} if this is the reliable (ordered) channel,
     *                 {@code false} if it is the unreliable channel
     * @param handler  callbacks for channel open, close, and message events
     */
    public void setupReceivedChannel(Object channel, boolean reliable, DataChannelEventHandler handler) {
        JSObject ch = (JSObject) channel;
        setupChannelEvents(ch, ch, reliable, handler);
    }

    /**
     * Sends a byte array over the specified data channel.
     *
     * <p>Converts the Java byte array to a JavaScript {@link ArrayBuffer} using
     * {@link #toArrayBuffer(byte[])} and sends it via the native {@code RTCDataChannel.send()}
     * method. If the channel is not in the {@code "open"} state, the send is silently
     * dropped by the underlying JavaScript.</p>
     *
     * @param channel the native {@code RTCDataChannel} JSObject handle
     * @param data    the byte array to send
     */
    public void sendData(Object channel, byte[] data) {
        sendChannelData((JSObject) channel, toArrayBuffer(data));
    }

    /**
     * Returns the number of bytes currently queued in the data channel's send buffer.
     *
     * <p>Reads the native {@code RTCDataChannel.bufferedAmount} property via JavaScript.
     * This is used by the unreliable send path to decide whether to drop packets when
     * the buffer exceeds the 64KB threshold.</p>
     *
     * @param channel the native {@code RTCDataChannel} JSObject handle
     * @return the number of buffered bytes, or 0 if the channel is invalid
     */
    public long getBufferedAmount(Object channel) {
        return getBufferedAmountNative((JSObject) channel);
    }

    /**
     * Checks whether the specified data channel is currently open and ready for sending.
     *
     * <p>Reads the native {@code RTCDataChannel.readyState} property via JavaScript and
     * compares it to {@code "open"}.</p>
     *
     * @param channel the native {@code RTCDataChannel} JSObject handle
     * @return {@code true} if the channel's ready state is {@code "open"}, {@code false} otherwise
     */
    public boolean isChannelOpen(Object channel) {
        return "open".equals(getChannelState((JSObject) channel));
    }

    // --- Internal helpers ---

    /**
     * Maps a browser {@code RTCPeerConnectionState} string to a {@link ConnectionState} constant.
     *
     * <p>The browser provides connection state as one of: {@code "new"}, {@code "connecting"},
     * {@code "connected"}, {@code "disconnected"}, {@code "failed"}, or {@code "closed"}.
     * This method converts those strings to the corresponding integer constants defined
     * in {@link ConnectionState}. Unknown state strings default to {@link ConnectionState#NEW}.</p>
     *
     * @param state the browser connection state string
     * @return the corresponding {@link ConnectionState} integer constant
     */
    private static int mapConnectionState(String state) {
        if ("connected".equals(state)) {
            return ConnectionState.CONNECTED;
        } else if ("disconnected".equals(state)) {
            return ConnectionState.DISCONNECTED;
        } else if ("failed".equals(state)) {
            return ConnectionState.FAILED;
        } else if ("closed".equals(state)) {
            return ConnectionState.CLOSED;
        } else if ("connecting".equals(state)) {
            return ConnectionState.CONNECTING;
        } else if ("new".equals(state)) {
            return ConnectionState.NEW;
        }
        return ConnectionState.NEW;
    }

    /**
     * Installs event listeners on a native {@code RTCDataChannel} for open, close, and message events.
     *
     * <p>Sets the channel's binary type to {@code "arraybuffer"} so that incoming messages
     * arrive as {@link ArrayBuffer} instances rather than Blobs. Registers three event
     * listeners:</p>
     * <ul>
     *   <li><b>open</b> -- calls {@link DataChannelEventHandler#onReliableOpen()} or
     *       {@link DataChannelEventHandler#onUnreliableOpen()} depending on the channel type</li>
     *   <li><b>close</b> -- calls {@link DataChannelEventHandler#onReliableClose()} or
     *       {@link DataChannelEventHandler#onUnreliableClose()} depending on the channel type</li>
     *   <li><b>message</b> -- extracts the {@link ArrayBuffer} payload, converts it to a
     *       Java byte array, and calls {@link DataChannelEventHandler#onMessage(byte[], boolean)}</li>
     * </ul>
     *
     * @param channel  the native {@code RTCDataChannel} JSObject
     * @param reliable {@code true} for the reliable (ordered) channel, {@code false} for unreliable
     * @param handler  callbacks for channel lifecycle and message events
     */
    private void setupChannelEvents(JSObject retentionKey, JSObject channel, final boolean reliable,
                                     final DataChannelEventHandler handler) {
        setChannelBinaryType(channel, "arraybuffer");

        EventListener<Event> openListener = new EventListener<Event>() {
            public void handleEvent(Event evt) {
                if (reliable) {
                    handler.onReliableOpen();
                } else {
                    handler.onUnreliableOpen();
                }
            }
        };

        EventListener<Event> closeListener = new EventListener<Event>() {
            public void handleEvent(Event evt) {
                if (reliable) {
                    handler.onReliableClose();
                } else {
                    handler.onUnreliableClose();
                }
            }
        };

        EventListener<Event> messageListener = new EventListener<Event>() {
            public void handleEvent(Event evt) {
                ArrayBuffer buffer = getChannelMessageData(evt);
                if (buffer != null) {
                    Int8Array arr = Int8Array.create(buffer);
                    byte[] data = new byte[arr.getLength()];
                    for (int i = 0; i < data.length; i++) {
                        data[i] = arr.get(i);
                    }
                    handler.onMessage(data, reliable);
                }
            }
        };

        retainCallback(retentionKey, openListener);
        retainCallback(retentionKey, closeListener);
        retainCallback(retentionKey, messageListener);

        addChannelListener(channel, "open", openListener);
        addChannelListener(channel, "close", closeListener);
        addChannelListener(channel, "message", messageListener);
    }

    /**
     * Converts a Java byte array to a JavaScript {@link ArrayBuffer}.
     *
     * <p>Creates a new {@link Int8Array} of the appropriate length, copies byte values
     * one-by-one from the Java array, and returns the underlying {@link ArrayBuffer}.
     * This is necessary because TeaVM cannot directly pass Java byte arrays to
     * JavaScript WebRTC APIs.</p>
     *
     * @param data the Java byte array to convert
     * @return an {@link ArrayBuffer} containing the same bytes
     */
    private static ArrayBuffer toArrayBuffer(byte[] data) {
        Int8Array arr = Int8Array.create(data.length);
        for (int i = 0; i < data.length; i++) {
            arr.set(i, data[i]);
        }
        return arr.getBuffer();
    }

    // --- Native methods ---

    /**
     * Creates a native {@code RTCPeerConnection} with the given ICE server configuration.
     *
     * <p>Constructs an {@code iceServers} array from the provided STUN and optional TURN
     * server parameters, and passes it to {@code new RTCPeerConnection(config)}. If
     * {@code forceRelay} is {@code true}, the ICE transport policy is set to {@code "relay"}.</p>
     *
     * @param stunUrl    the STUN server URL (e.g. {@code "stun:stun.l.google.com:19302"})
     * @param turnUrl    the TURN server URL, or empty string if not configured
     * @param turnUser   the TURN server username, or empty string if not configured
     * @param turnPass   the TURN server password (credential), or empty string if not configured
     * @param forceRelay {@code true} to force all traffic through the TURN relay
     * @return the native {@code RTCPeerConnection} as a JSObject
     */
    @JSBody(params = {"stunUrl", "turnUrl", "turnUser", "turnPass", "forceRelay"}, script =
            "var servers = [{urls: stunUrl}];"
            + "if(turnUrl && turnUrl.length > 0) servers.push({urls: turnUrl, username: turnUser || '', credential: turnPass || ''});"
            + "var cfg = {iceServers: servers, iceCandidatePoolSize: 1};"
            + "if(forceRelay) { cfg.iceTransportPolicy = 'relay'; }"
            + "console.log('[WebRTC-JS] Creating PC with config:', JSON.stringify(cfg));"
            + "return new RTCPeerConnection(cfg);")
    private static native JSObject createPeerConnectionNative(String stunUrl, String turnUrl,
                                                               String turnUser, String turnPass,
                                                               boolean forceRelay);

    /**
     * Closes the native {@code RTCPeerConnection}.
     *
     * <p>Calls {@code pc.close()} inside a try-catch to silently handle connections
     * that are already closed or in an invalid state.</p>
     *
     * @param pc the native {@code RTCPeerConnection} to close
     */
    @JSBody(params = {"pc"}, script = "try { pc.close(); } catch(e) {}")
    private static native void closePeerConnectionNative(JSObject pc);

    /**
     * Installs the {@code onicecandidate} event handler on the peer connection.
     *
     * <p>When the browser gathers a new ICE candidate, it is serialized to JSON via
     * {@code JSON.stringify()} and passed to the provided callback. Null candidates
     * (signaling the end of ICE gathering) are filtered out.</p>
     *
     * @param pc the native {@code RTCPeerConnection}
     * @param cb callback invoked with each ICE candidate as a JSON string
     */
    @JSBody(params = {"pc", "cb"}, script =
            "console.log('[WebRTC-JS] Installing onicecandidate handler on pc, state=' + pc.iceGatheringState);"
            + "pc.onicecandidate = function(e) {"
            + "  console.log('[WebRTC-JS] onicecandidate fired, candidate=' + (e.candidate ? 'yes' : 'null'));"
            + "  if(e.candidate) cb(JSON.stringify(e.candidate));"
            + "};")
    private static native void setOnIceCandidate(JSObject pc, IceCallback cb);

    /**
     * Installs the {@code onconnectionstatechange} event handler on the peer connection.
     *
     * <p>When the connection state changes, the new state string (e.g. {@code "connected"},
     * {@code "disconnected"}, {@code "failed"}) is read from {@code pc.connectionState}
     * and passed to the provided callback.</p>
     *
     * @param pc the native {@code RTCPeerConnection}
     * @param cb callback invoked with the new connection state string
     */
    @JSBody(params = {"pc", "cb"}, script =
            "console.log('[WebRTC-JS] Installing onconnectionstatechange handler');"
            + "pc.onconnectionstatechange = function() {"
            + "  console.log('[WebRTC-JS] connectionstatechange: ' + pc.connectionState);"
            + "  cb(pc.connectionState);"
            + "};")
    private static native void setOnConnectionStateChange(JSObject pc, ConnectionStateCallback cb);

    /**
     * Installs the {@code ondatachannel} event handler on the peer connection.
     *
     * <p>When the remote peer creates a data channel and the browser receives it,
     * the channel JSObject is extracted from the event and passed to the callback.</p>
     *
     * @param pc the native {@code RTCPeerConnection}
     * @param cb callback invoked with the received {@code RTCDataChannel} JSObject
     */
    @JSBody(params = {"pc", "cb"}, script = "pc.ondatachannel = function(e) { cb(e.channel); };")
    private static native void setOnDataChannel(JSObject pc, DataChannelCallback cb);

    /**
     * Creates a native {@code RTCDataChannel} on the peer connection.
     *
     * <p>If {@code reliable} is {@code true}, the channel is created with {@code ordered: true}
     * and unlimited retransmits. If {@code false}, the channel is created with
     * {@code ordered: false} and the specified {@code maxRetransmits} value for
     * fire-and-forget semantics.</p>
     *
     * @param pc             the native {@code RTCPeerConnection}
     * @param name           the channel label (e.g. {@code "reliable"} or {@code "unreliable"})
     * @param reliable       {@code true} for ordered reliable delivery, {@code false} for unreliable
     * @param maxRetransmits the maximum number of retransmit attempts for unreliable channels
     *                       (ignored when {@code reliable} is {@code true})
     * @return the created {@code RTCDataChannel} as a JSObject
     */
    @JSBody(params = {"pc", "name", "reliable", "maxRetransmits"}, script =
            "var opts = reliable ? {ordered: true} : {ordered: false, maxRetransmits: maxRetransmits};"
            + "return pc.createDataChannel(name, opts);")
    private static native JSObject createDataChannel(JSObject pc, String name, boolean reliable,
                                                      int maxRetransmits);

    /**
     * Creates an SDP offer and sets it as the local description.
     *
     * <p>Executes a JavaScript promise chain: {@code pc.createOffer()} followed by
     * {@code pc.setLocalDescription(offer)}. On success, the offer's SDP string is
     * passed to {@code successCb}. On failure, the error is stringified and passed
     * to {@code errorCb}.</p>
     *
     * @param pc        the native {@code RTCPeerConnection}
     * @param successCb callback invoked with the offer SDP string on success
     * @param errorCb   callback invoked with the error string on failure
     */
    @JSBody(params = {"pc", "successCb", "errorCb"}, script =
            "console.log('[WebRTC-JS] createOffer starting, iceGathering=' + pc.iceGatheringState);"
            + "pc.createOffer()"
            + ".then(function(o){console.log('[WebRTC-JS] offer created, setting local desc'); return pc.setLocalDescription(o);})"
            + ".then(function(){"
            + "  if(pc.iceGatheringState === 'complete'){"
            + "    console.log('[WebRTC-JS] ICE gathering already complete'); successCb(pc.localDescription.sdp);"
            + "  } else {"
            + "    console.log('[WebRTC-JS] Waiting for ICE gathering, state=' + pc.iceGatheringState);"
            + "    pc.addEventListener('icegatheringstatechange', function(){"
            + "      console.log('[WebRTC-JS] ICE gathering state changed to: ' + pc.iceGatheringState);"
            + "      if(pc.iceGatheringState === 'complete'){"
            + "        var sdp = pc.localDescription.sdp;"
            + "        console.log('[WebRTC-JS] ICE complete, candidates in SDP: ' + (sdp.match(/a=candidate/g)||[]).length);"
            + "        console.log('[WebRTC-JS] Offer SDP:\\n' + sdp);"
            + "        successCb(sdp);"
            + "      }"
            + "    });"
            + "  }"
            + "})"
            + ".catch(function(e){console.log('[WebRTC-JS] createOffer error: ' + e); errorCb('' + e);});")
    private static native void createOfferNative(JSObject pc, StringCallback successCb,
                                                  StringCallback errorCb);

    /**
     * Performs the full SDP answer handshake: sets the remote offer, creates an answer,
     * and sets it as the local description.
     *
     * <p>Executes a JavaScript promise chain: {@code pc.setRemoteDescription(offer)} followed
     * by {@code pc.createAnswer()} followed by {@code pc.setLocalDescription(answer)}. On
     * success, the answer's SDP string is passed to {@code successCb}. On failure, the error
     * is stringified and passed to {@code errorCb}.</p>
     *
     * @param pc        the native {@code RTCPeerConnection}
     * @param sdp       the remote SDP offer string
     * @param successCb callback invoked with the answer SDP string on success
     * @param errorCb   callback invoked with the error string on failure
     */
    @JSBody(params = {"pc", "sdp", "successCb", "errorCb"}, script =
            "console.log('[WebRTC-JS] doSignalingHandshake starting');"
            + "pc.setRemoteDescription({type:'offer',sdp:sdp})"
            + ".then(function(){console.log('[WebRTC-JS] remote desc set, creating answer'); return pc.createAnswer();})"
            + ".then(function(a){console.log('[WebRTC-JS] answer created, setting local desc'); return pc.setLocalDescription(a);})"
            + ".then(function(){"
            + "  if(pc.iceGatheringState === 'complete'){"
            + "    console.log('[WebRTC-JS] ICE gathering already complete for answer'); successCb(pc.localDescription.sdp);"
            + "  } else {"
            + "    console.log('[WebRTC-JS] Waiting for ICE gathering (answer), state=' + pc.iceGatheringState);"
            + "    pc.addEventListener('icegatheringstatechange', function(){"
            + "      console.log('[WebRTC-JS] ICE gathering state changed to: ' + pc.iceGatheringState);"
            + "      if(pc.iceGatheringState === 'complete'){"
            + "        var sdp = pc.localDescription.sdp;"
            + "        console.log('[WebRTC-JS] ICE complete, candidates in SDP: ' + (sdp.match(/a=candidate/g)||[]).length);"
            + "        successCb(sdp);"
            + "      }"
            + "    });"
            + "  }"
            + "})"
            + ".catch(function(e){console.log('[WebRTC-JS] handshake error: ' + e); errorCb('' + e);});")
    private static native void doSignalingHandshake(JSObject pc, String sdp,
                                                     StringCallback successCb,
                                                     StringCallback errorCb);

    /**
     * Sets the remote SDP answer as the remote description on the peer connection.
     *
     * <p>Calls {@code pc.setRemoteDescription({type:'answer', sdp})} via a JavaScript
     * promise. On success, {@code successCb} is invoked. On failure, the error is
     * stringified and passed to {@code errorCb}.</p>
     *
     * @param pc        the native {@code RTCPeerConnection}
     * @param sdp       the remote SDP answer string
     * @param successCb callback invoked on successful completion
     * @param errorCb   callback invoked with the error string on failure
     */
    @JSBody(params = {"pc", "sdp", "successCb", "errorCb"}, script =
            "console.log('[WebRTC-JS] setRemoteAnswer, signalingState=' + pc.signalingState + ' iceGathering=' + pc.iceGatheringState);"
            + "pc.setRemoteDescription({type:'answer',sdp:sdp})"
            + ".then(function(){console.log('[WebRTC-JS] remote answer set, iceGathering=' + pc.iceGatheringState); successCb();})"
            + ".catch(function(e){console.log('[WebRTC-JS] setRemoteAnswer error: ' + e); errorCb('' + e);});")
    private static native void setRemoteAnswerNative(JSObject pc, String sdp,
                                                      VoidCallback successCb,
                                                      StringCallback errorCb);

    /**
     * Adds a remote ICE candidate to the peer connection.
     *
     * <p>Parses the JSON string and calls {@code pc.addIceCandidate()}. Both synchronous
     * and asynchronous errors are silently caught to handle malformed or late-arriving
     * candidates gracefully.</p>
     *
     * @param pc      the native {@code RTCPeerConnection}
     * @param iceJson the JSON-encoded ICE candidate string
     */
    @JSBody(params = {"pc", "iceJson"}, script =
            "try{pc.addIceCandidate(JSON.parse(iceJson)).catch(function(e){});}catch(e){}")
    private static native void addIceCandidateNative(JSObject pc, String iceJson);

    /**
     * Triggers an ICE restart on the peer connection.
     *
     * <p>Calls {@code pc.restartIce()} inside a try-catch. This causes the browser to
     * re-gather ICE candidates and triggers renegotiation on the next offer/answer
     * exchange.</p>
     *
     * @param pc the native {@code RTCPeerConnection}
     */
    @JSBody(params = {"pc"}, script =
            "try { pc.restartIce(); } catch(e) {}")
    private static native void restartIceNative(JSObject pc);

    /**
     * Returns the label of a native {@code RTCDataChannel}.
     *
     * @param ch the native {@code RTCDataChannel}
     * @return the channel's label string (e.g. {@code "reliable"} or {@code "unreliable"})
     */
    @JSBody(params = {"ch"}, script = "return ch.label;")
    private static native String getChannelLabel(JSObject ch);

    /**
     * Sets the binary type on a native {@code RTCDataChannel}.
     *
     * <p>Typically set to {@code "arraybuffer"} so incoming binary messages arrive as
     * {@code ArrayBuffer} instances rather than Blobs.</p>
     *
     * @param ch   the native {@code RTCDataChannel}
     * @param type the binary type string (e.g. {@code "arraybuffer"})
     */
    @JSBody(params = {"ch", "type"}, script = "ch.binaryType = type;")
    private static native void setChannelBinaryType(JSObject ch, String type);

    /**
     * Adds a DOM event listener to a native {@code RTCDataChannel}.
     *
     * <p>Calls {@code ch.addEventListener(event, listener)} in JavaScript. Used to
     * register handlers for {@code "open"}, {@code "close"}, and {@code "message"} events.</p>
     *
     * @param ch       the native {@code RTCDataChannel}
     * @param event    the event name (e.g. {@code "open"}, {@code "close"}, {@code "message"})
     * @param listener the TeaVM event listener to invoke when the event fires
     */
    @JSBody(params = {"ch", "event", "listener"}, script =
            "ch.addEventListener(event, listener);")
    private static native void addChannelListener(JSObject ch, String event,
                                                   EventListener<Event> listener);

    /**
     * Extracts the {@link ArrayBuffer} payload from a data channel message event.
     *
     * <p>Reads {@code evt.data} from the JavaScript {@code MessageEvent}. The channel's
     * binary type must be set to {@code "arraybuffer"} for this to return a valid buffer.</p>
     *
     * @param evt the JavaScript {@code MessageEvent}
     * @return the message data as an {@link ArrayBuffer}, or {@code null} if not binary
     */
    @JSBody(params = {"evt"}, script = "return evt.data;")
    private static native ArrayBuffer getChannelMessageData(Event evt);

    /**
     * Sends binary data over a native {@code RTCDataChannel}.
     *
     * <p>Checks that the channel's {@code readyState} is {@code "open"} before calling
     * {@code ch.send(data)}. Errors are silently caught to avoid disruption from
     * channels that close between the state check and the send call.</p>
     *
     * @param ch   the native {@code RTCDataChannel}
     * @param data the {@link ArrayBuffer} to send
     */
    @JSBody(params = {"ch", "data"}, script =
            "try { if(ch.readyState === 'open') ch.send(data); } catch(e) {}")
    private static native void sendChannelData(JSObject ch, ArrayBuffer data);

    /**
     * Returns the buffered amount (in bytes) for a native {@code RTCDataChannel}.
     *
     * <p>Reads {@code ch.bufferedAmount} from JavaScript, defaulting to 0 if the
     * property is not available.</p>
     *
     * @param ch the native {@code RTCDataChannel}
     * @return the number of bytes currently buffered for sending, or 0
     */
    @JSBody(params = {"ch"}, script = "return ch.bufferedAmount || 0;")
    private static native int getBufferedAmountNative(JSObject ch);

    /**
     * Returns the ready state of a native {@code RTCDataChannel}.
     *
     * <p>Reads {@code ch.readyState} from JavaScript, defaulting to {@code "closed"}
     * if the property is not available. Possible values are {@code "connecting"},
     * {@code "open"}, {@code "closing"}, and {@code "closed"}.</p>
     *
     * @param ch the native {@code RTCDataChannel}
     * @return the channel's ready state string, or {@code "closed"} if unavailable
     */
    @JSBody(params = {"ch"}, script = "return ch.readyState || 'closed';")
    private static native String getChannelState(JSObject ch);
}
