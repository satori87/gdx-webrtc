package com.github.satori87.gdx.webrtc;

import com.github.satori87.gdx.webrtc.TestHelpers.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IceStateMachineTest {

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
    void connectedResetsStateAndCancelsTimers() {
        // Set up some state that CONNECTED should reset
        peer.iceClosedOrFailed = true;
        peer.disconnectedAtMs = 12345L;
        peer.iceRestartAttempts = 2;

        // Mark channel as open so it doesn't trigger disconnect
        pc.channelOpen = true;

        client.handleConnectionStateChanged(peer, ConnectionState.CONNECTED);

        assertFalse(peer.iceClosedOrFailed);
        assertEquals(0, peer.disconnectedAtMs);
        assertEquals(0, peer.iceRestartAttempts);
    }

    @Test
    void connectedWithClosedChannelFiresDisconnect() {
        pc.channelOpen = false;
        peer.connected = true;

        client.handleConnectionStateChanged(peer, ConnectionState.CONNECTED);

        assertFalse(peer.connected);
        assertEquals(1, listener.disconnectedPeers.size());
    }

    @Test
    void disconnectedSchedulesIceRestart() {
        client.handleConnectionStateChanged(peer, ConnectionState.DISCONNECTED);

        assertTrue(peer.disconnectedAtMs > 0);
        assertEquals(1, sched.tasks.size());
        // Default delay is 3500ms
        assertEquals(3500, sched.tasks.get(0).delayMs);
    }

    @Test
    void disconnectedTimerRestartsIce() {
        client.handleConnectionStateChanged(peer, ConnectionState.DISCONNECTED);

        sched.runLast();

        assertTrue(pc.calls.contains("restartIce"));
    }

    @Test
    void disconnectedTimerCancelledByConnected() {
        client.handleConnectionStateChanged(peer, ConnectionState.DISCONNECTED);
        assertNotNull(peer.disconnectedTimerHandle);

        // Now CONNECTED arrives before timer fires
        pc.channelOpen = true;
        client.handleConnectionStateChanged(peer, ConnectionState.CONNECTED);

        // Timer should be cancelled
        assertTrue(sched.calls.contains("cancel"));
    }

    @Test
    void disconnectedTimerDoesNothingAfterRecovery() {
        client.handleConnectionStateChanged(peer, ConnectionState.DISCONNECTED);
        long originalStamp = peer.disconnectedAtMs;

        // Recover (resets disconnectedAtMs)
        pc.channelOpen = true;
        client.handleConnectionStateChanged(peer, ConnectionState.CONNECTED);
        assertEquals(0, peer.disconnectedAtMs);

        // Even if we run the old task, the stamp check should prevent restart
        pc.calls.clear();
        sched.runAll();
        assertFalse(pc.calls.contains("restartIce"));
    }

    @Test
    void failedIncrementsAttemptsAndSchedulesBackoff() {
        client.handleConnectionStateChanged(peer, ConnectionState.FAILED);

        assertEquals(1, peer.iceRestartAttempts);
        assertTrue(peer.iceClosedOrFailed);
        assertEquals(1, sched.tasks.size());
        // First backoff: 2000ms * 2^0 = 2000ms
        assertEquals(2000, sched.tasks.get(0).delayMs);
    }

    @Test
    void failedExponentialBackoff() {
        // First failure: 2000ms
        client.handleConnectionStateChanged(peer, ConnectionState.FAILED);
        assertEquals(2000, sched.tasks.get(sched.tasks.size() - 1).delayMs);

        // Second failure: 4000ms
        client.handleConnectionStateChanged(peer, ConnectionState.FAILED);
        assertEquals(4000, sched.tasks.get(sched.tasks.size() - 1).delayMs);

        // Third failure: 8000ms
        client.handleConnectionStateChanged(peer, ConnectionState.FAILED);
        assertEquals(8000, sched.tasks.get(sched.tasks.size() - 1).delayMs);
    }

    @Test
    void failedMaxAttemptsExceededFiresDisconnect() {
        peer.connected = true;

        // Default max is 3 attempts
        for (int i = 0; i < 3; i++) {
            client.handleConnectionStateChanged(peer, ConnectionState.FAILED);
        }
        // Should still be connected after 3 attempts (scheduling retries)
        assertTrue(peer.connected);

        // 4th attempt exceeds max
        client.handleConnectionStateChanged(peer, ConnectionState.FAILED);
        assertFalse(peer.connected);
        assertEquals(1, listener.disconnectedPeers.size());
    }

    @Test
    void failedTimerRestartsIce() {
        client.handleConnectionStateChanged(peer, ConnectionState.FAILED);

        sched.runLast();

        assertTrue(pc.calls.contains("restartIce"));
    }

    @Test
    void closedFiresDisconnect() {
        peer.connected = true;

        client.handleConnectionStateChanged(peer, ConnectionState.CLOSED);

        assertTrue(peer.iceClosedOrFailed);
        assertEquals(0, peer.disconnectedAtMs);
        assertFalse(peer.connected);
        assertEquals(1, listener.disconnectedPeers.size());
    }

    @Test
    void closedCancelsTimers() {
        // Set up a disconnected timer
        client.handleConnectionStateChanged(peer, ConnectionState.DISCONNECTED);
        assertNotNull(peer.disconnectedTimerHandle);

        // CLOSED should cancel it
        client.handleConnectionStateChanged(peer, ConnectionState.CLOSED);
        assertNull(peer.disconnectedTimerHandle);
        assertNull(peer.failedTimerHandle);
    }

    @Test
    void failedCancelsExistingTimers() {
        // Schedule a disconnected timer
        client.handleConnectionStateChanged(peer, ConnectionState.DISCONNECTED);
        assertNotNull(peer.disconnectedTimerHandle);

        // FAILED should cancel the disconnected timer before scheduling backoff
        client.handleConnectionStateChanged(peer, ConnectionState.FAILED);
        assertTrue(sched.calls.contains("cancel"));
    }

    @Test
    void customIceRestartDelayIsUsed() {
        client.getConfig().iceRestartDelayMs = 5000;

        client.handleConnectionStateChanged(peer, ConnectionState.DISCONNECTED);

        assertEquals(5000, sched.tasks.get(sched.tasks.size() - 1).delayMs);
    }

    @Test
    void customBackoffBaseIsUsed() {
        client.getConfig().iceBackoffBaseMs = 1000;

        client.handleConnectionStateChanged(peer, ConnectionState.FAILED);

        // 1000 * 2^0 = 1000
        assertEquals(1000, sched.tasks.get(sched.tasks.size() - 1).delayMs);
    }

    @Test
    void customMaxAttemptsIsUsed() {
        client.getConfig().maxIceRestartAttempts = 1;
        peer.connected = true;

        // First failure (attempt 1) - still schedules retry
        client.handleConnectionStateChanged(peer, ConnectionState.FAILED);
        assertTrue(peer.connected);

        // Second failure (attempt 2) exceeds max of 1
        client.handleConnectionStateChanged(peer, ConnectionState.FAILED);
        assertFalse(peer.connected);
    }

    @Test
    void disconnectedReplacesExistingTimer() {
        client.handleConnectionStateChanged(peer, ConnectionState.DISCONNECTED);
        assertEquals(1, sched.tasks.size());

        // Second DISCONNECTED should cancel old timer and schedule new one
        client.handleConnectionStateChanged(peer, ConnectionState.DISCONNECTED);
        assertTrue(sched.calls.contains("cancel"));
        assertEquals(2, sched.tasks.size());
    }
}
