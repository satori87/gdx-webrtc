package com.github.satori87.gdx.webrtc.transport;

import com.github.satori87.gdx.webrtc.ConnectionState;
import com.github.satori87.gdx.webrtc.DataChannelEventHandler;
import com.github.satori87.gdx.webrtc.TestHelpers;
import com.github.satori87.gdx.webrtc.WebRTCConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link BaseWebRTCClientTransport}.
 */
class BaseWebRTCClientTransportTest {

    private TestHelpers.MockPeerConnectionProvider pc;
    private TestHelpers.MockScheduler sched;
    private TransportTestHelpers.MockTransportListener listener;
    private BaseWebRTCClientTransport transport;

    @BeforeEach
    void setUp() {
        pc = new TestHelpers.MockPeerConnectionProvider();
        sched = new TestHelpers.MockScheduler();
        listener = new TransportTestHelpers.MockTransportListener();
        transport = TransportTestHelpers.createClientTransport(pc, sched, listener);
    }

    // --- Connection flow ---

    @Test
    void connectWithOfferInitializesFactory() {
        TransportTestHelpers.MockClientSignalCallback cb = new TransportTestHelpers.MockClientSignalCallback();
        transport.connectWithOffer("offer-sdp", cb);

        assertTrue(pc.initialized);
        assertTrue(pc.calls.contains("initialize"));
    }

    @Test
    void connectWithOfferCreatesPeerConnection() {
        TransportTestHelpers.MockClientSignalCallback cb = new TransportTestHelpers.MockClientSignalCallback();
        transport.connectWithOffer("offer-sdp", cb);

        assertTrue(pc.calls.contains("createPeerConnection"));
    }

    @Test
    void connectWithOfferHandlesOffer() {
        TransportTestHelpers.MockClientSignalCallback cb = new TransportTestHelpers.MockClientSignalCallback();
        transport.connectWithOffer("offer-sdp", cb);

        assertTrue(pc.calls.contains("handleOffer"));
        assertNotNull(pc.lastHandleOfferCallback);
    }

    @Test
    void connectWithOfferSendsAnswerViaCallback() {
        TransportTestHelpers.MockClientSignalCallback cb = new TransportTestHelpers.MockClientSignalCallback();
        transport.connectWithOffer("offer-sdp", cb);

        pc.lastHandleOfferCallback.onSuccess("answer-sdp");

        assertEquals(1, cb.answers.size());
        assertEquals("answer-sdp", cb.answers.get(0));
    }

    @Test
    void connectWithOfferHandshakeFailureReportsError() {
        TransportTestHelpers.MockClientSignalCallback cb = new TransportTestHelpers.MockClientSignalCallback();
        transport.connectWithOffer("offer-sdp", cb);

        pc.lastHandleOfferCallback.onFailure("bad offer");

        assertEquals(1, listener.errors.size());
        assertTrue(listener.errors.get(0).contains("bad offer"));
    }

    @Test
    void connectWithOfferInitFailureReportsError() {
        pc.initializeResult = false;
        TransportTestHelpers.MockClientSignalCallback cb = new TransportTestHelpers.MockClientSignalCallback();
        transport.connectWithOffer("offer-sdp", cb);

        assertEquals(1, listener.errors.size());
        assertTrue(listener.errors.get(0).contains("initialization"));
    }

    @Test
    void connectWithOfferClosesExistingConnection() {
        TransportTestHelpers.MockClientSignalCallback cb1 = new TransportTestHelpers.MockClientSignalCallback();
        transport.connectWithOffer("offer1", cb1);
        assertNotNull(transport.getPeerConnection());

        TransportTestHelpers.MockClientSignalCallback cb2 = new TransportTestHelpers.MockClientSignalCallback();
        transport.connectWithOffer("offer2", cb2);

        assertTrue(pc.calls.contains("closePeerConnection"));
    }

    // --- ICE candidate exchange ---

    @Test
    void iceCandidatesForwardedToCallback() {
        TransportTestHelpers.MockClientSignalCallback cb = new TransportTestHelpers.MockClientSignalCallback();
        transport.connectWithOffer("offer", cb);

        pc.lastPcHandler.onIceCandidate("{\"candidate\":\"test\"}");

        assertEquals(1, cb.iceCandidates.size());
        assertEquals("{\"candidate\":\"test\"}", cb.iceCandidates.get(0));
    }

    @Test
    void addIceCandidateDelegatesToProvider() {
        TransportTestHelpers.MockClientSignalCallback cb = new TransportTestHelpers.MockClientSignalCallback();
        transport.connectWithOffer("offer", cb);

        transport.addIceCandidate("{\"candidate\":\"remote\"}");

        assertTrue(pc.calls.contains("addIceCandidate"));
        assertEquals("{\"candidate\":\"remote\"}", pc.lastIceCandidateJson);
    }

    @Test
    void addIceCandidateNoOpWithoutPeerConnection() {
        transport.addIceCandidate("{\"candidate\":\"test\"}");
        assertFalse(pc.calls.contains("addIceCandidate"));
    }

