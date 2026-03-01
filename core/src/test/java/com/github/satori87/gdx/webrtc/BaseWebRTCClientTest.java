package com.github.satori87.gdx.webrtc;

import com.github.satori87.gdx.webrtc.TestHelpers.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BaseWebRTCClientTest {

    MockPeerConnectionProvider pc;
    MockSignalingProvider sig;
    MockScheduler sched;
    MockListener listener;
    BaseWebRTCClient client;

    @BeforeEach
    void setUp() {
        pc = new MockPeerConnectionProvider();
        sig = new MockSignalingProvider();
        sched = new MockScheduler();
        listener = new MockListener();
        client = TestHelpers.createClient(pc, sig, sched, listener);
    }

    @Test
    void connectCallsSignalingConnect() {
        client.connect();
        assertTrue(sig.calls.contains("connect"));
        assertTrue(sig.isOpen());
    }

    @Test
    void disconnectClosesSignalingAndScheduler() {
        client.connect();
        client.disconnect();
        assertTrue(sig.calls.contains("close"));
        assertTrue(sched.calls.contains("shutdown"));
        assertEquals(-1, client.getLocalId());
    }

    @Test
    void disconnectClosesPeers() {
        client.connect();
        sig.simulateMessage(new SignalMessage(SignalMessage.TYPE_WELCOME, 0, 0, "1"));
        sig.simulateMessage(new SignalMessage(SignalMessage.TYPE_CONNECT_REQUEST, 5, 1, ""));
        assertFalse(client.getPeers().isEmpty());

        client.disconnect();
        assertTrue(client.getPeers().isEmpty());
    }

    @Test
    void isConnectedToSignalingDelegatesToProvider() {
        assertFalse(client.isConnectedToSignaling());
        client.connect();
        assertTrue(client.isConnectedToSignaling());
    }

    @Test
    void connectToPeerSendsConnectRequest() {
        client.connect();
        sig.simulateMessage(new SignalMessage(SignalMessage.TYPE_WELCOME, 0, 0, "1"));
        client.connectToPeer(5);

        assertEquals(1, sig.sentMessages.size());
        SignalMessage sent = sig.sentMessages.get(0);
        assertEquals(SignalMessage.TYPE_CONNECT_REQUEST, sent.type);
        assertEquals(1, sent.source);
        assertEquals(5, sent.target);
    }

    @Test
    void setListenerUpdatesListener() {
        MockListener newListener = new MockListener();
        client.setListener(newListener);
        client.connect();
        sig.simulateMessage(new SignalMessage(SignalMessage.TYPE_WELCOME, 0, 0, "7"));
        assertEquals(7, newListener.signalingConnectedId);
        assertEquals(-1, listener.signalingConnectedId);
    }

    @Test
    void getLocalIdReturnsMinusOneBeforeConnect() {
        assertEquals(-1, client.getLocalId());
    }

    @Test
    void getLocalIdReturnsAssignedIdAfterWelcome() {
        client.connect();
        sig.simulateMessage(new SignalMessage(SignalMessage.TYPE_WELCOME, 0, 0, "42"));
        assertEquals(42, client.getLocalId());
    }

    @Test
    void nullListenerDoesNotThrow() {
        client.setListener(null);
        client.connect();
        assertDoesNotThrow(() ->
            sig.simulateMessage(new SignalMessage(SignalMessage.TYPE_WELCOME, 0, 0, "1"))
        );
        assertDoesNotThrow(() ->
            sig.simulateMessage(new SignalMessage(SignalMessage.TYPE_ERROR, 0, 0, "err"))
        );
        assertDoesNotThrow(() ->
            sig.simulateMessage(new SignalMessage(SignalMessage.TYPE_PEER_JOINED, 5, 0, ""))
        );
    }
}
