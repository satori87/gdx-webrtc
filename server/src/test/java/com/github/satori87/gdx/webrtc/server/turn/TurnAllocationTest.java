package com.github.satori87.gdx.webrtc.server.turn;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.*;

class TurnAllocationTest {

    private DatagramSocket relaySocket;
    private TurnAllocation alloc;
    private InetSocketAddress clientAddr;
    private InetSocketAddress relayAddr;
    private byte[] key;

    @BeforeEach
    void setUp() throws Exception {
        relaySocket = new DatagramSocket(0);
        clientAddr = new InetSocketAddress(InetAddress.getLoopbackAddress(), 5000);
        relayAddr = new InetSocketAddress(InetAddress.getLoopbackAddress(), relaySocket.getLocalPort());
        key = StunMessage.computeKey("user", "realm", "pass");
        alloc = new TurnAllocation(clientAddr, relayAddr, relaySocket, key, 600);
    }

    @AfterEach
    void tearDown() {
        if (relaySocket != null && !relaySocket.isClosed()) {
            relaySocket.close();
        }
    }

    @Test
    void newAllocationIsNotExpired() {
        assertFalse(alloc.isExpired());
    }

    @Test
    void allocationWithZeroLifetimeIsExpired() throws Exception {
        DatagramSocket s = new DatagramSocket(0);
        try {
            TurnAllocation expired = new TurnAllocation(clientAddr, relayAddr, s, key, 0);
            assertTrue(expired.isExpired());
        } finally {
            s.close();
        }
    }

    @Test
    void refreshExtendsLifetime() {
        alloc.refresh(3600);
        assertFalse(alloc.isExpired());
    }

    @Test
    void refreshWithZeroExpiresImmediately() {
        alloc.refresh(0);
        assertTrue(alloc.isExpired());
    }

    @Test
    void refreshWithNegativeExpiresImmediately() {
        alloc.refresh(-1);
        assertTrue(alloc.isExpired());
    }

    @Test
    void addAndCheckPermission() throws Exception {
        InetAddress peer = InetAddress.getByName("10.0.0.1");
        assertFalse(alloc.hasPermission(peer));
        alloc.addPermission(peer);
        assertTrue(alloc.hasPermission(peer));
    }

    @Test
    void permissionForDifferentIpNotGranted() throws Exception {
        InetAddress peer1 = InetAddress.getByName("10.0.0.1");
        InetAddress peer2 = InetAddress.getByName("10.0.0.2");
        alloc.addPermission(peer1);
        assertTrue(alloc.hasPermission(peer1));
        assertFalse(alloc.hasPermission(peer2));
    }

    @Test
    void bindChannelAndLookup() {
        InetSocketAddress peerAddr = new InetSocketAddress("10.0.0.1", 6000);
        alloc.bindChannel(0x4000, peerAddr);
        assertEquals(peerAddr, alloc.channels.get(0x4000));
    }

    @Test
    void getChannelForPeer() {
        InetSocketAddress peerAddr = new InetSocketAddress("10.0.0.1", 6000);
        assertNull(alloc.getChannelForPeer(peerAddr));
        alloc.bindChannel(0x4001, peerAddr);
        assertEquals(Integer.valueOf(0x4001), alloc.getChannelForPeer(peerAddr));
    }

    @Test
    void multipleChannelBindings() {
        InetSocketAddress peer1 = new InetSocketAddress("10.0.0.1", 6000);
        InetSocketAddress peer2 = new InetSocketAddress("10.0.0.2", 7000);
        alloc.bindChannel(0x4000, peer1);
        alloc.bindChannel(0x4001, peer2);
        assertEquals(Integer.valueOf(0x4000), alloc.getChannelForPeer(peer1));
        assertEquals(Integer.valueOf(0x4001), alloc.getChannelForPeer(peer2));
    }

    @Test
    void closeClosesRelaySocket() {
        assertFalse(relaySocket.isClosed());
        alloc.close();
        assertTrue(relaySocket.isClosed());
    }

    @Test
    void closeIsIdempotent() {
        alloc.close();
        alloc.close(); // should not throw
        assertTrue(relaySocket.isClosed());
    }

    @Test
    void clientAddrIsStored() {
        assertEquals(clientAddr, alloc.clientAddr);
    }

    @Test
    void relayAddrIsStored() {
        assertEquals(relayAddr, alloc.relayAddr);
    }

    @Test
    void keyIsStored() {
        assertArrayEquals(key, alloc.key);
    }
}
