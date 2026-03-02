package studio.whitlock.webrtc.chat.server;

import com.github.satori87.gdx.webrtc.server.WebRTCSignalingServer;

/**
 * Runs the gdx-webrtc signaling server as a relay for the chat example.
 * The signaling server assigns peer IDs, broadcasts join/leave events,
 * and relays SDP/ICE messages between the host and clients.
 */
public class ChatServer {

    public static void main(String[] args) {
        int port = 9090;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port: " + args[0] + ", using default 9090");
            }
        }

        final WebRTCSignalingServer server = new WebRTCSignalingServer(port);

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                System.out.println("Shutting down signaling server...");
                server.stop();
            }
        });

        server.start();
        System.out.println("WebRTC Signaling Server started on port " + port);
        System.out.println("Clients connect to: ws://YOUR_IP:" + port);

        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            server.stop();
        }
    }
}
