package com.github.satori87.gdx.webrtc.teavm;

import com.github.satori87.gdx.webrtc.SignalMessage;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;
import org.teavm.jso.dom.events.Event;
import org.teavm.jso.dom.events.EventListener;
import org.teavm.jso.typedarrays.ArrayBuffer;

/**
 * Browser WebSocket client for connecting to the signaling server (TeaVM).
 */
class TeaVMSignalingClient {

    interface Listener {
        void onSignalingOpen();
        void onSignalingMessage(SignalMessage msg);
        void onSignalingClose(String reason);
        void onSignalingError(String error);
    }

    private JSObject ws;
    private final Listener listener;
    private boolean open;

    TeaVMSignalingClient(Listener listener) {
        this.listener = listener;
    }

    void connect(String url) {
        ws = createWebSocket(url);

        addEventListener(ws, "open", new EventListener<Event>() {
            public void handleEvent(Event evt) {
                open = true;
                listener.onSignalingOpen();
            }
        });

        addEventListener(ws, "message", new EventListener<Event>() {
            public void handleEvent(Event evt) {
                String data = getMessageDataString(evt);
                if (data != null) {
                    SignalMessage msg = SignalMessage.fromJson(data);
                    if (msg != null) {
                        listener.onSignalingMessage(msg);
                    }
                }
            }
        });

        addEventListener(ws, "close", new EventListener<Event>() {
            public void handleEvent(Event evt) {
                open = false;
                listener.onSignalingClose("WebSocket closed");
            }
        });

        addEventListener(ws, "error", new EventListener<Event>() {
            public void handleEvent(Event evt) {
                listener.onSignalingError("WebSocket error");
            }
        });
    }

    void send(SignalMessage msg) {
        if (ws != null && open) {
            sendText(ws, msg.toJson());
        }
    }

    void close() {
        if (ws != null) {
            closeWebSocket(ws);
            ws = null;
            open = false;
        }
    }

    boolean isOpen() {
        return open;
    }

    // --- Native methods ---

    @JSBody(params = {"url"}, script = "return new WebSocket(url);")
    private static native JSObject createWebSocket(String url);

    @JSBody(params = {"ws"}, script = "try { ws.close(); } catch(e) {}")
    private static native void closeWebSocket(JSObject ws);

    @JSBody(params = {"ws", "text"}, script =
            "try { if(ws.readyState === 1) ws.send(text); } catch(e) {}")
    private static native void sendText(JSObject ws, String text);

    @JSBody(params = {"obj", "event", "listener"}, script =
            "obj.addEventListener(event, listener);")
    private static native void addEventListener(JSObject obj, String event, EventListener<Event> listener);

    @JSBody(params = {"evt"}, script = "return typeof evt.data === 'string' ? evt.data : null;")
    private static native String getMessageDataString(Event evt);
}
