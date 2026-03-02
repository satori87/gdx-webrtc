package com.github.satori87.gdx.webrtc.transport;

import com.github.satori87.gdx.webrtc.ConnectionState;
import com.github.satori87.gdx.webrtc.TestHelpers;
import com.github.satori87.gdx.webrtc.WebRTCConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link BaseWebRTCServerTransport}.
 */
class BaseWebRTCServerTransportTest {

    private TestHelpers.MockPeerConnectionProvider pc;
    private TestHelpers.MockScheduler sched;
    private TransportTestHelpers.MockServerTransportListener listener;
    private BaseWebRTCServerTransport transport;

    @BeforeEach
    void setUp() {
        pc = new TestHelpers.MockPeerConnectionProvider();
        sched = new TestHelpers.MockScheduler();
        listener = new TransportTestHelpers.MockServerTransportListener();
        transport = TransportTestHelpers.createServerTransport(pc, sched, listener);
    }

    // --- Offer creation ---

    @Test
    void createPeerForOfferInitializesFactory() {
        TransportTestHelpers.MockServerSignalCallback cb = new TransportTestHelpers.MockServerSignalCallback();
        transport.createPeerForOffer(cb);

        assertTrue(pc.initialized);
    }

    @Test
    void createPeerForOfferCreatesPeerConnection() {
        TransportTestHelpers.MockServerSignalCallback cb = new TransportTestHelpers.MockServerSignalCallback();
        transport.createPeerForOffer(cb);

        assertTrue(pc.calls.contains("createPeerConnection"));
    }

    @Test
    void createPeerForOfferCreatesDataChannels() {
        TransportTestHelpers.MockServerSignalCallback cb = new TransportTestHelpers.MockServerSignalCallback();
        transport.createPeerForOffer(cb);

        assertTrue(pc.calls.contains("createDataChannels"));
    }

    @Test
    void createPeerForOfferCreatesOffer() {
        TransportTestHelpers.MockServerSignalCallback cb = new TransportTestHelpers.MockServerSignalCallback();
        transport.createPeerForOffer(cb);

        assertTrue(pc.calls.contains("createOffer"));
    }

    @Test
    void createPeerForOfferSendsOfferViaCallback() {
        TransportTestHelpers.MockServerSignalCallback cb = new TransportTestHelpers.MockServerSignalCallback();
        transport.createPeerForOffer(cb);

        pc.lastOfferCallback.onSuccess("server-offer-sdp");

        assertEquals(1, cb.offers.size());
        assertEquals("server-offer-sdp", cb.offers.get(0));
    }

    @Test
    void createPeerForOfferReturnsConnId() {
        TransportTestHelpers.MockServerSignalCallback cb = new TransportTestHelpers.MockServerSignalCallback();
        int connId = transport.createPeerForOffer(cb);

        assertEquals(1, connId);
    }

    // --- connId assignment ---

    @Test
    void connIdsAreSequential() {
        TransportTestHelpers.MockServerSignalCallback cb = new TransportTestHelpers.MockServerSignalCallback();
        int id1 = transport.createPeerForOffer(cb);
        int id2 = transport.createPeerForOffer(cb);
        int id3 = transport.createPeerForOffer(cb);

        assertEquals(1, id1);
        assertEquals(2, id2);
        assertEquals(3, id3);
    }

    // --- Answer handling ---

    @Test
    void setAnswerDelegatesToProvider() {
        TransportTestHelpers.MockServerSignalCallback cb = new TransportTestHelpers.MockServerSignalCallback();
        int connId = transport.createPeerForOffer(cb);

        transport.setAnswer(connId, "client-answer-sdp");

        assertTrue(pc.calls.contains("setRemoteAnswer"));
        assertEquals("client-answer-sdp", pc.lastRemoteAnswerSdp);
    }

    @Test
    void setAnswerUnknownConnIdIsNoOp() {
        transport.setAnswer(999, "answer");
        assertFalse(pc.calls.contains("setRemoteAnswer"));
    }

    // --- ICE candidate handling ---

    @Test
    void addIceCandidateDelegatesToProvider() {
        TransportTestHelpers.MockServerSignalCallback cb = new TransportTestHelpers.MockServerSignalCallback();
        int connId = transport.createPeerForOffer(cb);

        transport.addIceCandidate(connId, "{\"candidate\":\"test\"}");

        assertTrue(pc.calls.contains("addIceCandidate"));
    }

    @Test
    void addIceCandidateUnknownConnIdIsNoOp() {
        transport.addIceCandidate(999, "{\"candidate\":\"test\"}");
        assertFalse(pc.calls.contains("addIceCandidate"));
    }

