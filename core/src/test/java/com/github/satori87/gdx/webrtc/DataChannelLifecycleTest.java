package com.github.satori87.gdx.webrtc;

import com.github.satori87.gdx.webrtc.TestHelpers.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import static org.junit.jupiter.api.Assertions.*;

class DataChannelLifecycleTest {

    MockPeerConnectionProvider pc;
    MockSignalingProvider sig;
    MockScheduler sched;
    MockListener listener;
    BaseWebRTCClient client;
    BaseWebRTCClient.PeerState peer;

    @BeforeEach
    void setUp() {
        pc = new MockPeerConnectionProvider();
        sig = new MockSignalingProvider();
        sched = new MockScheduler();
        listener = new MockListener();
        client = TestHelpers.createConnectedClient(pc, sig, sched, listener);
        // Create a peer via connect request
        sig.simulateMessage(new SignalMessage(SignalMessage.TYPE_CONNECT_REQUEST, 5, 42, ""));
        peer = (BaseWebRTCClient.PeerState) client.getPeers().get(Integer.valueOf(5));
        assertNotNull(peer);
    }

    @Test
    void reliableOpenSetsConnectedAndNotifiesListener() {
        assertNotNull(pc.lastDcHandler);
        pc.lastDcHandler.onReliableOpen();

        assertTrue(peer.connected);
        assertEquals(1, listener.connectedPeers.size());
        assertEquals(5, listener.connectedPeers.get(0).getId());
    }

    @Test
    void reliableCloseSetsDisconnectedAndNotifiesListener() {
        peer.connected = true;
        pc.lastDcHandler.onReliableClose();

        assertFalse(peer.connected);
        assertEquals(1, listener.disconnectedPeers.size());
        assertEquals(5, listener.disconnectedPeers.get(0).getId());
    }

    @Test
    void unreliableOpenDoesNotSetConnected() {
        peer.connected = false;
        pc.lastDcHandler.onUnreliableOpen();

        assertFalse(peer.connected);
        assertEquals(0, listener.connectedPeers.size());
    }

    @Test
    void unreliableCloseDoesNotSetDisconnected() {
        peer.connected = true;
        pc.lastDcHandler.onUnreliableClose();

        assertTrue(peer.connected);
        assertEquals(0, listener.disconnectedPeers.size());
    }

    @Test
    void messageDeliveredToListener() {
        byte[] data = new byte[]{10, 20, 30};
        pc.lastDcHandler.onMessage(data, true);

        assertEquals(1, listener.receivedData.size());
        assertArrayEquals(data, listener.receivedData.get(0));
        assertTrue(listener.receivedReliable.get(0).booleanValue());
    }

    @Test
    void unreliableMessageDeliveredToListener() {
        byte[] data = new byte[]{40, 50};
        pc.lastDcHandler.onMessage(data, false);

        assertEquals(1, listener.receivedData.size());
        assertFalse(listener.receivedReliable.get(0).booleanValue());
    }

    @Test
    void messageWithNullListenerDoesNotThrow() {
        client.setListener(null);
        assertDoesNotThrow(new Executable() {
            public void execute() {
                pc.lastDcHandler.onMessage(new byte[]{1}, true);
            }
        });
    }

    @Test
    void reliableOpenWithNullListenerDoesNotThrow() {
        client.setListener(null);
        assertDoesNotThrow(new Executable() {
            public void execute() {
                pc.lastDcHandler.onReliableOpen();
            }
        });
        assertTrue(peer.connected);
    }

    @Test
    void reliableCloseWithNullListenerDoesNotThrow() {
        client.setListener(null);
        peer.connected = true;
        assertDoesNotThrow(new Executable() {
            public void execute() {
                pc.lastDcHandler.onReliableClose();
            }
        });
        assertFalse(peer.connected);
    }

    @Test
    void onDataChannelSetsReliableChannel() {
        assertNotNull(pc.lastPcHandler);
        Object newChannel = new Object();
        pc.lastPcHandler.onDataChannel(newChannel, "reliable");

        assertSame(newChannel, peer.reliableChannel);
        assertTrue(pc.calls.contains("setupReceivedChannel:reliable"));
    }

    @Test
    void onDataChannelSetsUnreliableChannel() {
        Object newChannel = new Object();
        pc.lastPcHandler.onDataChannel(newChannel, "unreliable");

        assertSame(newChannel, peer.unreliableChannel);
        assertTrue(pc.calls.contains("setupReceivedChannel:unreliable"));
    }

    @Test
    void onDataChannelIgnoresUnknownLabel() {
        Object newChannel = new Object();
        int callsBefore = pc.calls.size();
        pc.lastPcHandler.onDataChannel(newChannel, "unknown");

        // No setupReceivedChannel call should be made
        assertFalse(pc.calls.contains("setupReceivedChannel:reliable"));
        assertFalse(pc.calls.contains("setupReceivedChannel:unreliable"));
    }

    @Test
    void iceCandidateSendsSignalingMessage() {
        assertNotNull(pc.lastPcHandler);
        pc.lastPcHandler.onIceCandidate("{\"candidate\":\"test\"}");

        SignalMessage lastSent = sig.sentMessages.get(sig.sentMessages.size() - 1);
        assertEquals(SignalMessage.TYPE_ICE, lastSent.type);
        assertEquals(42, lastSent.source);
        assertEquals(5, lastSent.target);
        assertEquals("{\"candidate\":\"test\"}", lastSent.data);
    }

    @Test
    void connectRequestAssignsChannelsFromPair() {
        // The setUp already created a peer via connect request
        // Verify channels were assigned from the ChannelPair
        assertNotNull(peer.reliableChannel);
        assertNotNull(peer.unreliableChannel);
        assertSame(pc.reliableChannel, peer.reliableChannel);
        assertSame(pc.unreliableChannel, peer.unreliableChannel);
    }

    @Test
    void offerAnswerFlowDoesNotCreateChannels() {
        // When receiving an offer (answerer), data channels come via ondatachannel
        sig.simulateMessage(new SignalMessage(SignalMessage.TYPE_OFFER, 7, 42, "offer-sdp"));
        BaseWebRTCClient.PeerState answererPeer =
                (BaseWebRTCClient.PeerState) client.getPeers().get(Integer.valueOf(7));
        assertNotNull(answererPeer);

        // Answerer should NOT have createDataChannels called — channels come via ondatachannel
        // Actually, the current impl doesn't call createDataChannels for offer handler
        // The peer's channels are null until ondatachannel fires
        assertNull(answererPeer.reliableChannel);
        assertNull(answererPeer.unreliableChannel);
    }
}