    // --- Disconnect ---

    @Test
    void disconnectClosesPeerConnection() {
        TransportTestHelpers.MockClientSignalCallback cb = new TransportTestHelpers.MockClientSignalCallback();
        transport.connectWithOffer("offer", cb);

        transport.disconnect();

        assertTrue(pc.calls.contains("closePeerConnection"));
        assertNull(transport.getPeerConnection());
        assertFalse(transport.isConnected());
    }

    @Test
    void disconnectShutsDownScheduler() {
        transport.disconnect();
        assertTrue(sched.calls.contains("shutdown"));
    }

    // --- Send reliable ---

    @Test
    void sendReliableDelegatesToProvider() {
        connectAndOpenChannels();

        byte[] data = new byte[]{1, 2, 3};
        transport.sendReliable(data);

        assertTrue(pc.calls.contains("sendData"));
        assertEquals(1, pc.sentData.size());
        assertArrayEquals(data, pc.sentData.get(0));
    }

    @Test
    void sendReliableNoOpWhenDisconnected() {
        transport.sendReliable(new byte[]{1});
        assertFalse(pc.calls.contains("sendData"));
    }

    // --- Send unreliable ---

    @Test
    void sendUnreliableDelegatesToProvider() {
        connectAndOpenChannels();

        byte[] data = new byte[]{4, 5, 6};
        transport.sendUnreliable(data);

        assertTrue(pc.calls.contains("sendData"));
    }

    @Test
    void sendUnreliableDropsWhenBufferFull() {
        connectAndOpenChannels();

        pc.bufferedAmount = 100000;
        transport.sendUnreliable(new byte[]{1});

        // sendData should NOT be called (only the earlier opens)
        int sendCount = 0;
        for (int i = 0; i < pc.calls.size(); i++) {
            if ("sendData".equals(pc.calls.get(i))) {
                sendCount++;
            }
        }
        assertEquals(0, sendCount);
    }

    @Test
    void sendUnreliableFallsBackToReliableWhenChannelNull() {
        // Connect but only open reliable channel (no unreliable)
        TransportTestHelpers.MockClientSignalCallback cb = new TransportTestHelpers.MockClientSignalCallback();
        transport.connectWithOffer("offer", cb);
        // Simulate only reliable channel received
        pc.lastPcHandler.onDataChannel(pc.reliableChannel, "reliable");
        pc.lastDcHandler.onReliableOpen();

        transport.sendUnreliable(new byte[]{1, 2});

        assertTrue(pc.calls.contains("sendData"));
    }

    // --- Data channel lifecycle ---

    @Test
    void reliableChannelOpenFiresOnConnected() {
        TransportTestHelpers.MockClientSignalCallback cb = new TransportTestHelpers.MockClientSignalCallback();
        transport.connectWithOffer("offer", cb);

        pc.lastPcHandler.onDataChannel(pc.reliableChannel, "reliable");
        pc.lastDcHandler.onReliableOpen();

        assertTrue(transport.isConnected());
        assertEquals(1, listener.connectedCount);
    }

    @Test
    void reliableChannelCloseFiresOnDisconnected() {
        connectAndOpenChannels();

        pc.lastDcHandler.onReliableClose();

        assertFalse(transport.isConnected());
        assertEquals(1, listener.disconnectedCount);
    }

    @Test
    void unreliableChannelOpenCloseDoNotAffectConnectionState() {
        connectAndOpenChannels();

        pc.lastDcHandler.onUnreliableClose();
        assertTrue(transport.isConnected());
        assertEquals(0, listener.disconnectedCount);

        pc.lastDcHandler.onUnreliableOpen();
        assertTrue(transport.isConnected());
    }

    @Test
    void messagesForwardedToListenerWithReliableFlag() {
        connectAndOpenChannels();

        pc.lastDcHandler.onMessage(new byte[]{10}, true);
        pc.lastDcHandler.onMessage(new byte[]{20}, false);

        assertEquals(2, listener.receivedData.size());
        assertTrue(listener.receivedReliable.get(0).booleanValue());
        assertFalse(listener.receivedReliable.get(1).booleanValue());
    }

    // --- ICE state machine ---

    @Test
    void iceConnectedResetsCounts() {
        connectAndOpenChannels();

        transport.handleConnectionStateChanged(ConnectionState.DISCONNECTED);
        transport.handleConnectionStateChanged(ConnectionState.CONNECTED);

        assertEquals(0, transport.getIceRestartAttempts());
        assertEquals(0, transport.getDisconnectedAtMs());
        assertFalse(transport.getIceClosedOrFailed());
    }

    @Test
    void iceDisconnectedSchedulesRestart() {
        connectAndOpenChannels();

        transport.handleConnectionStateChanged(ConnectionState.DISCONNECTED);

        assertTrue(sched.calls.contains("schedule:3500"));
        assertTrue(transport.getDisconnectedAtMs() > 0);
    }