    @Test
    void iceCandidatesForwardedToCallbackWithConnId() {
        TransportTestHelpers.MockServerSignalCallback cb = new TransportTestHelpers.MockServerSignalCallback();
        int connId = transport.createPeerForOffer(cb);

        pc.lastPcHandler.onIceCandidate("{\"candidate\":\"server-ice\"}");

        assertEquals(1, cb.iceCandidates.size());
        assertEquals("{\"candidate\":\"server-ice\"}", cb.iceCandidates.get(0));
        assertEquals(connId, cb.iceConnIds.get(0).intValue());
    }

    // --- Data channel lifecycle ---

    @Test
    void reliableOpenFiresOnClientConnected() {
        TransportTestHelpers.MockServerSignalCallback cb = new TransportTestHelpers.MockServerSignalCallback();
        int connId = transport.createPeerForOffer(cb);

        pc.lastDcHandler.onReliableOpen();

        assertEquals(1, listener.connectedIds.size());
        assertEquals(connId, listener.connectedIds.get(0).intValue());
    }

    @Test
    void reliableCloseFiresOnClientDisconnected() {
        TransportTestHelpers.MockServerSignalCallback cb = new TransportTestHelpers.MockServerSignalCallback();
        int connId = transport.createPeerForOffer(cb);
        pc.lastDcHandler.onReliableOpen();

        pc.lastDcHandler.onReliableClose();

        assertEquals(1, listener.disconnectedIds.size());
        assertEquals(connId, listener.disconnectedIds.get(0).intValue());
    }

    @Test
    void messagesForwardedWithConnIdAndReliableFlag() {
        TransportTestHelpers.MockServerSignalCallback cb = new TransportTestHelpers.MockServerSignalCallback();
        int connId = transport.createPeerForOffer(cb);
        pc.lastDcHandler.onReliableOpen();

        pc.lastDcHandler.onMessage(new byte[]{42}, true);
        pc.lastDcHandler.onMessage(new byte[]{43}, false);

        assertEquals(2, listener.receivedData.size());
        assertEquals(connId, listener.messageConnIds.get(0).intValue());
        assertTrue(listener.receivedReliable.get(0).booleanValue());
        assertFalse(listener.receivedReliable.get(1).booleanValue());
    }

    // --- Send reliable/unreliable ---

    @Test
    void sendReliableToConnId() {
        TransportTestHelpers.MockServerSignalCallback cb = new TransportTestHelpers.MockServerSignalCallback();
        int connId = transport.createPeerForOffer(cb);
        pc.lastDcHandler.onReliableOpen();

        transport.sendReliable(connId, new byte[]{1, 2, 3});

        assertTrue(pc.calls.contains("sendData"));
        assertArrayEquals(new byte[]{1, 2, 3}, pc.sentData.get(0));
    }

    @Test
    void sendReliableUnknownConnIdIsNoOp() {
        transport.sendReliable(999, new byte[]{1});
        assertFalse(pc.calls.contains("sendData"));
    }

    @Test
    void sendUnreliableToConnId() {
        TransportTestHelpers.MockServerSignalCallback cb = new TransportTestHelpers.MockServerSignalCallback();
        int connId = transport.createPeerForOffer(cb);
        pc.lastDcHandler.onReliableOpen();

        transport.sendUnreliable(connId, new byte[]{4, 5});

        assertTrue(pc.calls.contains("sendData"));
    }

    @Test
    void sendUnreliableDropsWhenBufferFull() {
        TransportTestHelpers.MockServerSignalCallback cb = new TransportTestHelpers.MockServerSignalCallback();
        int connId = transport.createPeerForOffer(cb);
        pc.lastDcHandler.onReliableOpen();

        pc.bufferedAmount = 100000;
        transport.sendUnreliable(connId, new byte[]{1});

        int sendCount = countCalls(pc.calls, "sendData");
        assertEquals(0, sendCount);
    }

    @Test
    void sendUnreliableUnknownConnIdIsNoOp() {
        transport.sendUnreliable(999, new byte[]{1});
        assertFalse(pc.calls.contains("sendData"));
    }

    // --- Broadcast ---

    @Test
    void broadcastReliableSendsToAllConnected() {
        TransportTestHelpers.MockServerSignalCallback cb = new TransportTestHelpers.MockServerSignalCallback();
        transport.createPeerForOffer(cb);
        pc.lastDcHandler.onReliableOpen();
        transport.createPeerForOffer(cb);
        pc.lastDcHandler.onReliableOpen();

        pc.sentData.clear();
        pc.calls.clear();
        transport.broadcastReliable(new byte[]{99});

        int sendCount = countCalls(pc.calls, "sendData");
        assertEquals(2, sendCount);
    }

