package com.github.satori87.gdx.webrtc.server.turn;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents a single TURN allocation (RFC 5766 section 5).
 * Each allocation has a dedicated relay UDP socket.
 */
class TurnAllocation {

    final InetSocketAddress clientAddr;
    final InetSocketAddress relayAddr;
    final DatagramSocket relaySocket;
    long expiresAt;
    final byte[] key;

    final Set<InetAddress> permissions = new HashSet<InetAddress>();
    final Map<Integer, InetSocketAddress> channels = new ConcurrentHashMap<Integer, InetSocketAddress>();
    final Map<String, Integer> reverseChannels = new ConcurrentHashMap<String, Integer>();

    TurnAllocation(InetSocketAddress clientAddr, InetSocketAddress relayAddr,
                   DatagramSocket relaySocket, byte[] key, int lifetimeSecs) {
        this.clientAddr = clientAddr;
        this.relayAddr = relayAddr;
        this.relaySocket = relaySocket;
        this.key = key;
        this.expiresAt = System.currentTimeMillis() + lifetimeSecs * 1000L;
    }

    boolean isExpired() {
        return System.currentTimeMillis() >= expiresAt;
    }

    void refresh(int lifetimeSecs) {
        if (lifetimeSecs <= 0) {
            expiresAt = 0;
        } else {
            expiresAt = System.currentTimeMillis() + lifetimeSecs * 1000L;
        }
    }

    void addPermission(InetAddress peer) {
        permissions.add(peer);
    }

    boolean hasPermission(InetAddress peer) {
        return permissions.contains(peer);
    }

    void bindChannel(int channelNumber, InetSocketAddress peerAddr) {
        channels.put(channelNumber, peerAddr);
        reverseChannels.put(addrKey(peerAddr), channelNumber);
    }

    Integer getChannelForPeer(InetSocketAddress peerAddr) {
        return reverseChannels.get(addrKey(peerAddr));
    }

    void close() {
        if (relaySocket != null && !relaySocket.isClosed()) {
            relaySocket.close();
        }
    }

    private static String addrKey(InetSocketAddress addr) {
        return addr.getAddress().getHostAddress() + ":" + addr.getPort();
    }
}
