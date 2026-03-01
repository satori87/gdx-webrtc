package com.github.satori87.gdx.webrtc.teavm;

import com.github.satori87.gdx.webrtc.SignalMessage;
import com.github.satori87.gdx.webrtc.SignalingEventHandler;
import com.github.satori87.gdx.webrtc.SignalingProvider;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSObject;
import org.teavm.jso.dom.events.Event;
import org.teavm.jso.dom.events.EventListener;

/**
 * Browser implementation of {@link SignalingProvider} using the native {@code WebSocket} API
 * via TeaVM's JSO (JavaScript Object) interop layer.
 *
 * <p>This class manages a single WebSocket connection to the signaling server for exchanging
 * {@link SignalMessage} instances (SDP offers/answers, ICE candidates, peer lists, etc.)
 * between browser-based WebRTC clients.</p>
 *
 * <p>All WebSocket operations -- creating the socket, sending text frames, closing the
 * connection, and listening for events -- are delegated to inline JavaScript via
 * {@code @JSBody}-annotated native methods. Event listeners are registered using the
 * browser's {@code addEventListener} API through TeaVM's {@link EventListener} interface.</p>
 *
 * <h3>Connection Lifecycle</h3>
 * <ol>
 *   <li>{@link #connect(String, SignalingEventHandler)} creates a new {@code WebSocket}
 *       and registers open, message, close, and error event handlers</li>
 *   <li>Incoming text messages are parsed via {@link SignalMessage#fromJson(String)} and
 *       dispatched to the {@link SignalingEventHandler}</li>
 *   <li>{@link #send(SignalMessage)} serializes messages to JSON and sends them over
 *       the WebSocket (only if the socket is in the {@code OPEN} ready state)</li>
 *   <li>{@link #close()} closes the WebSocket and resets the internal state</li>
 * </ol>
 *
 * @see SignalingProvider
 * @see SignalMessage
 * @see TeaVMWebRTCFactory
 */
public class TeaVMSignalingProvider implements SignalingProvider {

    /** The native browser {@code WebSocket} object, or {@code null} if not connected. */
    private JSObject ws;

    /** Whether the WebSocket is currently in the open state. */
    private boolean open;

    /**
     * Opens a WebSocket connection to the signaling server at the specified URL.
     *
     * <p>Creates a native browser {@code WebSocket} via JavaScript and registers four
     * event listeners:</p>
     * <ul>
     *   <li><b>open</b> -- sets the internal open flag and calls
     *       {@link SignalingEventHandler#onOpen()}</li>
     *   <li><b>message</b> -- extracts the text data from the event, parses it as a
     *       {@link SignalMessage}, and calls {@link SignalingEventHandler#onMessage(SignalMessage)}
     *       (silently ignores messages that fail to parse)</li>
     *   <li><b>close</b> -- clears the internal open flag and calls
     *       {@link SignalingEventHandler#onClose(String)}</li>
     *   <li><b>error</b> -- calls {@link SignalingEventHandler#onError(String)} with a
     *       generic error description</li>
     * </ul>
     *
     * @param url     the WebSocket URL of the signaling server (e.g. {@code "wss://example.com:9090"})
     * @param handler callbacks for connection lifecycle and incoming signaling messages
     */
    public void connect(String url, final SignalingEventHandler handler) {
        ws = createWebSocket(url);

        addEventListener(ws, "open", new EventListener<Event>() {
            public void handleEvent(Event evt) {
                open = true;
                handler.onOpen();
            }
        });

        addEventListener(ws, "message", new EventListener<Event>() {
            public void handleEvent(Event evt) {
                String data = getMessageDataString(evt);
                if (data != null) {
                    SignalMessage msg = SignalMessage.fromJson(data);
                    if (msg != null) {
                        handler.onMessage(msg);
                    }
                }
            }
        });

        addEventListener(ws, "close", new EventListener<Event>() {
            public void handleEvent(Event evt) {
                open = false;
                handler.onClose("WebSocket closed");
            }
        });

        addEventListener(ws, "error", new EventListener<Event>() {
            public void handleEvent(Event evt) {
                handler.onError("WebSocket error");
            }
        });
    }

