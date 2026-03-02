package studio.whitlock.webrtc.chat.lwjgl3;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import studio.whitlock.webrtc.chat.ChatSignalServer;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class DesktopSignalServer implements ChatSignalServer {

    private WebSocketServer wsServer;
    private final Map<WebSocket, Integer> wsToId = new ConcurrentHashMap<WebSocket, Integer>();
    private final Map<Integer, WebSocket> idToWs = new ConcurrentHashMap<Integer, WebSocket>();
    private final AtomicInteger nextId = new AtomicInteger(1);

    @Override
    public void start(final int port, final Listener listener) {
        wsServer = new WebSocketServer(new InetSocketAddress(port)) {
            @Override
            public void onOpen(WebSocket conn, ClientHandshake handshake) {
                int id = nextId.getAndIncrement();
                wsToId.put(conn, id);
                idToWs.put(id, conn);
                listener.onClientConnected(id);
            }

            @Override
            public void onMessage(WebSocket conn, String message) {
                Integer id = wsToId.get(conn);
                if (id != null) {
                    listener.onClientMessage(id, message);
                }
            }

            @Override
            public void onClose(WebSocket conn, int code, String reason, boolean remote) {
                Integer id = wsToId.remove(conn);
                if (id != null) {
                    idToWs.remove(id);
                    listener.onClientDisconnected(id);
                }
            }

            @Override
            public void onError(WebSocket conn, Exception ex) {
                // Connection errors are handled via onClose
            }

            @Override
            public void onStart() {
            }
        };
        wsServer.start();
    }

    @Override
    public void stop() {
        if (wsServer != null) {
            try {
                wsServer.stop(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        wsToId.clear();
        idToWs.clear();
    }

    @Override
    public void send(int clientId, String text) {
        WebSocket ws = idToWs.get(clientId);
        if (ws != null && ws.isOpen()) {
            ws.send(text);
        }
    }
}