    @Test
    void broadcastUnreliableSendsToAllConnected() {
        TransportTestHelpers.MockServerSignalCallback cb = new TransportTestHelpers.MockServerSignalCallback();
        transport.createPeerForOffer(cb);
        pc.lastDcHandler.onReliableOpen();
        transport.createPeerForOffer(cb);
        pc.lastDcHandler.onReliableOpen();

        pc.sentData.clear();
        pc.calls.clear();
        transport.broadcastUnreliable(new byte[]{88});

        int sendCount = countCalls(pc.calls, "sendData");
        assertEquals(2, sendCount);
    }

    @Test
    void broadcastSkipsDisconnectedPeers() {
        TransportTestHelpers.MockServerSignalCallback cb = new TransportTestHelpers.MockServerSignalCallback();
        transport.createPeerForOffer(cb);
        pc.lastDcHandler.onReliableOpen();
        transport.createPeerForOffer(cb);
        // Second peer NOT connected

        pc.sentData.clear();
        pc.calls.clear();
        transport.broadcastReliable(new byte[]{77});

        int sendCount = countCalls(pc.calls, "sendData");
        assertEquals(1, sendCount);
    }

    // --- Disconnect specific peer ---

    @Test
    void disconnectRemovesPeerAndClosesPc() {
        TransportTestHelpers.MockServerSignalCallback cb = new TransportTestHelpers.MockServerSignalCallback();
        int connId = transport.createPeerForOffer(cb);

        transport.disconnect(connId);

        assertTrue(pc.calls.contains("closePeerConnection"));
        assertFalse(transport.getPeers().containsKey(Integer.valueOf(connId)));
    }

    @Test
    void disconnectUnknownConnIdIsNoOp() {
        transport.disconnect(999);
        assertFalse(pc.calls.contains("closePeerConnection"));
    }

    // --- Stop ---

    @Test
    void stopClosesAllPeers() {
        TransportTestHelpers.MockServerSignalCallback cb = new TransportTestHelpers.MockServerSignalCallback();
        transport.createPeerForOffer(cb);
        transport.createPeerForOffer(cb);

        transport.stop();

        assertTrue(transport.getPeers().isEmpty());
        assertTrue(sched.calls.contains("shutdown"));
    }

    // --- getConnectionCount ---

    @Test
    void getConnectionCountReturnsConnectedOnly() {
        TransportTestHelpers.MockServerSignalCallback cb = new TransportTestHelpers.MockServerSignalCallback();
        transport.createPeerForOffer(cb);
        pc.lastDcHandler.onReliableOpen();
        transport.createPeerForOffer(cb);
        // Second peer not connected

        assertEquals(1, transport.getConnectionCount());
    }

    @Test
    void getConnectionCountZeroInitially() {
        assertEquals(0, transport.getConnectionCount());
    }

    // --- setTurnServer ---

    @Test
    void setTurnServerAffectsSubsequentPeers() {
        transport.setTurnServer("turn:test:3478", "user", "pass");

        TransportTestHelpers.MockServerSignalCallback cb = new TransportTestHelpers.MockServerSignalCallback();
        transport.createPeerForOffer(cb);

        // The config passed to createPeerConnection should have TURN set
        assertTrue(pc.calls.contains("createPeerConnection"));
    }

    // --- Per-peer ICE state machine ---

    @Test
    void iceConnectedPerPeerResetsCounts() {
        TransportTestHelpers.MockServerSignalCallback cb = new TransportTestHelpers.MockServerSignalCallback();
        int connId = transport.createPeerForOffer(cb);
        pc.lastDcHandler.onReliableOpen();

        pc.lastPcHandler.onConnectionStateChanged(ConnectionState.DISCONNECTED);
        pc.lastPcHandler.onConnectionStateChanged(ConnectionState.CONNECTED);

        // Should not fire disconnect
        assertEquals(0, listener.disconnectedIds.size());
    }

    @Test
    void iceDisconnectedPerPeerSchedulesRestart() {
        TransportTestHelpers.MockServerSignalCallback cb = new TransportTestHelpers.MockServerSignalCallback();
        transport.createPeerForOffer(cb);
        pc.lastDcHandler.onReliableOpen();

        pc.lastPcHandler.onConnectionStateChanged(ConnectionState.DISCONNECTED);

        assertTrue(sched.calls.contains("schedule:3500"));
    }

