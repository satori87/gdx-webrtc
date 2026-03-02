package studio.whitlock.webrtc.chat;

/**
 * Platform-agnostic abstraction for a WebSocket signaling server.
 * Used by EmbeddedChatServer to accept client connections and relay signaling messages.
 */
public interface ChatSignalServer {

    interface Listener {
        void onClientConnected(int clientId);
        void onClientMessage(int clientId, String text);
        void onClientDisconnected(int clientId);
    }

    void start(int port, Listener listener);
    void stop();
    void send(int clientId, String text);
}