    @Test
    void iceDisconnectedTimerRestartsIce() {
        connectAndOpenChannels();

        transport.handleConnectionStateChanged(ConnectionState.DISCONNECTED);
        sched.runLast();

        assertTrue(pc.calls.contains("restartIce"));
    }

    @Test
    void iceFailedExponentialBackoff() {
        connectAndOpenChannels();

        transport.handleConnectionStateChanged(ConnectionState.FAILED);
        assertEquals(1, transport.getIceRestartAttempts());
        assertTrue(sched.calls.contains("schedule:2000"));

        transport.handleConnectionStateChanged(ConnectionState.FAILED);
        assertEquals(2, transport.getIceRestartAttempts());
        assertTrue(sched.calls.contains("schedule:4000"));

        transport.handleConnectionStateChanged(ConnectionState.FAILED);
        assertEquals(3, transport.getIceRestartAttempts());
        assertTrue(sched.calls.contains("schedule:8000"));
    }

    @Test
    void iceFailedMaxAttemptsFiresDisconnect() {
        connectAndOpenChannels();

        // Exhaust attempts (default max = 3)
        transport.handleConnectionStateChanged(ConnectionState.FAILED);
        transport.handleConnectionStateChanged(ConnectionState.FAILED);
        transport.handleConnectionStateChanged(ConnectionState.FAILED);
        transport.handleConnectionStateChanged(ConnectionState.FAILED);

        // 4th attempt exceeds max (3), should fire disconnect
        assertTrue(listener.disconnectedCount > 0);
    }

    @Test
    void iceClosedFiresDisconnect() {
        connectAndOpenChannels();

        transport.handleConnectionStateChanged(ConnectionState.CLOSED);

        assertFalse(transport.isConnected());
        assertTrue(listener.disconnectedCount > 0);
        assertTrue(transport.getIceClosedOrFailed());
    }

    @Test
    void iceConnectedCancelsTimers() {
        connectAndOpenChannels();

        transport.handleConnectionStateChanged(ConnectionState.DISCONNECTED);
        transport.handleConnectionStateChanged(ConnectionState.CONNECTED);

        assertTrue(sched.calls.contains("cancel"));
    }

    @Test
    void iceConnectedButChannelClosedFiresDisconnect() {
        connectAndOpenChannels();
        pc.channelOpen = false;

        transport.handleConnectionStateChanged(ConnectionState.CONNECTED);

        assertTrue(listener.disconnectedCount > 0);
    }

    @Test
    void iceDisconnectedStaleTimerPrevented() {
        connectAndOpenChannels();

        transport.handleConnectionStateChanged(ConnectionState.DISCONNECTED);
        // Simulate connection recovery before timer fires
        transport.handleConnectionStateChanged(ConnectionState.CONNECTED);
        // Now run the stale timer — should NOT restart ICE
        int restartCountBefore = countCalls(pc.calls, "restartIce");
        sched.runAll();
        int restartCountAfter = countCalls(pc.calls, "restartIce");

        assertEquals(restartCountBefore, restartCountAfter);
    }

    // --- Error handling ---

    @Test
    void nullListenerSafeOnConnect() {
        transport.setListener(null);
        TransportTestHelpers.MockClientSignalCallback cb = new TransportTestHelpers.MockClientSignalCallback();
        transport.connectWithOffer("offer", cb);

        // Should not throw
        pc.lastPcHandler.onDataChannel(pc.reliableChannel, "reliable");
        pc.lastDcHandler.onReliableOpen();
        pc.lastDcHandler.onMessage(new byte[]{1}, true);
        pc.lastDcHandler.onReliableClose();
    }

    @Test
    void nullListenerSafeOnIceStateChanges() {
        transport.setListener(null);
        TransportTestHelpers.MockClientSignalCallback cb = new TransportTestHelpers.MockClientSignalCallback();
        transport.connectWithOffer("offer", cb);

        // Should not throw
        transport.handleConnectionStateChanged(ConnectionState.CLOSED);
    }

    @Test
    void isConnectedReturnsFalseInitially() {
        assertFalse(transport.isConnected());
    }

    @Test
    void configAccessor() {
        assertNotNull(transport.getConfig());
        assertEquals(WebRTCConfiguration.DEFAULT_ICE_RESTART_DELAY_MS,
                transport.getConfig().iceRestartDelayMs);
    }

    // --- Helpers ---

    private void connectAndOpenChannels() {
        TransportTestHelpers.MockClientSignalCallback cb = new TransportTestHelpers.MockClientSignalCallback();
        transport.connectWithOffer("offer", cb);
        pc.lastPcHandler.onDataChannel(pc.reliableChannel, "reliable");
        pc.lastPcHandler.onDataChannel(pc.unreliableChannel, "unreliable");
        pc.lastDcHandler.onReliableOpen();
    }

    private int countCalls(java.util.List<String> calls, String target) {
        int count = 0;
        for (int i = 0; i < calls.size(); i++) {
            if (target.equals(calls.get(i))) {
                count++;
            }
        }
        return count;
    }
}
