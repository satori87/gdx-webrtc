package studio.whitlock.webrtc.chat.teavm;

import org.teavm.jso.JSBody;
import org.teavm.jso.JSObject;
import org.teavm.jso.dom.events.Event;
import org.teavm.jso.dom.events.EventListener;
import studio.whitlock.webrtc.chat.ChatSignalClient;

/** Browser implementation of {@link ChatSignalClient} using native WebSocket API via TeaVM JSO. */
public class BrowserSignalClient implements ChatSignalClient {

    private JSObject ws;
    private boolean open;

    public void connect(String url, final Listener listener) {
        ws = createWebSocket(url);

        addEventListener(ws, "open", new EventListener<Event>() {
            public void handleEvent(Event evt) {
                open = true;
                listener.onOpen();
            }
        });

        addEventListener(ws, "message", new EventListener<Event>() {
            public void handleEvent(Event evt) {
                String data = getMessageDataString(evt);
                if (data != null) {
                    listener.onMessage(data);
                }
            }
        });

        addEventListener(ws, "close", new EventListener<Event>() {
            public void handleEvent(Event evt) {
                open = false;
                listener.onClose("WebSocket closed");
            }
        });

        addEventListener(ws, "error", new EventListener<Event>() {
            public void handleEvent(Event evt) {
                listener.onError("WebSocket error");
            }
        });
    }

    public void send(String text) {
        if (ws != null && open) {
            sendText(ws, text);
        }
    }

    public void close() {
        if (ws != null) {
            closeWebSocket(ws);
            ws = null;
            open = false;
        }
    }

    public boolean isOpen() {
        return open;
    }

    @JSBody(params = {"url"}, script = "return new WebSocket(url);")
    private static native JSObject createWebSocket(String url);

    @JSBody(params = {"ws"}, script = "try { ws.close(); } catch(e) {}")
    private static native void closeWebSocket(JSObject ws);

    @JSBody(params = {"ws", "text"}, script =
            "try { if(ws.readyState === 1) ws.send(text); } catch(e) {}")
    private static native void sendText(JSObject ws, String text);

    @JSBody(params = {"obj", "event", "listener"}, script =
            "obj.addEventListener(event, listener);")
    private static native void addEventListener(JSObject obj, String event,
                                                 EventListener<Event> listener);

    @JSBody(params = {"evt"}, script =
            "return typeof evt.data === 'string' ? evt.data : null;")
    private static native String getMessageDataString(Event evt);
}
