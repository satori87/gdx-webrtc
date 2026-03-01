package com.github.satori87.gdx.webrtc;

import com.github.satori87.gdx.webrtc.TestHelpers.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConnectionFlowTest {

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

    // --- Connect Request (offerer flow) ---

    @Test
    void connectRequestInitializesFactoryCreatesPcAndOffer() {
        sig.simulateMessage(new SignalMessage(SignalMessage.TYPE_CONNECT_REQUEST, 5, 42, ""));

        assertTrue(pc.calls.contains("initialize"));
        assertTrue(pc.calls.contains("createPeerConnection"));
        assertTrue(pc.calls.contains("createDataChannels"));
        assertTrue(pc.calls.contains("createOffer"));
    }

    @Test
    void connectRequestSetsOfferer() {
        sig.simulateMessage(new SignalMessage(SignalMessage.TYPE_CONNECT_REQUEST, 5, 42, ""));
        BaseWebRTCClient.PeerState peer =
                (BaseWebRTCClient.PeerState) client.getPeers().get(Integer.valueOf(5));
        assertTrue(peer.isOfferer);
    }

    @Test
    void connectRequestOfferSuccessSendsOffer() {
        sig.simulateMessage(new SignalMessage(SignalMessage.TYPE_CONNECT_REQUEST, 5, 42, ""));
        pc.lastOfferCallback.onSuccess("offer-sdp");

        SignalMessage sent = sig.sentMessages.get(sig.sentMessages.size() - 1);
        assertEquals(SignalMessage.TYPE_OFFER, sent.type);
        assertEquals(42, sent.source);
        assertEquals(5, sent.target);
        assertEquals("offer-sdp", sent.data);
    }

    @Test
    void connectRequestOfferFailureReportsError() {
        sig.simulateMessage(new SignalMessage(SignalMessage.TYPE_CONNECT_REQUEST, 5, 42, ""));
        pc.lastOfferCallback.onFailure("sdp error");

        assertEquals(1, listener.errors.size());
        assertTrue(listener.errors.get(0).contains("sdp error"));
    }

    @Test
    void connectRequestInitFailureReportsError() {
        pc.initializeResult = false;
        sig.simulateMessage(new SignalMessage(SignalMessage.TYPE_CONNECT_REQUEST, 5, 42, ""));

        assertEquals(1, listener.errors.size());
        assertTrue(listener.errors.get(0).contains("factory initialization failed"));
        assertFalse(pc.calls.contains("createPeerConnection"));
    }

    @Test
    void connectRequestNullPcReportsError() {
        pc.lastCreatedPc = null;
        sig.simulateMessage(new SignalMessage(SignalMessage.TYPE_CONNECT_REQUEST, 5, 42, ""));

        assertEquals(1, listener.errors.size());
        assertTrue(listener.errors.get(0).contains("Failed to create peer connection"));
    }

    // --- Offer (answerer flow) ---

    @Test
    void offerInitializesFactoryCreatesPcAndHandlesOffer() {
        sig.simulateMessage(new SignalMessage(SignalMessage.TYPE_OFFER, 5, 42, "offer-sdp"));

        assertTrue(pc.calls.contains("initialize"));
        assertTrue(pc.calls.contains("createPeerConnection"));
        assertTrue(pc.calls.contains("handleOffer"));
        // Answerer should NOT create data channels
        assertFalse(pc.calls.contains("createDataChannels"));
    }

    @Test
    void offerSetsNonOfferer() {
        sig.simulateMessage(new SignalMessage(SignalMessage.TYPE_OFFER, 5, 42, "offer-sdp"));
        BaseWebRTCClient.PeerState peer =
                (BaseWebRTCClient.PeerState) client.getPeers().get(Integer.valueOf(5));
        assertFalse(peer.isOfferer);
    }

    @Test
    void offerAnswerSuccessSendsAnswer() {
        sig.simulateMessage(new SignalMessage(SignalMessage.TYPE_OFFER, 5, 42, "offer-sdp"));
        pc.lastHandleOfferCallback.onSuccess("answer-sdp");

        SignalMessage sent = sig.sentMessages.get(sig.sentMessages.size() - 1);
        assertEquals(SignalMessage.TYPE_ANSWER, sent.type);
        assertEquals(42, sent.source);
        assertEquals(5, sent.target);
        assertEquals("answer-sdp", sent.data);
    }

    @Test
    void offerHandshakeFailureReportsError() {
        sig.simulateMessage(new SignalMessage(SignalMessage.TYPE_OFFER, 5, 42, "offer-sdp"));
        pc.lastHandleOfferCallback.onFailure("handshake error");

        assertEquals(1, listener.errors.size());
        assertTrue(listener.errors.get(0).contains("handshake failed"));
    }

    @Test
    void offerInitFailureReportsError() {
        pc.initializeResult = false;
        sig.simulateMessage(new SignalMessage(SignalMessage.TYPE_OFFER, 5, 42, "offer-sdp"));

        assertEquals(1, listener.errors.size());
        assertTrue(listener.errors.get(0).contains("factory initialization failed"));
    }

    @Test
    void offerNullPcReportsError() {
        pc.lastCreatedPc = null;
        sig.simulateMessage(new SignalMessage(SignalMessage.TYPE_OFFER, 5, 42, "offer-sdp"));

        assertEquals(1, listener.errors.size());
        assertTrue(listener.errors.get(0).contains("Failed to create peer connection"));
    }

    // --- Answer ---

    @Test
    void answerSetsRemoteDescription() {
        sig.simulateMessage(new SignalMessage(SignalMessage.TYPE_CONNECT_REQUEST, 5, 42, ""));
        sig.simulateMessage(new SignalMessage(SignalMessage.TYPE_ANSWER, 5, 42, "answer-sdp"));

        assertTrue(pc.calls.contains("setRemoteAnswer"));
        assertEquals("answer-sdp", pc.lastRemoteAnswerSdp);
    }

    @Test
    void answerForUnknownPeerIsIgnored() {
        sig.simulateMessage(new SignalMessage(SignalMessage.TYPE_ANSWER, 99, 42, "sdp"));
        assertFalse(pc.calls.contains("setRemoteAnswer"));
    }

    // --- ICE ---

    @Test
    void iceAddsCandidate() {
        sig.simulateMessage(new SignalMessage(SignalMessage.TYPE_CONNECT_REQUEST, 5, 42, ""));
        sig.simulateMessage(new SignalMessage(SignalMessage.TYPE_ICE, 5, 42, "{\"candidate\":\"c\"}"));

        assertTrue(pc.calls.contains("addIceCandidate"));
        assertEquals("{\"candidate\":\"c\"}", pc.lastIceCandidateJson);
    }

    @Test
    void iceForUnknownPeerIsIgnored() {
        sig.simulateMessage(new SignalMessage(SignalMessage.TYPE_ICE, 99, 42, "{\"c\":\"d\"}"));
        assertFalse(pc.calls.contains("addIceCandidate"));
    }

    // --- Full flow ---

    @Test
    void fullOfferAnswerFlow() {
        // Peer 5 sends connect request -> we create offer
        sig.simulateMessage(new SignalMessage(SignalMessage.TYPE_CONNECT_REQUEST, 5, 42, ""));
        assertTrue(pc.calls.contains("createOffer"));

        // Offer callback sends offer
        pc.lastOfferCallback.onSuccess("our-offer");
        SignalMessage offerMsg = sig.sentMessages.get(sig.sentMessages.size() - 1);
        assertEquals(SignalMessage.TYPE_OFFER, offerMsg.type);

        // Receive answer
        sig.simulateMessage(new SignalMessage(SignalMessage.TYPE_ANSWER, 5, 42, "their-answer"));
        assertTrue(pc.calls.contains("setRemoteAnswer"));
        assertEquals("their-answer", pc.lastRemoteAnswerSdp);

        // Receive ICE candidate
        sig.simulateMessage(new SignalMessage(SignalMessage.TYPE_ICE, 5, 42, "{\"candidate\":\"ice1\"}"));
        assertTrue(pc.calls.contains("addIceCandidate"));
    }

    @Test
    void multiplePeersAreIndependent() {
        // Create two peers
        sig.simulateMessage(new SignalMessage(SignalMessage.TYPE_CONNECT_REQUEST, 5, 42, ""));
        sig.simulateMessage(new SignalMessage(SignalMessage.TYPE_CONNECT_REQUEST, 7, 42, ""));

        assertEquals(2, client.getPeers().size());

        BaseWebRTCClient.PeerState peer5 =
                (BaseWebRTCClient.PeerState) client.getPeers().get(Integer.valueOf(5));
        BaseWebRTCClient.PeerState peer7 =
                (BaseWebRTCClient.PeerState) client.getPeers().get(Integer.valueOf(7));

        assertNotNull(peer5);
        assertNotNull(peer7);
        assertEquals(5, peer5.peerId);
        assertEquals(7, peer7.peerId);
    }

    @Test
    void createPeerConnectionExceptionReportsError() {
        // Make createPeerConnection throw
        pc.lastCreatedPc = null; // will return null -> reports error
        sig.simulateMessage(new SignalMessage(SignalMessage.TYPE_CONNECT_REQUEST, 5, 42, ""));

        assertEquals(1, listener.errors.size());
    }

    @Test
    void connectToPeerSendsConnectRequestWithCorrectIds() {
        client.connectToPeer(10);

        assertEquals(1, sig.sentMessages.size());
        SignalMessage sent = sig.sentMessages.get(sig.sentMessages.size() - 1);
        assertEquals(SignalMessage.TYPE_CONNECT_REQUEST, sent.type);
        assertEquals(42, sent.source);
        assertEquals(10, sent.target);
    }

    @Test
    void signalingOnErrorNotifiesListener() {
        // Get the signaling handler and trigger error
        // We need to test through signaling provider's error handler
        // The connect() already wired this up, so let's simulate a signaling error
        sig.handler.onError("ws failure");

        assertEquals(1, listener.errors.size());
        assertTrue(listener.errors.get(0).contains("Signaling"));
    }
}