    /**
     * Sends a signaling message over the WebSocket connection.
     *
     * <p>Serializes the message to JSON via {@link SignalMessage#toJson()} and sends it
     * as a text frame. The message is silently dropped if the WebSocket is not currently
     * open (i.e., {@link #ws} is {@code null} or {@link #open} is {@code false}).</p>
     *
     * @param msg the signaling message to send
     */
    public void send(SignalMessage msg) {
        if (ws != null && open) {
            sendText(ws, msg.toJson());
        }
    }

    /**
     * Closes the WebSocket connection to the signaling server.
     *
     * <p>Calls {@code ws.close()} via JavaScript and resets the internal state.
     * Safe to call even if the WebSocket is already closed or was never opened.
     * After this call, {@link #isOpen()} returns {@code false}.</p>
     */
    public void close() {
        if (ws != null) {
            closeWebSocket(ws);
            ws = null;
            open = false;
        }
    }

    /**
     * Returns whether the WebSocket connection is currently open.
     *
     * @return {@code true} if the WebSocket has been opened and has not yet been closed,
     *         {@code false} otherwise
     */
    public boolean isOpen() {
        return open;
    }

    // --- Native methods ---

    /**
     * Creates a native browser {@code WebSocket} connected to the given URL.
     *
     * @param url the WebSocket URL to connect to
     * @return the native {@code WebSocket} object as a JSObject
     */
    @JSBody(params = {"url"}, script = "return new WebSocket(url);")
    private static native JSObject createWebSocket(String url);

    /**
     * Closes the native browser {@code WebSocket}.
     *
     * <p>Calls {@code ws.close()} inside a try-catch to silently handle sockets that
     * are already closed or in an invalid state.</p>
     *
     * @param ws the native {@code WebSocket} to close
     */
    @JSBody(params = {"ws"}, script = "try { ws.close(); } catch(e) {}")
    private static native void closeWebSocket(JSObject ws);

    /**
     * Sends a text message over the native browser {@code WebSocket}.
     *
     * <p>Checks that the socket's {@code readyState} is 1 ({@code OPEN}) before calling
     * {@code ws.send(text)}. Errors are silently caught to avoid disruption from sockets
     * that close between the state check and the send call.</p>
     *
     * @param ws   the native {@code WebSocket}
     * @param text the text message to send (typically a JSON-serialized {@link SignalMessage})
     */
    @JSBody(params = {"ws", "text"}, script =
            "try { if(ws.readyState === 1) ws.send(text); } catch(e) {}")
    private static native void sendText(JSObject ws, String text);

    /**
     * Adds a DOM event listener to a JavaScript object.
     *
     * <p>Calls {@code obj.addEventListener(event, listener)} in JavaScript. Used to
     * register handlers for {@code "open"}, {@code "message"}, {@code "close"}, and
     * {@code "error"} events on the WebSocket.</p>
     *
     * @param obj      the JavaScript object to attach the listener to
     * @param event    the event name (e.g. {@code "open"}, {@code "message"}, {@code "close"}, {@code "error"})
     * @param listener the TeaVM event listener to invoke when the event fires
     */
    @JSBody(params = {"obj", "event", "listener"}, script =
            "obj.addEventListener(event, listener);")
    private static native void addEventListener(JSObject obj, String event,
                                                 EventListener<Event> listener);

    /**
     * Extracts the text data from a WebSocket message event.
     *
     * <p>Reads {@code evt.data} from the JavaScript {@code MessageEvent} and returns it
     * only if it is a string. Returns {@code null} for binary messages or other non-string
     * data types, since signaling messages are always text-based JSON.</p>
     *
     * @param evt the JavaScript {@code MessageEvent}
     * @return the message text, or {@code null} if the data is not a string
     */
    @JSBody(params = {"evt"}, script =
            "return typeof evt.data === 'string' ? evt.data : null;")
    private static native String getMessageDataString(Event evt);
}