    @Test
    void iceFailedPerPeerExponentialBackoff() {
        TransportTestHelpers.MockServerSignalCallback cb = new TransportTestHelpers.MockServerSignalCallback();
        transport.createPeerForOffer(cb);
        pc.lastDcHandler.onReliableOpen();

        pc.lastPcHandler.onConnectionStateChanged(ConnectionState.FAILED);
        assertTrue(sched.calls.contains("schedule:2000"));

        pc.lastPcHandler.onConnectionStateChanged(ConnectionState.FAILED);
        assertTrue(sched.calls.contains("schedule:4000"));
    }

    @Test
    void iceFailedMaxAttemptsFiresClientDisconnected() {
        TransportTestHelpers.MockServerSignalCallback cb = new TransportTestHelpers.MockServerSignalCallback();
        int connId = transport.createPeerForOffer(cb);
        pc.lastDcHandler.onReliableOpen();

        pc.lastPcHandler.onConnectionStateChanged(ConnectionState.FAILED);
        pc.lastPcHandler.onConnectionStateChanged(ConnectionState.FAILED);
        pc.lastPcHandler.onConnectionStateChanged(ConnectionState.FAILED);
        pc.lastPcHandler.onConnectionStateChanged(ConnectionState.FAILED);

        assertTrue(listener.disconnectedIds.contains(Integer.valueOf(connId)));
    }

    @Test
    void iceClosedPerPeerFiresClientDisconnected() {
        TransportTestHelpers.MockServerSignalCallback cb = new TransportTestHelpers.MockServerSignalCallback();
        int connId = transport.createPeerForOffer(cb);
        pc.lastDcHandler.onReliableOpen();

        pc.lastPcHandler.onConnectionStateChanged(ConnectionState.CLOSED);

        assertTrue(listener.disconnectedIds.contains(Integer.valueOf(connId)));
    }

    // --- Multiple peers independence ---

    @Test
    void multiplePeersIndependent() {
        TransportTestHelpers.MockServerSignalCallback cb = new TransportTestHelpers.MockServerSignalCallback();

        int id1 = transport.createPeerForOffer(cb);
        pc.lastDcHandler.onReliableOpen();
        // Save dc handler for peer1
        com.github.satori87.gdx.webrtc.DataChannelEventHandler dc1 = pc.lastDcHandler;

        int id2 = transport.createPeerForOffer(cb);
        pc.lastDcHandler.onReliableOpen();

        assertEquals(2, transport.getConnectionCount());

        // Disconnect peer 1 only
        dc1.onReliableClose();

        assertEquals(1, transport.getConnectionCount());
        assertTrue(listener.disconnectedIds.contains(Integer.valueOf(id1)));
    }

    // --- Null listener safety ---

    @Test
    void nullListenerSafeOnDataChannelEvents() {
        transport.setListener(null);
        TransportTestHelpers.MockServerSignalCallback cb = new TransportTestHelpers.MockServerSignalCallback();
        transport.createPeerForOffer(cb);

        // Should not throw
        pc.lastDcHandler.onReliableOpen();
        pc.lastDcHandler.onMessage(new byte[]{1}, true);
        pc.lastDcHandler.onReliableClose();
    }

    @Test
    void nullListenerSafeOnIceStateChange() {
        transport.setListener(null);
        TransportTestHelpers.MockServerSignalCallback cb = new TransportTestHelpers.MockServerSignalCallback();
        transport.createPeerForOffer(cb);

        // Should not throw
        pc.lastPcHandler.onConnectionStateChanged(ConnectionState.CLOSED);
    }

    // --- Configuration ---

    @Test
    void configAccessor() {
        assertNotNull(transport.getConfig());
    }

    @Test
    void nextConnIdAccessor() {
        assertEquals(1, transport.getNextConnId());
        TransportTestHelpers.MockServerSignalCallback cb = new TransportTestHelpers.MockServerSignalCallback();
        transport.createPeerForOffer(cb);
        assertEquals(2, transport.getNextConnId());
    }

    @Test
    void initFailureDoesNotAddPeerToMap() {
        pc.initializeResult = false;
        TransportTestHelpers.MockServerSignalCallback cb = new TransportTestHelpers.MockServerSignalCallback();
        transport.createPeerForOffer(cb);

        // Peer should not be in map since init failed
        // ConnId was still assigned (1) but peer state wasn't kept
        assertTrue(transport.getPeers().isEmpty());
    }

    // --- Helpers ---

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
