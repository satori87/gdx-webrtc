package com.github.satori87.gdx.webrtc;

import com.github.satori87.gdx.webrtc.TestHelpers.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import static org.junit.jupiter.api.Assertions.*;

class PeerStateTest {

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
    void getIdReturnsPeerId() {
        assertEquals(5, peer.getId());
    }

    @Test
    void sendReliableSendsWhenConnected() {
        peer.connected = true;
        byte[] data = new byte[]{1, 2, 3};
        peer.sendReliable(data);

        assertTrue(pc.calls.contains("sendData"));
        assertEquals(1, pc.sentData.size());
        assertArrayEquals(data, pc.sentData.get(0));
    }

    @Test
    void sendReliableDoesNothingWhenNotConnected() {
        peer.connected = false;
        peer.sendReliable(new byte[]{1});

        assertFalse(pc.calls.contains("sendData"));
    }

    @Test
    void sendReliableDoesNothingWhenChannelNull() {
        peer.connected = true;
        peer.reliableChannel = null;
        peer.sendReliable(new byte[]{1});

        assertFalse(pc.calls.contains("sendData"));
    }

    @Test
    void sendUnreliableSendsWhenConnected() {
        peer.connected = true;
        pc.bufferedAmount = 0;
        byte[] data = new byte[]{4, 5, 6};
        peer.sendUnreliable(data);

        assertTrue(pc.calls.contains("sendData"));
        assertEquals(1, pc.sentData.size());
    }

    @Test
    void sendUnreliableDropsWhenBufferFull() {
        peer.connected = true;
        pc.bufferedAmount = 100000; // exceeds 65536 default limit

        peer.sendUnreliable(new byte[]{1});

        assertFalse(pc.calls.contains("sendData"));
    }

    @Test
    void sendUnreliableFallsBackToReliableWhenChannelNull() {
        peer.connected = true;
        peer.unreliableChannel = null;
        byte[] data = new byte[]{7, 8, 9};
        peer.sendUnreliable(data);

        // Should fall back to reliable
        assertTrue(pc.calls.contains("sendData"));
        assertEquals(1, pc.sentData.size());
    }

    @Test
    void sendUnreliableFallsBackToReliableWhenNotConnected() {
        peer.connected = false;
        peer.sendUnreliable(new byte[]{1});

        // Not connected, so even fallback doesn't send
        assertFalse(pc.calls.contains("sendData"));
    }

    @Test
    void sendUnreliableRespectsCustomBufferLimit() {
        peer.connected = true;
        client.getConfig().unreliableBufferLimit = 100;
        pc.bufferedAmount = 101;

        peer.sendUnreliable(new byte[]{1});

        assertFalse(pc.calls.contains("sendData"));
    }

    @Test
    void sendUnreliableSendsWhenBufferBelowLimit() {
        peer.connected = true;
        pc.bufferedAmount = 65535; // just below default 65536

        peer.sendUnreliable(new byte[]{1});

        assertTrue(pc.calls.contains("sendData"));
    }

    @Test
    void isConnectedReflectsState() {
        peer.connected = false;
        assertFalse(peer.isConnected());

        peer.connected = true;
        assertTrue(peer.isConnected());
    }

    @Test
    void closeCleansPeerState() {
        peer.connected = true;
        assertFalse(client.getPeers().isEmpty());

        peer.close();

        assertFalse(peer.connected);
        assertNull(peer.peerConnection);
        assertNull(peer.reliableChannel);
        assertNull(peer.unreliableChannel);
        assertTrue(pc.calls.contains("closePeerConnection"));
        assertTrue(client.getPeers().isEmpty());
    }

    @Test
    void closeCancelsTimers() {
        // Set up timers
        client.handleConnectionStateChanged(peer, ConnectionState.DISCONNECTED);
        assertNotNull(peer.disconnectedTimerHandle);

        peer.close();
        assertNull(peer.disconnectedTimerHandle);
        assertNull(peer.failedTimerHandle);
    }

    @Test
    void closeWithNullPeerConnectionDoesNotThrow() {
        peer.peerConnection = null;
        assertDoesNotThrow(new Executable() {
            public void execute() {
                peer.close();
            }
        });
    }

    @Test
    void cancelTimersCancelsDisconnectedTimer() {
        peer.disconnectedTimerHandle = Integer.valueOf(1);
        peer.cancelTimers();
        assertNull(peer.disconnectedTimerHandle);
        assertTrue(sched.calls.contains("cancel"));
    }

    @Test
    void cancelTimersCancelsFailedTimer() {
        peer.failedTimerHandle = Integer.valueOf(2);
        peer.cancelTimers();
        assertNull(peer.failedTimerHandle);
        assertTrue(sched.calls.contains("cancel"));
    }

    @Test
    void cancelTimersWithNullHandlesDoesNothing() {
        peer.disconnectedTimerHandle = null;
        peer.failedTimerHandle = null;
        assertDoesNotThrow(new Executable() {
            public void execute() {
                peer.cancelTimers();
            }
        });
    }
}
