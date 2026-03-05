package com.github.satori87.gdx.webrtc.server;

import com.github.satori87.gdx.webrtc.SignalMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WebRTCSignalingServerTest {

    private WebRTCSignalingServer server;

    /** Mock implementation of SignalingConnection for testing. */
    static class MockSignalingConnection implements SignalingConnection {
        final List<String> sentMessages = new ArrayList<String>();
        boolean open = true;
        final String id;

        MockSignalingConnection(String id) {
            this.id = id;
        }

        public void send(String text) {
            sentMessages.add(text);
        }

        public boolean isOpen() {
            return open;
        }

        public InetSocketAddress getRemoteSocketAddress() {
            return new InetSocketAddress("127.0.0.1", 5000);
        }

        @Override
        public int hashCode() {
            return id.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof MockSignalingConnection)) return false;
            return id.equals(((MockSignalingConnection) obj).id);
        }
    }

    @BeforeEach
    void setUp() {
        server = new WebRTCSignalingServer(9090);
    }

    // --- Constructor tests ---

    @Test
    void constructorWithPort() {
        assertEquals(9090, server.getConfig().port);
    }

    @Test
    void constructorWithConfig() {
        SignalingServerConfig config = new SignalingServerConfig();
        config.port = 8080;
        config.connectionLostTimeout = 60;
        WebRTCSignalingServer s = new WebRTCSignalingServer(config);
        assertEquals(8080, s.getConfig().port);
        assertEquals(60, s.getConfig().connectionLostTimeout);
    }

    // --- handleOpen tests ---

    @Test
    void handleOpenAssignsPeerId() {
        MockSignalingConnection conn = new MockSignalingConnection("a");
        server.handleOpen(conn, "");
        assertEquals(1, server.getPeerCount());
        assertTrue(server.getConnToPeer().containsKey(conn));
    }

    @Test
    void handleOpenSendsWelcome() {
        MockSignalingConnection conn = new MockSignalingConnection("a");
        server.handleOpen(conn, "");
        assertEquals(1, conn.sentMessages.size());
        SignalMessage welcome = SignalMessage.fromJson(conn.sentMessages.get(0));
        assertNotNull(welcome);
        assertEquals(SignalMessage.TYPE_WELCOME, welcome.type);
        assertTrue(welcome.target > 0); // target is the assigned peer ID
    }

    @Test
    void handleOpenAssignsIncrementingPeerIds() {
        MockSignalingConnection conn1 = new MockSignalingConnection("a");
        MockSignalingConnection conn2 = new MockSignalingConnection("b");
        server.handleOpen(conn1, "");
        server.handleOpen(conn2, "");

        int peerId1 = server.getConnToPeer().get(conn1);
        int peerId2 = server.getConnToPeer().get(conn2);
        assertEquals(peerId1 + 1, peerId2);
    }

    @Test
    void handleOpenNotifiesNewPeerAboutExistingPeers() {
        MockSignalingConnection conn1 = new MockSignalingConnection("a");
        MockSignalingConnection conn2 = new MockSignalingConnection("b");
        server.handleOpen(conn1, "");
        server.handleOpen(conn2, "");

        // conn2 should receive: WELCOME + PEER_JOINED(conn1)
        assertEquals(2, conn2.sentMessages.size());
        SignalMessage welcome = SignalMessage.fromJson(conn2.sentMessages.get(0));
        assertEquals(SignalMessage.TYPE_WELCOME, welcome.type);

        SignalMessage peerJoined = SignalMessage.fromJson(conn2.sentMessages.get(1));
        assertEquals(SignalMessage.TYPE_PEER_JOINED, peerJoined.type);
        assertEquals(server.getConnToPeer().get(conn1).intValue(), peerJoined.source);
    }

    @Test
    void handleOpenBroadcastsPeerJoinedToExisting() {
        MockSignalingConnection conn1 = new MockSignalingConnection("a");
        MockSignalingConnection conn2 = new MockSignalingConnection("b");
        server.handleOpen(conn1, "");
        conn1.sentMessages.clear(); // clear welcome

        server.handleOpen(conn2, "");

        // conn1 should receive PEER_JOINED for conn2
        assertEquals(1, conn1.sentMessages.size());
        SignalMessage peerJoined = SignalMessage.fromJson(conn1.sentMessages.get(0));
        assertEquals(SignalMessage.TYPE_PEER_JOINED, peerJoined.type);
        assertEquals(server.getConnToPeer().get(conn2).intValue(), peerJoined.source);
    }

    @Test
    void handleOpenWithThreePeers() {
        MockSignalingConnection conn1 = new MockSignalingConnection("a");
        MockSignalingConnection conn2 = new MockSignalingConnection("b");
        MockSignalingConnection conn3 = new MockSignalingConnection("c");
        server.handleOpen(conn1, "");
        server.handleOpen(conn2, "");
        server.handleOpen(conn3, "");

        assertEquals(3, server.getPeerCount());
        // conn3 should receive: WELCOME + PEER_JOINED(1) + PEER_JOINED(2)
        assertEquals(3, conn3.sentMessages.size());
    }

    // --- handleClose tests ---

    @Test
    void handleCloseRemovesPeer() {
        MockSignalingConnection conn = new MockSignalingConnection("a");
        server.handleOpen(conn, "");
        assertEquals(1, server.getPeerCount());

        server.handleClose(conn);
        assertEquals(0, server.getPeerCount());
        assertFalse(server.getConnToPeer().containsKey(conn));
    }

    @Test
    void handleCloseBroadcastsPeerLeft() {
        MockSignalingConnection conn1 = new MockSignalingConnection("a");
        MockSignalingConnection conn2 = new MockSignalingConnection("b");
        server.handleOpen(conn1, "");
        server.handleOpen(conn2, "");
        conn1.sentMessages.clear();

        int peerId2 = server.getConnToPeer().get(conn2);
        server.handleClose(conn2);

        // conn1 should receive PEER_LEFT
        assertEquals(1, conn1.sentMessages.size());
        SignalMessage peerLeft = SignalMessage.fromJson(conn1.sentMessages.get(0));
        assertEquals(SignalMessage.TYPE_PEER_LEFT, peerLeft.type);
        assertEquals(peerId2, peerLeft.source);
    }

    @Test
    void handleCloseForUnknownConnectionDoesNothing() {
        MockSignalingConnection unknown = new MockSignalingConnection("unknown");
        server.handleClose(unknown); // should not throw
        assertEquals(0, server.getPeerCount());
    }

    // --- handleMessage tests ---

    @Test
    void handleMessageRelaysToTarget() {
        MockSignalingConnection conn1 = new MockSignalingConnection("a");
        MockSignalingConnection conn2 = new MockSignalingConnection("b");
        server.handleOpen(conn1, "");
        server.handleOpen(conn2, "");
        int peerId1 = server.getConnToPeer().get(conn1);
        int peerId2 = server.getConnToPeer().get(conn2);
        conn2.sentMessages.clear();

        // conn1 sends an OFFER to conn2
        SignalMessage offer = new SignalMessage(
                SignalMessage.TYPE_OFFER, peerId1, peerId2, "sdp-data");
        server.handleMessage(conn1, offer.toJson());

        assertEquals(1, conn2.sentMessages.size());
        SignalMessage relayed = SignalMessage.fromJson(conn2.sentMessages.get(0));
        assertEquals(SignalMessage.TYPE_OFFER, relayed.type);
        assertEquals(peerId1, relayed.source); // source stamped
        assertEquals("sdp-data", relayed.data);
    }

    @Test
    void handleMessageStampsSourcePeerId() {
        MockSignalingConnection conn1 = new MockSignalingConnection("a");
        MockSignalingConnection conn2 = new MockSignalingConnection("b");
        server.handleOpen(conn1, "");
        server.handleOpen(conn2, "");
        int peerId1 = server.getConnToPeer().get(conn1);
        int peerId2 = server.getConnToPeer().get(conn2);
        conn2.sentMessages.clear();

        // Send with wrong source - server should override
        SignalMessage msg = new SignalMessage(
                SignalMessage.TYPE_ANSWER, 999, peerId2, "answer-data");
        server.handleMessage(conn1, msg.toJson());

        SignalMessage relayed = SignalMessage.fromJson(conn2.sentMessages.get(0));
        assertEquals(peerId1, relayed.source); // stamped with actual source
    }

    @Test
    void handleMessageSendsErrorForUnknownTarget() {
        MockSignalingConnection conn1 = new MockSignalingConnection("a");
        server.handleOpen(conn1, "");
        int peerId1 = server.getConnToPeer().get(conn1);
        conn1.sentMessages.clear();

        SignalMessage msg = new SignalMessage(
                SignalMessage.TYPE_OFFER, peerId1, 999, "data");
        server.handleMessage(conn1, msg.toJson());

        assertEquals(1, conn1.sentMessages.size());
        SignalMessage err = SignalMessage.fromJson(conn1.sentMessages.get(0));
        assertEquals(SignalMessage.TYPE_ERROR, err.type);
        assertTrue(err.data.contains("999"));
    }

    @Test
    void handleMessageSendsErrorForClosedTarget() {
        MockSignalingConnection conn1 = new MockSignalingConnection("a");
        MockSignalingConnection conn2 = new MockSignalingConnection("b");
        server.handleOpen(conn1, "");
        server.handleOpen(conn2, "");
        int peerId2 = server.getConnToPeer().get(conn2);
        conn1.sentMessages.clear();

        conn2.open = false; // mark as closed

        SignalMessage msg = new SignalMessage(
                SignalMessage.TYPE_OFFER, 0, peerId2, "data");
        server.handleMessage(conn1, msg.toJson());

        assertEquals(1, conn1.sentMessages.size());
        SignalMessage err = SignalMessage.fromJson(conn1.sentMessages.get(0));
        assertEquals(SignalMessage.TYPE_ERROR, err.type);
    }

    @Test
    void handleMessageHandlesMalformedJson() {
        MockSignalingConnection conn = new MockSignalingConnection("a");
        server.handleOpen(conn, "");
        conn.sentMessages.clear();

        // "not-valid-json" is longer than 2 chars, so fromJson returns a message
        // with type=0, source=0, target=0. The server will try to relay to target 0
        // which doesn't exist, resulting in an ERROR response.
        server.handleMessage(conn, "not-valid-json");
        assertEquals(1, conn.sentMessages.size());
        SignalMessage err = SignalMessage.fromJson(conn.sentMessages.get(0));
        assertEquals(SignalMessage.TYPE_ERROR, err.type);
    }

    @Test
    void handleMessageIgnoresNullFromJson() {
        MockSignalingConnection conn = new MockSignalingConnection("a");
        server.handleOpen(conn, "");
        conn.sentMessages.clear();

        // fromJson returns null for very short strings
        server.handleMessage(conn, "");
        assertEquals(0, conn.sentMessages.size());
    }

    @Test
    void handleMessageIgnoresUnknownSender() {
        MockSignalingConnection unknown = new MockSignalingConnection("unknown");
        SignalMessage msg = new SignalMessage(SignalMessage.TYPE_OFFER, 0, 1, "data");
        server.handleMessage(unknown, msg.toJson()); // should not throw
    }

    // --- PEER_LIST tests ---

    @Test
    void handleMessagePeerListReturnsOtherPeers() {
        MockSignalingConnection conn1 = new MockSignalingConnection("a");
        MockSignalingConnection conn2 = new MockSignalingConnection("b");
        MockSignalingConnection conn3 = new MockSignalingConnection("c");
        server.handleOpen(conn1, "");
        server.handleOpen(conn2, "");
        server.handleOpen(conn3, "");
        int peerId1 = server.getConnToPeer().get(conn1);
        int peerId2 = server.getConnToPeer().get(conn2);
        int peerId3 = server.getConnToPeer().get(conn3);
        conn1.sentMessages.clear();

        SignalMessage listReq = new SignalMessage(
                SignalMessage.TYPE_PEER_LIST, peerId1, 0, "");
        server.handleMessage(conn1, listReq.toJson());

        assertEquals(1, conn1.sentMessages.size());
        SignalMessage listResp = SignalMessage.fromJson(conn1.sentMessages.get(0));
        assertEquals(SignalMessage.TYPE_PEER_LIST, listResp.type);
        // Should contain peerId2 and peerId3 but not peerId1
        String data = listResp.data;
        assertTrue(data.contains(String.valueOf(peerId2)));
        assertTrue(data.contains(String.valueOf(peerId3)));
        assertFalse(data.contains(String.valueOf(peerId1)));
    }

    @Test
    void handleMessagePeerListEmptyWhenAlone() {
        MockSignalingConnection conn = new MockSignalingConnection("a");
        server.handleOpen(conn, "");
        int peerId = server.getConnToPeer().get(conn);
        conn.sentMessages.clear();

        SignalMessage listReq = new SignalMessage(
                SignalMessage.TYPE_PEER_LIST, peerId, 0, "");
        server.handleMessage(conn, listReq.toJson());

        SignalMessage listResp = SignalMessage.fromJson(conn.sentMessages.get(0));
        assertEquals("", listResp.data);
    }

    // --- ICE relay ---

    @Test
    void handleMessageRelaysIceCandidate() {
        MockSignalingConnection conn1 = new MockSignalingConnection("a");
        MockSignalingConnection conn2 = new MockSignalingConnection("b");
        server.handleOpen(conn1, "");
        server.handleOpen(conn2, "");
        int peerId1 = server.getConnToPeer().get(conn1);
        int peerId2 = server.getConnToPeer().get(conn2);
        conn2.sentMessages.clear();

        SignalMessage ice = new SignalMessage(
                SignalMessage.TYPE_ICE, peerId1, peerId2, "candidate:data");
        server.handleMessage(conn1, ice.toJson());

        assertEquals(1, conn2.sentMessages.size());
        SignalMessage relayed = SignalMessage.fromJson(conn2.sentMessages.get(0));
        assertEquals(SignalMessage.TYPE_ICE, relayed.type);
        assertEquals("candidate:data", relayed.data);
    }

    // --- CONNECT_REQUEST relay ---

    @Test
    void handleMessageRelaysConnectRequest() {
        MockSignalingConnection conn1 = new MockSignalingConnection("a");
        MockSignalingConnection conn2 = new MockSignalingConnection("b");
        server.handleOpen(conn1, "");
        server.handleOpen(conn2, "");
        int peerId1 = server.getConnToPeer().get(conn1);
        int peerId2 = server.getConnToPeer().get(conn2);
        conn2.sentMessages.clear();

        SignalMessage connectReq = new SignalMessage(
                SignalMessage.TYPE_CONNECT_REQUEST, peerId1, peerId2, "");
        server.handleMessage(conn1, connectReq.toJson());

        assertEquals(1, conn2.sentMessages.size());
        SignalMessage relayed = SignalMessage.fromJson(conn2.sentMessages.get(0));
        assertEquals(SignalMessage.TYPE_CONNECT_REQUEST, relayed.type);
        assertEquals(peerId1, relayed.source);
    }

    // --- getPeerCount ---

    @Test
    void getPeerCountInitiallyZero() {
        assertEquals(0, server.getPeerCount());
    }

    @Test
    void getPeerCountTracksConnections() {
        MockSignalingConnection conn1 = new MockSignalingConnection("a");
        MockSignalingConnection conn2 = new MockSignalingConnection("b");
        server.handleOpen(conn1, "");
        assertEquals(1, server.getPeerCount());
        server.handleOpen(conn2, "");
        assertEquals(2, server.getPeerCount());
        server.handleClose(conn1);
        assertEquals(1, server.getPeerCount());
        server.handleClose(conn2);
        assertEquals(0, server.getPeerCount());
    }

    // --- extractRoom tests ---

    @Test
    void extractRoomFromNull() {
        assertEquals("", WebRTCSignalingServer.extractRoom(null));
    }

    @Test
    void extractRoomNoQueryString() {
        assertEquals("", WebRTCSignalingServer.extractRoom("/"));
    }

    @Test
    void extractRoomWithRoomParam() {
        assertEquals("myroom", WebRTCSignalingServer.extractRoom("/?room=myroom"));
    }

    @Test
    void extractRoomWithMultipleParams() {
        assertEquals("abc", WebRTCSignalingServer.extractRoom("/?foo=bar&room=abc&baz=1"));
    }

    @Test
    void extractRoomNoRoomParam() {
        assertEquals("", WebRTCSignalingServer.extractRoom("/?foo=bar"));
    }

    // --- Room isolation tests ---

    @Test
    void peersInDifferentRoomsDontSeeEachOther() {
        MockSignalingConnection connA = new MockSignalingConnection("a");
        MockSignalingConnection connB = new MockSignalingConnection("b");
        server.handleOpen(connA, "room1");
        server.handleOpen(connB, "room2");

        // connA should only get WELCOME (no PEER_JOINED for connB)
        assertEquals(1, connA.sentMessages.size());
        SignalMessage welcome = SignalMessage.fromJson(connA.sentMessages.get(0));
        assertEquals(SignalMessage.TYPE_WELCOME, welcome.type);

        // connB should only get WELCOME (no PEER_JOINED for connA)
        assertEquals(1, connB.sentMessages.size());
        welcome = SignalMessage.fromJson(connB.sentMessages.get(0));
        assertEquals(SignalMessage.TYPE_WELCOME, welcome.type);
    }

    @Test
    void peersInSameRoomSeeEachOther() {
        MockSignalingConnection connA = new MockSignalingConnection("a");
        MockSignalingConnection connB = new MockSignalingConnection("b");
        server.handleOpen(connA, "room1");
        server.handleOpen(connB, "room1");

        // connB should get WELCOME + PEER_JOINED(connA)
        assertEquals(2, connB.sentMessages.size());
        // connA should get WELCOME + PEER_JOINED(connB)
        assertEquals(2, connA.sentMessages.size());
    }

    @Test
    void peerLeftOnlySentToSameRoom() {
        MockSignalingConnection connA = new MockSignalingConnection("a");
        MockSignalingConnection connB = new MockSignalingConnection("b");
        MockSignalingConnection connC = new MockSignalingConnection("c");
        server.handleOpen(connA, "room1");
        server.handleOpen(connB, "room1");
        server.handleOpen(connC, "room2");
        connA.sentMessages.clear();
        connC.sentMessages.clear();

        server.handleClose(connB);

        // connA (same room) should get PEER_LEFT
        assertEquals(1, connA.sentMessages.size());
        SignalMessage left = SignalMessage.fromJson(connA.sentMessages.get(0));
        assertEquals(SignalMessage.TYPE_PEER_LEFT, left.type);

        // connC (different room) should get nothing
        assertEquals(0, connC.sentMessages.size());
    }

    @Test
    void crossRoomRelayBlocked() {
        MockSignalingConnection connA = new MockSignalingConnection("a");
        MockSignalingConnection connB = new MockSignalingConnection("b");
        server.handleOpen(connA, "room1");
        server.handleOpen(connB, "room2");
        int peerIdA = server.getConnToPeer().get(connA);
        int peerIdB = server.getConnToPeer().get(connB);
        connA.sentMessages.clear();
        connB.sentMessages.clear();

        // connA tries to send to connB (different room)
        SignalMessage offer = new SignalMessage(
                SignalMessage.TYPE_OFFER, peerIdA, peerIdB, "sdp");
        server.handleMessage(connA, offer.toJson());

        // connB should not receive anything
        assertEquals(0, connB.sentMessages.size());
        // connA should get error
        assertEquals(1, connA.sentMessages.size());
        SignalMessage err = SignalMessage.fromJson(connA.sentMessages.get(0));
        assertEquals(SignalMessage.TYPE_ERROR, err.type);
    }

    @Test
    void peerListOnlyReturnsSameRoom() {
        MockSignalingConnection connA = new MockSignalingConnection("a");
        MockSignalingConnection connB = new MockSignalingConnection("b");
        MockSignalingConnection connC = new MockSignalingConnection("c");
        server.handleOpen(connA, "room1");
        server.handleOpen(connB, "room1");
        server.handleOpen(connC, "room2");
        int peerIdA = server.getConnToPeer().get(connA);
        int peerIdB = server.getConnToPeer().get(connB);
        int peerIdC = server.getConnToPeer().get(connC);
        connA.sentMessages.clear();

        SignalMessage listReq = new SignalMessage(
                SignalMessage.TYPE_PEER_LIST, peerIdA, 0, "");
        server.handleMessage(connA, listReq.toJson());

        SignalMessage listResp = SignalMessage.fromJson(connA.sentMessages.get(0));
        assertTrue(listResp.data.contains(String.valueOf(peerIdB)));
        assertFalse(listResp.data.contains(String.valueOf(peerIdC)));
    }

    @Test
    void defaultRoomBackwardCompatible() {
        // Peers with no room (empty string) should see each other
        MockSignalingConnection connA = new MockSignalingConnection("a");
        MockSignalingConnection connB = new MockSignalingConnection("b");
        server.handleOpen(connA, "");
        server.handleOpen(connB, "");

        // connB should get WELCOME + PEER_JOINED
        assertEquals(2, connB.sentMessages.size());
    }
}
