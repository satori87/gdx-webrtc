package studio.whitlock.webrtc.chat.server;

import com.github.satori87.gdx.webrtc.server.WebRTCSignalingServer;

public class SignalServerLauncher {

    public static void main(String[] args) {
        int port = 9090;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port: " + args[0] + ", using default 9090");
            }
        }

        WebRTCSignalingServer server = new WebRTCSignalingServer(port);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down signaling server...");
            server.stop();
        }));

        server.start();
        System.out.println("WebRTC Signaling Server started on port " + port);
        System.out.println("Clients should connect to: ws://YOUR_IP:" + port);

        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            server.stop();
        }
    }
}
