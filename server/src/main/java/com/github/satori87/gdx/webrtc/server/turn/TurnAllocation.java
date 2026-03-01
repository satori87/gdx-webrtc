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
 * Represents a single TURN allocation as defined in
 * <a href="https://tools.ietf.org/html/rfc5766#section-5">RFC 5766 Section 5</a>.
 *
 * <p>A TURN allocation is a data session between a client and the TURN
 * server, consisting of:</p>
 * <ul>
 *   <li>A dedicated relay UDP socket that receives data from peers</li>
 *   <li>A set of peer IP permissions controlling which peers may send
 *       data through the relay</li>
 *   <li>A map of channel bindings for the ChannelData fast-path</li>
 *   <li>An expiration time after which the allocation is automatically
 *       cleaned up</li>
 * </ul>
 *
 * <p>Each allocation is identified by the client's 5-tuple (transport
 * address). The {@link TurnServer} manages the lifecycle of allocations
 * and uses this class to track their state.</p>
 *
 * <p>This class is package-private and used only by {@link TurnServer}.</p>
 *
 * @see TurnServer
 */
class TurnAllocation {

    /**
     * The client's transport address (IP + port) that created this
     * allocation. All STUN messages from this address are associated
     * with this allocation.
     */
    final InetSocketAddress clientAddr;

    /**
     * The relay transport address allocated on the server side.
     * Peers send data to this address, and it is reported to the
     * client in the XOR-RELAYED-ADDRESS attribute.
     */
    final InetSocketAddress relayAddr;

    /**
     * The UDP socket bound to the relay port. Used to send and receive
     * data on behalf of the client to/from peers.
     */
    final DatagramSocket relaySocket;

    /**
     * Absolute timestamp (in milliseconds since epoch) at which this
     * allocation expires. Refreshed by TURN Refresh requests.
     */
    long expiresAt;

    /**
     * The HMAC-SHA1 key derived from the client's long-term credentials,
     * used for MESSAGE-INTEGRITY verification on subsequent requests
     * associated with this allocation.
     */
    final byte[] key;

    /**
     * Set of peer IP addresses that have been granted permission to send
     * data through this allocation. Permissions are installed via
     * CreatePermission requests and implicitly via ChannelBind.
     */
    final Set<InetAddress> permissions = new HashSet<InetAddress>();

    /**
     * Map from channel number to peer transport address. Enables the
     * ChannelData fast-path framing for bound peers.
     */
    final Map<Integer, InetSocketAddress> channels = new ConcurrentHashMap<Integer, InetSocketAddress>();

    /**
     * Reverse lookup map from peer address string ({@code "ip:port"}) to
     * channel number. Used to determine whether incoming peer data should
     * be forwarded as ChannelData or as a Data indication.
     */
    final Map<String, Integer> reverseChannels = new ConcurrentHashMap<String, Integer>();

    /**
     * Creates a new TURN allocation.
     *
     * @param clientAddr   the client's transport address
     * @param relayAddr    the allocated relay transport address
     * @param relaySocket  the UDP socket bound to the relay port
     * @param key          the HMAC-SHA1 key for integrity verification
     * @param lifetimeSecs the initial allocation lifetime in seconds
     */
    TurnAllocation(InetSocketAddress clientAddr, InetSocketAddress relayAddr,
                   DatagramSocket relaySocket, byte[] key, int lifetimeSecs) {
        this.clientAddr = clientAddr;
        this.relayAddr = relayAddr;
        this.relaySocket = relaySocket;
        this.key = key;
        this.expiresAt = System.currentTimeMillis() + lifetimeSecs * 1000L;
    }

    /**
     * Checks whether this allocation has expired.
     *
     * @return {@code true} if the current time is at or past the
     *         expiration timestamp, {@code false} otherwise
     */
    boolean isExpired() {
        return System.currentTimeMillis() >= expiresAt;
    }

    /**
     * Refreshes the allocation's expiration time.
     *
     * <p>If the lifetime is zero or negative, the allocation is immediately
     * expired (used for deletion via TURN Refresh with lifetime=0).
     * Otherwise, the expiration is reset to the current time plus the
     * specified number of seconds.</p>
     *
     * @param lifetimeSecs the new lifetime in seconds; zero or negative
     *                     to expire immediately
     */
    void refresh(int lifetimeSecs) {
        if (lifetimeSecs <= 0) {
            expiresAt = 0;
        } else {
            expiresAt = System.currentTimeMillis() + lifetimeSecs * 1000L;
        }
    }

    /**
     * Installs a permission for the given peer IP address.
     *
     * <p>Once a permission is installed, the peer is allowed to send data
     * to the client through this allocation's relay address. Permissions
     * are checked by IP address only (not port), as specified in
     * RFC 5766 Section 8.</p>
     *
     * @param peer the peer's IP address to permit
     */
    void addPermission(InetAddress peer) {
        permissions.add(peer);
    }

    /**
     * Checks whether a permission exists for the given peer IP address.
     *
     * @param peer the peer's IP address to check
     * @return {@code true} if the peer has been granted permission,
     *         {@code false} otherwise
     */
    boolean hasPermission(InetAddress peer) {
        return permissions.contains(peer);
    }

    /**
     * Binds a channel number to a peer transport address.
     *
     * <p>Once bound, the ChannelData fast-path framing can be used for
     * communication with this peer, avoiding the overhead of full STUN
     * message encoding. A permission is implicitly installed for the
     * peer by the caller before invoking this method.</p>
     *
     * @param channelNumber the channel number to bind (must be in the
     *                      range {@link StunConstants#CHANNEL_MIN} to
     *                      {@link StunConstants#CHANNEL_MAX})
     * @param peerAddr      the peer's transport address to associate
     *                      with this channel
     */
    void bindChannel(int channelNumber, InetSocketAddress peerAddr) {
        channels.put(channelNumber, peerAddr);
        reverseChannels.put(addrKey(peerAddr), channelNumber);
    }

    /**
     * Looks up the channel number bound to the given peer address.
     *
     * @param peerAddr the peer's transport address
     * @return the channel number, or {@code null} if no channel is bound
     *         to this peer address
     */
    Integer getChannelForPeer(InetSocketAddress peerAddr) {
        return reverseChannels.get(addrKey(peerAddr));
    }

    /**
     * Closes this allocation by closing the relay UDP socket.
     *
     * <p>Once closed, no more data can be relayed through this allocation.
     * This method is safe to call multiple times.</p>
     */
    void close() {
        if (relaySocket != null && !relaySocket.isClosed()) {
            relaySocket.close();
        }
    }

    /**
     * Creates a string key from a socket address for use in hash maps.
     *
     * @param addr the socket address
     * @return a string in the format {@code "ip:port"}
     */
    private static String addrKey(InetSocketAddress addr) {
        return addr.getAddress().getHostAddress() + ":" + addr.getPort();
    }
}
