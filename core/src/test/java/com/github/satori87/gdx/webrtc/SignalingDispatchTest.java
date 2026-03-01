package com.github.satori87.gdx.webrtc;

import com.github.satori87.gdx.webrtc.TestHelpers.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SignalingDispatchTest {

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
        client = TestHelpers.createConnectedClient(pc, sig, sched, listener);
    }

    @Test
    void welcomeAssignsLocalIdAndNotifiesListener() {
        assertEquals(42, client.getLocalId());
        assertEquals(42, listener.signalingConnectedId);
    }

    @Test
    void connectRequestCreatesOfferFlow() {
        sig.simulateMessage(new SignalMessage(SignalMessage.TYPE_CONNECT_REQUEST, 5, 42, ""));
        assertTrue(pc.calls.contains("initialize"));
        assertTrue(pc.calls.contains("createPeerConnection"));
        assertTrue(pc.calls.contains("createDataChannels"));
        assertTrue(pc.calls.contains("createOffer"));
    }

    @Test
    void offerCreatesAnswerFlow() {
        sig.simulateMessage(new SignalMessage(SignalMessage.TYPE_OFFER, 5, 42, "offer-sdp"));
        assertTrue(pc.calls.contains("initialize"));
        assertTrue(pc.calls.contains("createPeerConnection"));
        assertTrue(pc.calls.contains("handleOffer"));
    }

    @Test
    void offerCallbackSendsAnswer() {
        sig.simulateMessage(new SignalMessage(SignalMessage.TYPE_OFFER, 5, 42, "offer-sdp"));
        assertNotNull(pc.lastHandleOfferCallback);
        pc.lastHandleOfferCallback.onSuccess("answer-sdp");

        SignalMessage lastSent = sig.sentMessages.get(sig.sentMessages.size() - 1);
        assertEquals(SignalMessage.TYPE_ANSWER, lastSent.type);
        assertEquals(42, lastSent.source);
        assertEquals(5, lastSent.target);
        assertEquals("answer-sdp", lastSent.data);
    }

    @Test
    void answerSetsRemoteDescription() {
        // First create a peer via connect request
        sig.simulateMessage(new SignalMessage(SignalMessage.TYPE_CONNECT_REQUEST, 5, 42, ""));
        // Now receive answer
        sig.simulateMessage(new SignalMessage(SignalMessage.TYPE_ANSWER, 5, 42, "answer-sdp"));
        assertTrue(pc.calls.contains("setRemoteAnswer"));
        assertEquals("answer-sdp", pc.lastRemoteAnswerSdp);
    }

    @Test
    void answerForUnknownPeerIsIgnored() {
        sig.simulateMessage(new SignalMessage(SignalMessage.TYPE_ANSWER, 99, 42, "sdp"));
        assertFalse(pc.calls.contains("setRemoteAnswer"));
    }

    @Test
    void iceAddsCandidate() {
        sig.simulateMessage(new SignalMessage(SignalMessage.TYPE_CONNECT_REQUEST, 5, 42, ""));
        sig.simulateMessage(new SignalMessage(SignalMessage.TYPE_ICE, 5, 42, "{\"candidate\":\"c\"}"));
        assertTrue(pc.calls.contains("addIceCandidate"));
        assertEquals("{\"candidate\":\"c\"}", pc.lastIceCandidateJson);
    }

    @Test
    void iceForUnknownPeerIsIgnored() {
        sig.simulateMessage(new SignalMessage(SignalMessage.TYPE_ICE, 99, 42, "{\"candidate\":\"c\"}"));
        assertFalse(pc.calls.contains("addIceCandidate"));
    }

    @Test
    void errorNotifiesListener() {
        sig.simulateMessage(new SignalMessage(SignalMessage.TYPE_ERROR, 0, 0, "test error"));
        assertEquals(1, listener.errors.size());
        assertEquals("test error", listener.errors.get(0));
    }

    @Test
    void peerJoinedNotifiesListener() {
        sig.simulateMessage(new SignalMessage(SignalMessage.TYPE_PEER_JOINED, 7, 0, ""));
        assertEquals(1, listener.joinedPeers.size());
        assertEquals(Integer.valueOf(7), listener.joinedPeers.get(0));
    }

    @Test
    void peerLeftNotifiesListener() {
        sig.simulateMessage(new SignalMessage(SignalMessage.TYPE_PEER_LEFT, 7, 0, ""));
        assertEquals(1, listener.leftPeers.size());
        assertEquals(Integer.valueOf(7), listener.leftPeers.get(0));
    }

    @Test
    void peerListIsIgnored() {
        assertDoesNotThrow(() ->
            sig.simulateMessage(new SignalMessage(SignalMessage.TYPE_PEER_LIST, 0, 0, ""))
        );
    }

    @Test
    void unknownTypeDoesNotThrow() {
        assertDoesNotThrow(() ->
            sig.simulateMessage(new SignalMessage(99, 0, 0, ""))
        );
    }

    @Test
    void initFailureReportsError() {
        pc.initializeResult = false;
        sig.simulateMessage(new SignalMessage(SignalMessage.TYPE_CONNECT_REQUEST, 5, 42, ""));
        assertEquals(1, listener.errors.size());
        assertTrue(listener.errors.get(0).contains("factory initialization failed"));
    }

    @Test
    void connectRequestOfferCallbackSendsOffer() {
        sig.simulateMessage(new SignalMessage(SignalMessage.TYPE_CONNECT_REQUEST, 5, 42, ""));
        assertNotNull(pc.lastOfferCallback);
        pc.lastOfferCallback.onSuccess("offer-sdp");

        SignalMessage lastSent = sig.sentMessages.get(sig.sentMessages.size() - 1);
        assertEquals(SignalMessage.TYPE_OFFER, lastSent.type);
        assertEquals("offer-sdp", lastSent.data);
    }

    @Test
    void offerFailureReportsError() {
        sig.simulateMessage(new SignalMessage(SignalMessage.TYPE_CONNECT_REQUEST, 5, 42, ""));
        pc.lastOfferCallback.onFailure("offer failed");
        assertEquals(1, listener.errors.size());
        assertTrue(listener.errors.get(0).contains("offer failed"));
    }

    @Test
    void nullPcCreationReportsError() {
        pc.lastCreatedPc = null;
        sig.simulateMessage(new SignalMessage(SignalMessage.TYPE_CONNECT_REQUEST, 5, 42, ""));
        assertEquals(1, listener.errors.size());
        assertTrue(listener.errors.get(0).contains("Failed to create peer connection"));
    }
}
