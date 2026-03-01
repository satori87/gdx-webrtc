package studio.whitlock.webrtc.chat;

import com.badlogic.gdx.Gdx;
import com.github.satori87.gdx.webrtc.WebRTCClient;
import com.github.satori87.gdx.webrtc.WebRTCClientListener;
import com.github.satori87.gdx.webrtc.WebRTCClients;
import com.github.satori87.gdx.webrtc.WebRTCConfiguration;
import com.github.satori87.gdx.webrtc.WebRTCPeer;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class ConnectionManager implements WebRTCClientListener {

    public interface ChatCallback {
        void onSignalingConnected(int localId);
        void onPeerJoined(int peerId);
        void onPeerLeft(int peerId);
        void onPeerConnected(int peerId);
        void onPeerDisconnected(int peerId);
        void onMessageReceived(String message);
        void onError(String error);
    }

    private final WebRTCClient client;
    private final ChatCallback callback;
    private final Map<Integer, WebRTCPeer> peers = new HashMap<>();

    public ConnectionManager(String signalingUrl, ChatCallback callback) {
        this.callback = callback;

        WebRTCConfiguration config = new WebRTCConfiguration();
        config.signalingServerUrl = signalingUrl;

        this.client = WebRTCClients.newClient(config, this);
    }

    public void connect() {
        client.connect();
    }

    public void disconnect() {
        for (WebRTCPeer peer : peers.values()) {
            peer.close();
        }
        peers.clear();
        client.disconnect();
    }

    public void connectToPeer(int peerId) {
        client.connectToPeer(peerId);
    }

    public int getLocalId() {
        return client.getLocalId();
    }

    public void sendMessage(String text) {
        byte[] data = text.getBytes(StandardCharsets.UTF_8);
        for (WebRTCPeer peer : peers.values()) {
            if (peer.isConnected()) {
                peer.sendReliable(data);
            }
        }
    }

    public boolean isConnected() {
        return !peers.isEmpty();
    }

    @Override
    public void onSignalingConnected(int localId) {
        Gdx.app.postRunnable(() -> callback.onSignalingConnected(localId));
    }

    @Override
    public void onPeerJoined(int peerId) {
        Gdx.app.postRunnable(() -> callback.onPeerJoined(peerId));
    }

    @Override
    public void onPeerLeft(int peerId) {
        Gdx.app.postRunnable(() -> callback.onPeerLeft(peerId));
    }

    @Override
    public void onConnected(WebRTCPeer peer) {
        Gdx.app.postRunnable(() -> {
            peers.put(peer.getId(), peer);
            callback.onPeerConnected(peer.getId());
        });
    }

    @Override
    public void onDisconnected(WebRTCPeer peer) {
        Gdx.app.postRunnable(() -> {
            peers.remove(peer.getId());
            callback.onPeerDisconnected(peer.getId());
        });
    }

    @Override
    public void onMessage(WebRTCPeer peer, byte[] data, boolean reliable) {
        String text = new String(data, StandardCharsets.UTF_8);
        Gdx.app.postRunnable(() -> callback.onMessageReceived("Peer " + peer.getId() + ": " + text));
    }

    @Override
    public void onError(String error) {
        Gdx.app.postRunnable(() -> callback.onError(error));
    }
}
