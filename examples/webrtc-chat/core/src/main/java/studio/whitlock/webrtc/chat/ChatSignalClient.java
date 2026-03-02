package studio.whitlock.webrtc.chat;

/**
 * Platform-agnostic WebSocket client interface for signaling.
 *
 * <p>Each platform provides an implementation: Java-WebSocket for
 * desktop/Android/iOS, browser WebSocket API for TeaVM.</p>
 */
public interface ChatSignalClient {

    /** Listener for WebSocket events. */
    interface Listener {
        void onOpen();
        void onMessage(String text);
        void onClose(String reason);
        void onError(String error);
    }

    /**
     * Opens a WebSocket connection to the given URL.
     *
     * @param url      the WebSocket URL (e.g. "ws://localhost:9090")
     * @param listener callbacks for WebSocket events
     */
    void connect(String url, Listener listener);

    /**
     * Sends a text message over the WebSocket.
     * No-op if not connected.
     *
     * @param text the message to send
     */
    void send(String text);

    /** Closes the WebSocket connection. */
    void close();

    /** Returns whether the WebSocket is currently open. */
    boolean isOpen();
}
