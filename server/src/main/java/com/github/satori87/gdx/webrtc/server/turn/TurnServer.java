package com.github.satori87.gdx.webrtc.server.turn;

import com.github.satori87.gdx.webrtc.util.Log;

import java.net.*;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A minimal embedded TURN server implementing
 * <a href="https://tools.ietf.org/html/rfc5766">RFC 5766</a> over UDP.
 *
 * <p>This server enables WebRTC peers to relay media traffic when direct
 * peer-to-peer connections cannot be established (e.g. due to symmetric
 * NATs or restrictive firewalls). It implements the following STUN/TURN
 * methods:</p>
 * <ul>
 *   <li><b>Binding</b> (RFC 5389) -- returns the client's server-reflexive
 *       address via XOR-MAPPED-ADDRESS</li>
 *   <li><b>Allocate</b> (RFC 5766 Section 6) -- creates a relay allocation
 *       with a dedicated UDP socket and returns the relay address</li>
 *   <li><b>Refresh</b> (RFC 5766 Section 7) -- extends or deletes an
 *       existing allocation's lifetime</li>
 *   <li><b>CreatePermission</b> (RFC 5766 Section 9) -- installs a
 *       permission for a peer IP to send data through the relay</li>
 *   <li><b>ChannelBind</b> (RFC 5766 Section 11) -- binds a channel number
 *       to a peer address for the ChannelData fast-path</li>
 *   <li><b>Send</b> (RFC 5766 Section 10) -- relays data from the client
 *       to a permitted peer via the allocation's relay socket</li>
 *   <li><b>ChannelData</b> (RFC 5766 Section 11.4) -- fast-path framing
 *       for data sent to/from channel-bound peers</li>
 * </ul>
 *
 * <h3>Authentication</h3>
 * <p>Uses the STUN long-term credential mechanism (RFC 5389 Section 10.2).
 * A single fixed username/password pair is configured via {@link TurnConfig}.
 * Allocate requests without credentials receive a 401 challenge with a
 * server-generated nonce.</p>
 *
 * <h3>Threading model</h3>
 * <p>The server runs two daemon threads:</p>
 * <ul>
 *   <li>A main receive loop that processes incoming STUN messages and
 *       ChannelData frames</li>
 *   <li>A cleanup loop that periodically removes expired allocations
 *       (at the interval configured by {@link TurnConfig#cleanupIntervalMs})</li>
 * </ul>
 * <p>Each allocation also spawns a dedicated daemon thread to listen for
 * incoming peer data on the relay socket and forward it to the client.</p>
 *
 * <h3>Usage</h3>
 * <pre>
 * TurnConfig config = new TurnConfig();
 * config.port = 3478;
 * TurnServer turn = new TurnServer(config);
 * turn.start();
 * // ...
 * turn.stop();
 * </pre>
 *
 * @see TurnConfig
 * @see TurnAllocation
 * @see StunMessage
 * @see StunConstants
 */
public class TurnServer {

    /** Log prefix used for all console output from this server. */
    private static final String TAG = "[TURN] ";

    /** Configuration for this TURN server instance. */
    private final TurnConfig config;

    /** The primary UDP socket on which the server listens for STUN/TURN messages. */
    private DatagramSocket socket;

    /** Flag indicating whether the server is currently running. */
    private volatile boolean running;

    /** The main thread running the UDP receive loop. */
    private Thread mainThread;

    /** The background thread that periodically cleans up expired allocations. */
    private Thread cleanupThread;

    /**
     * Map from client address key ({@code "ip:port"}) to the client's
     * active TURN allocation. Each client can have at most one allocation.
     */
    private final Map<String, TurnAllocation> allocations = new ConcurrentHashMap<String, TurnAllocation>();

    /**
     * Map from client address key ({@code "ip:port"}) to the server-generated
     * nonce for the long-term credential challenge. Nonces are consumed after
     * a successful Allocate request.
     */
    private final Map<String, String> nonces = new ConcurrentHashMap<String, String>();

    /**
     * Pre-computed HMAC-SHA1 key derived from the configured credentials
     * ({@code MD5(username:realm:password)}).
     */
    private final byte[] authKey;

    /** Random number generator used for nonce and transaction ID generation. */
    private final Random random = new Random();

    /**
     * Creates a new TURN server with the given configuration.
     *
     * <p>The authentication key is pre-computed from the configured credentials.
     * The server does not start until {@link #start()} is called.</p>
     *
     * @param config the TURN server configuration
     */
    public TurnServer(TurnConfig config) {
        this.config = config;
        this.authKey = StunMessage.computeKey(config.username, config.realm, config.password);
    }

    /**
     * Starts the TURN server.
     *
     * <p>This method binds a UDP socket to the configured port, then starts
     * two daemon threads:</p>
     * <ul>
     *   <li><b>TurnServer-Main</b> -- the main receive loop that dispatches
     *       incoming STUN messages and ChannelData frames</li>
     *   <li><b>TurnServer-Cleanup</b> -- periodically removes expired
     *       allocations (at the interval configured by
     *       {@link TurnConfig#cleanupIntervalMs})</li>
     * </ul>
     *
     * <p>If the port cannot be bound, an error is logged and the method
     * returns without starting any threads.</p>
     */
    public void start() {
        try {
            socket = new DatagramSocket(config.port);
            socket.setReuseAddress(true);
        } catch (SocketException e) {
            Log.warn(TAG + "Failed to bind port " + config.port + ": " + e.getMessage());
            return;
        }

        running = true;

        mainThread = new Thread(new Runnable() {
            public void run() {
                TurnServer.this.mainLoop();
            }
        }, "TurnServer-Main");
        mainThread.setDaemon(true);
        mainThread.start();

        cleanupThread = new Thread(new Runnable() {
            public void run() {
                TurnServer.this.cleanupLoop();
            }
        }, "TurnServer-Cleanup");
        cleanupThread.setDaemon(true);
        cleanupThread.start();

        Log.info(TAG + "Started on port " + config.port);
    }

    /**
     * Stops the TURN server and releases all resources.
     *
     * <p>Closes the main UDP socket, closes all active allocations (including
     * their relay sockets), and clears the allocation map. The main and cleanup
     * threads will terminate as a result of the socket closure and the
     * {@code running} flag being set to {@code false}.</p>
     */
    public void stop() {
        running = false;
        if (socket != null && !socket.isClosed()) socket.close();
        for (TurnAllocation alloc : allocations.values()) {
            alloc.close();
        }
        allocations.clear();
        Log.info(TAG + "Stopped");
    }

    /**
     * Main UDP receive loop. Continuously receives datagrams and dispatches
     * them to {@link #handlePacket(byte[], int, InetSocketAddress)}.
     *
     * <p>This method runs on the {@code TurnServer-Main} daemon thread and
     * exits when the socket is closed or {@code running} becomes false.</p>
     */
    private void mainLoop() {
        byte[] buf = new byte[config.receiveBufferSize];
        while (running) {
            try {
                DatagramPacket pkt = new DatagramPacket(buf, buf.length);
                socket.receive(pkt);
                handlePacket(pkt.getData(), pkt.getLength(),
                        new InetSocketAddress(pkt.getAddress(), pkt.getPort()));
            } catch (SocketException e) {
                if (running) Log.warn(TAG + "Socket error: " + e.getMessage());
            } catch (Exception e) {
                Log.warn(TAG + "Error: " + e.getMessage());
            }
        }
    }

    /**
     * Periodic cleanup loop that removes expired allocations.
     *
     * <p>Runs at the configured cleanup interval on the {@code TurnServer-Cleanup} daemon thread.
     * Expired allocations have their relay sockets closed and are removed
     * from the allocation map.</p>
     */
    private void cleanupLoop() {
        while (running) {
            try {
                Thread.sleep(config.cleanupIntervalMs);
                Iterator<Map.Entry<String, TurnAllocation>> it = allocations.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<String, TurnAllocation> entry = it.next();
                    TurnAllocation alloc = entry.getValue();
                    if (alloc.isExpired()) {
                        Log.debug(TAG + "Allocation expired: " + entry.getKey());
                        alloc.close();
                        it.remove();
                    }
                }
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    /**
     * Dispatches an incoming UDP packet to the appropriate handler.
     *
     * <p>Packets are classified by their first byte:</p>
     * <ul>
     *   <li>If the first byte is in the range {@code 0x40-0x7F}, the packet
     *       is treated as ChannelData framing and routed to
     *       {@link #handleChannelData(byte[], int, InetSocketAddress)}</li>
     *   <li>Otherwise, the packet is parsed as a STUN message and routed to
     *       {@link #handleStunMessage(StunMessage, InetSocketAddress)}</li>
     * </ul>
     *
     * @param data   the raw packet data
     * @param length the number of valid bytes in the data array
     * @param from   the sender's transport address
     */
    private void handlePacket(byte[] data, int length, InetSocketAddress from) {
        if (length < 4) return;

        int firstByte = data[0] & 0xFF;
        if (firstByte >= 0x40 && firstByte <= 0x7F) {
            handleChannelData(data, length, from);
            return;
        }

        StunMessage msg = StunMessage.parse(data, 0, length);
        if (msg == null) return;

        handleStunMessage(msg, from);
    }

    /**
     * Routes a parsed STUN message to the appropriate method handler based
     * on the STUN method extracted from the message type.
     *
     * <p>Only request (class 0) and indication (class 1) messages are
     * processed; responses are silently ignored.</p>
     *
     * @param msg  the parsed STUN message
     * @param from the sender's transport address
     */
    private void handleStunMessage(StunMessage msg, InetSocketAddress from) {
        int method = StunConstants.getMethod(msg.messageType);
        int clazz = StunConstants.getClass(msg.messageType);

        if (clazz != 0 && clazz != 1) return;

        switch (method) {
            case StunConstants.METHOD_BINDING:
                handleBinding(msg, from);
                break;
            case StunConstants.METHOD_ALLOCATE:
                handleAllocate(msg, from);
                break;
            case StunConstants.METHOD_REFRESH:
                handleRefresh(msg, from);
                break;
            case StunConstants.METHOD_CREATE_PERMISSION:
                handleCreatePermission(msg, from);
                break;
            case StunConstants.METHOD_CHANNEL_BIND:
                handleChannelBind(msg, from);
                break;
            case StunConstants.METHOD_SEND:
                handleSend(msg, from);
                break;
            default:
                Log.debug(TAG + "Unknown method: 0x" + Integer.toHexString(method));
                break;
        }
    }

    /**
     * Handles a STUN Binding request (RFC 5389 Section 3).
     *
     * <p>Returns a success response containing the client's server-reflexive
     * address as an XOR-MAPPED-ADDRESS attribute. This allows the client
     * to discover its public IP and port as seen by the server.</p>
     *
     * @param req  the Binding request message
     * @param from the client's transport address
     */
    private void handleBinding(StunMessage req, InetSocketAddress from) {
        StunMessage resp = req.createSuccessResponse();
        resp.putXorMappedAddress(StunConstants.ATTR_XOR_MAPPED_ADDRESS, from);
        sendTo(resp.encode(), from);
    }

    /**
     * Handles a TURN Allocate request (RFC 5766 Section 6).
     *
     * <p>This method performs the full allocation flow:</p>
     * <ol>
     *   <li>Rejects duplicate allocations with a 437 error</li>
     *   <li>Challenges unauthenticated requests with a 401 response
     *       containing REALM and NONCE attributes</li>
     *   <li>Validates the nonce (438 if stale), username (401 if wrong),
     *       and MESSAGE-INTEGRITY (401 if bad credentials)</li>
     *   <li>Validates the REQUESTED-TRANSPORT is UDP (400 otherwise)</li>
     *   <li>Allocates a relay UDP socket on a random port</li>
     *   <li>Determines the server's public address for the relay</li>
     *   <li>Creates a {@link TurnAllocation} and starts a relay listener
     *       thread</li>
     *   <li>Returns a success response with XOR-RELAYED-ADDRESS,
     *       XOR-MAPPED-ADDRESS, and LIFETIME attributes</li>
     * </ol>
     *
     * @param req  the Allocate request message
     * @param from the client's transport address
     */
    private void handleAllocate(StunMessage req, InetSocketAddress from) {
        String clientKey = addrKey(from);

        if (allocations.containsKey(clientKey)) {
            StunMessage err = req.createErrorResponse(
                    StunConstants.ERR_ALLOCATION_MISMATCH, "Allocation exists");
            sendTo(err.encode(), from);
            return;
        }

        String username = req.getString(StunConstants.ATTR_USERNAME);
        String realm = req.getString(StunConstants.ATTR_REALM);
        String nonce = req.getString(StunConstants.ATTR_NONCE);

        if (username == null || realm == null || nonce == null) {
            String newNonce = generateNonce();
            nonces.put(clientKey, newNonce);

            StunMessage err = req.createErrorResponse(StunConstants.ERR_UNAUTHORIZED, "Unauthorized");
            err.putString(StunConstants.ATTR_REALM, config.realm);
            err.putString(StunConstants.ATTR_NONCE, newNonce);
            sendTo(err.encode(), from);
            return;
        }

        String expectedNonce = nonces.get(clientKey);
        if (expectedNonce == null || !expectedNonce.equals(nonce)) {
            String newNonce = generateNonce();
            nonces.put(clientKey, newNonce);

            StunMessage err = req.createErrorResponse(StunConstants.ERR_STALE_NONCE, "Stale nonce");
            err.putString(StunConstants.ATTR_REALM, config.realm);
            err.putString(StunConstants.ATTR_NONCE, newNonce);
            sendTo(err.encode(), from);
            return;
        }

        if (!config.username.equals(username)) {
            StunMessage err = req.createErrorResponse(StunConstants.ERR_UNAUTHORIZED, "Bad username");
            err.putString(StunConstants.ATTR_REALM, config.realm);
            err.putString(StunConstants.ATTR_NONCE, expectedNonce);
            sendTo(err.encode(), from);
            return;
        }

        byte[] key = StunMessage.computeKey(username, config.realm, config.password);
        if (!req.verifyIntegrity(key)) {
            StunMessage err = req.createErrorResponse(StunConstants.ERR_UNAUTHORIZED, "Bad credentials");
            err.putString(StunConstants.ATTR_REALM, config.realm);
            err.putString(StunConstants.ATTR_NONCE, expectedNonce);
            sendTo(err.encode(), from);
            return;
        }

        if (req.hasAttribute(StunConstants.ATTR_REQUESTED_TRANSPORT)) {
            int transport = req.getInt(StunConstants.ATTR_REQUESTED_TRANSPORT);
            int proto = (transport >> 24) & 0xFF;
            if (proto != StunConstants.TRANSPORT_UDP) {
                StunMessage err = req.createErrorResponse(
                        StunConstants.ERR_BAD_REQUEST, "Only UDP supported");
                sendTo(err.encodeWithIntegrity(key), from);
                return;
            }
        }

        DatagramSocket relaySocket;
        try {
            relaySocket = new DatagramSocket(0);
        } catch (SocketException e) {
            StunMessage err = req.createErrorResponse(
                    StunConstants.ERR_INSUFFICIENT_CAPACITY, "Cannot allocate relay");
            sendTo(err.encodeWithIntegrity(key), from);
            return;
        }

        InetAddress serverAddr;
        try {
            if ("0.0.0.0".equals(config.host)) {
                serverAddr = socket.getLocalAddress();
                if (serverAddr.isAnyLocalAddress()) {
                    serverAddr = InetAddress.getLocalHost();
                }
            } else {
                serverAddr = InetAddress.getByName(config.host);
            }
        } catch (Exception e) {
            serverAddr = socket.getLocalAddress();
        }

        InetSocketAddress relayAddr = new InetSocketAddress(serverAddr, relaySocket.getLocalPort());

        int lifetime = StunConstants.DEFAULT_LIFETIME;
        if (req.hasAttribute(StunConstants.ATTR_LIFETIME)) {
            lifetime = req.getInt(StunConstants.ATTR_LIFETIME);
            lifetime = Math.min(lifetime, StunConstants.MAX_LIFETIME);
            lifetime = Math.max(lifetime, config.minLifetime);
        }

        TurnAllocation alloc = new TurnAllocation(from, relayAddr, relaySocket, key, lifetime);
        allocations.put(clientKey, alloc);
        nonces.remove(clientKey);

        startRelayListener(alloc);

        StunMessage resp = req.createSuccessResponse();
        resp.putXorMappedAddress(StunConstants.ATTR_XOR_RELAYED_ADDRESS, relayAddr);
        resp.putXorMappedAddress(StunConstants.ATTR_XOR_MAPPED_ADDRESS, from);
        resp.putInt(StunConstants.ATTR_LIFETIME, lifetime);
        sendTo(resp.encodeWithIntegrity(key), from);

        Log.info(TAG + "Allocation created: " + clientKey + " -> relay " + relayAddr);
    }

    /**
     * Handles a TURN Refresh request (RFC 5766 Section 7).
     *
     * <p>Refreshes the lifetime of an existing allocation. If the requested
     * lifetime is zero, the allocation is deleted. The lifetime is clamped
     * to the range [{@link TurnConfig#minLifetime}, {@link StunConstants#MAX_LIFETIME}] seconds for
     * non-zero values.</p>
     *
     * <p>Returns a 437 error if no allocation exists for the client, or a
     * 401 error if the MESSAGE-INTEGRITY check fails.</p>
     *
     * @param req  the Refresh request message
     * @param from the client's transport address
     */
    private void handleRefresh(StunMessage req, InetSocketAddress from) {
        String clientKey = addrKey(from);
        TurnAllocation alloc = allocations.get(clientKey);
        if (alloc == null) {
            StunMessage err = req.createErrorResponse(
                    StunConstants.ERR_ALLOCATION_MISMATCH, "No allocation");
            sendTo(err.encode(), from);
            return;
        }

        if (!req.verifyIntegrity(alloc.key)) {
            StunMessage err = req.createErrorResponse(StunConstants.ERR_UNAUTHORIZED, "Bad integrity");
            sendTo(err.encode(), from);
            return;
        }

        int lifetime = StunConstants.DEFAULT_LIFETIME;
        if (req.hasAttribute(StunConstants.ATTR_LIFETIME)) {
            lifetime = req.getInt(StunConstants.ATTR_LIFETIME);
            if (lifetime > 0) {
                lifetime = Math.min(lifetime, StunConstants.MAX_LIFETIME);
                lifetime = Math.max(lifetime, config.minLifetime);
            }
        }

        if (lifetime == 0) {
            alloc.close();
            allocations.remove(clientKey);
            Log.debug(TAG + "Allocation deleted by refresh: " + clientKey);
        } else {
            alloc.refresh(lifetime);
        }

        StunMessage resp = req.createSuccessResponse();
        resp.putInt(StunConstants.ATTR_LIFETIME, lifetime);
        sendTo(resp.encodeWithIntegrity(alloc.key), from);
    }

    /**
     * Handles a TURN CreatePermission request (RFC 5766 Section 9).
     *
     * <p>Installs a permission for the peer IP specified in the
     * XOR-PEER-ADDRESS attribute. Once installed, the peer is allowed
     * to send data to the client through the relay. Permissions are
     * keyed by IP address only (not port).</p>
     *
     * <p>Returns a 437 error if no allocation exists, a 401 error if
     * integrity verification fails, or a 400 error if the peer address
     * attribute is missing.</p>
     *
     * @param req  the CreatePermission request message
     * @param from the client's transport address
     */
    private void handleCreatePermission(StunMessage req, InetSocketAddress from) {
        String clientKey = addrKey(from);
        TurnAllocation alloc = allocations.get(clientKey);
        if (alloc == null) {
            StunMessage err = req.createErrorResponse(
                    StunConstants.ERR_ALLOCATION_MISMATCH, "No allocation");
            sendTo(err.encode(), from);
            return;
        }

        if (!req.verifyIntegrity(alloc.key)) {
            StunMessage err = req.createErrorResponse(StunConstants.ERR_UNAUTHORIZED, "Bad integrity");
            sendTo(err.encode(), from);
            return;
        }

        InetSocketAddress peerAddr = req.getXorAddress(StunConstants.ATTR_XOR_PEER_ADDRESS);
        if (peerAddr == null) {
            StunMessage err = req.createErrorResponse(StunConstants.ERR_BAD_REQUEST, "No peer address");
            sendTo(err.encodeWithIntegrity(alloc.key), from);
            return;
        }

        alloc.addPermission(peerAddr.getAddress());

        StunMessage resp = req.createSuccessResponse();
        sendTo(resp.encodeWithIntegrity(alloc.key), from);
    }

    /**
     * Handles a TURN ChannelBind request (RFC 5766 Section 11).
     *
     * <p>Binds a channel number (range {@code 0x4000-0x7FFF}) to a peer
     * transport address. This enables the ChannelData fast-path framing,
     * which has lower overhead than Send/Data indications. A permission
     * is also implicitly installed for the peer.</p>
     *
     * <p>Returns appropriate error responses for missing allocation (437),
     * bad integrity (401), missing/invalid channel number (400), out-of-range
     * channel (400), or missing peer address (400).</p>
     *
     * @param req  the ChannelBind request message
     * @param from the client's transport address
     */
    private void handleChannelBind(StunMessage req, InetSocketAddress from) {
        String clientKey = addrKey(from);
        TurnAllocation alloc = allocations.get(clientKey);
        if (alloc == null) {
            StunMessage err = req.createErrorResponse(
                    StunConstants.ERR_ALLOCATION_MISMATCH, "No allocation");
            sendTo(err.encode(), from);
            return;
        }

        if (!req.verifyIntegrity(alloc.key)) {
            StunMessage err = req.createErrorResponse(StunConstants.ERR_UNAUTHORIZED, "Bad integrity");
            sendTo(err.encode(), from);
            return;
        }

        byte[] chanAttr = req.attributes.get(StunConstants.ATTR_CHANNEL_NUMBER);
        if (chanAttr == null || chanAttr.length < 4) {
            StunMessage err = req.createErrorResponse(StunConstants.ERR_BAD_REQUEST, "No channel number");
            sendTo(err.encodeWithIntegrity(alloc.key), from);
            return;
        }
        int channelNumber = ((chanAttr[0] & 0xFF) << 8) | (chanAttr[1] & 0xFF);

        if (channelNumber < StunConstants.CHANNEL_MIN || channelNumber > StunConstants.CHANNEL_MAX) {
            StunMessage err = req.createErrorResponse(StunConstants.ERR_BAD_REQUEST, "Invalid channel number");
            sendTo(err.encodeWithIntegrity(alloc.key), from);
            return;
        }

        InetSocketAddress peerAddr = req.getXorAddress(StunConstants.ATTR_XOR_PEER_ADDRESS);
        if (peerAddr == null) {
            StunMessage err = req.createErrorResponse(StunConstants.ERR_BAD_REQUEST, "No peer address");
            sendTo(err.encodeWithIntegrity(alloc.key), from);
            return;
        }

        alloc.addPermission(peerAddr.getAddress());
        alloc.bindChannel(channelNumber, peerAddr);

        StunMessage resp = req.createSuccessResponse();
        sendTo(resp.encodeWithIntegrity(alloc.key), from);
    }

    /**
     * Handles a TURN Send indication (RFC 5766 Section 10).
     *
     * <p>Relays the DATA attribute payload from the client to the peer
     * specified in the XOR-PEER-ADDRESS attribute, using the allocation's
     * relay socket. The send is silently dropped if no allocation exists,
     * the peer address or data is missing, or the peer does not have a
     * permission installed.</p>
     *
     * @param req  the Send indication message
     * @param from the client's transport address
     */
    private void handleSend(StunMessage req, InetSocketAddress from) {
        String clientKey = addrKey(from);
        TurnAllocation alloc = allocations.get(clientKey);
        if (alloc == null) return;

        InetSocketAddress peerAddr = req.getXorAddress(StunConstants.ATTR_XOR_PEER_ADDRESS);
        byte[] data = req.attributes.get(StunConstants.ATTR_DATA);
        if (peerAddr == null || data == null) return;

        if (!alloc.hasPermission(peerAddr.getAddress())) return;

        try {
            DatagramPacket pkt = new DatagramPacket(data, data.length, peerAddr);
            alloc.relaySocket.send(pkt);
        } catch (Exception e) {
            // Ignore relay send failures
        }
    }

    /**
     * Handles an incoming ChannelData frame (RFC 5766 Section 11.4).
     *
     * <p>ChannelData frames bypass the full STUN message encoding for
     * efficiency. The frame consists of a 4-byte header (2-byte channel
     * number + 2-byte data length) followed by the payload. The channel
     * number is looked up in the allocation to determine the peer address,
     * and the payload is forwarded via the relay socket.</p>
     *
     * @param data   the raw packet data
     * @param length the number of valid bytes
     * @param from   the client's transport address
     */
    private void handleChannelData(byte[] data, int length, InetSocketAddress from) {
        if (length < 4) return;

        int channelNumber = ((data[0] & 0xFF) << 8) | (data[1] & 0xFF);
        int dataLen = ((data[2] & 0xFF) << 8) | (data[3] & 0xFF);

        if (4 + dataLen > length) return;

        String clientKey = addrKey(from);
        TurnAllocation alloc = allocations.get(clientKey);
        if (alloc == null) return;

        InetSocketAddress peerAddr = alloc.channels.get(channelNumber);
        if (peerAddr == null) return;

        try {
            DatagramPacket pkt = new DatagramPacket(data, 4, dataLen, peerAddr);
            alloc.relaySocket.send(pkt);
        } catch (Exception e) {
            // Ignore relay send failures
        }
    }

    /**
     * Starts a daemon thread that listens for incoming peer data on the
     * allocation's relay socket and forwards it to the client.
     *
     * <p>Data from peers with a channel binding is forwarded using the
     * compact ChannelData framing. Data from peers without a channel
     * binding is forwarded as a TURN Data indication containing
     * XOR-PEER-ADDRESS and DATA attributes.</p>
     *
     * <p>The thread runs until the relay socket is closed, the allocation
     * expires, or the server is stopped. Socket timeouts (configured by
     * {@link TurnConfig#relayTimeoutMs}) are used to periodically check
     * the allocation's expiry status.</p>
     *
     * @param alloc the allocation whose relay socket to listen on
     */
    private void startRelayListener(final TurnAllocation alloc) {
        Thread t = new Thread(new Runnable() {
            public void run() {
                byte[] buf = new byte[config.receiveBufferSize];
                while (running && !alloc.relaySocket.isClosed() && !alloc.isExpired()) {
                    try {
                        DatagramPacket pkt = new DatagramPacket(buf, buf.length);
                        alloc.relaySocket.setSoTimeout(config.relayTimeoutMs);
                        alloc.relaySocket.receive(pkt);

                        InetSocketAddress peerAddr = new InetSocketAddress(pkt.getAddress(), pkt.getPort());

                        if (!alloc.hasPermission(peerAddr.getAddress())) continue;

                        Integer channel = alloc.getChannelForPeer(peerAddr);
                        if (channel != null) {
                            int dataLen = pkt.getLength();
                            int padded = dataLen + ((4 - (dataLen & 0x03)) & 0x03);
                            byte[] channelData = new byte[4 + padded];
                            channelData[0] = (byte) ((channel >> 8) & 0xFF);
                            channelData[1] = (byte) (channel & 0xFF);
                            channelData[2] = (byte) ((dataLen >> 8) & 0xFF);
                            channelData[3] = (byte) (dataLen & 0xFF);
                            System.arraycopy(buf, 0, channelData, 4, dataLen);
                            sendTo(channelData, alloc.clientAddr);
                        } else {
                            StunMessage dataInd = new StunMessage();
                            dataInd.messageType = StunConstants.DATA_INDICATION;
                            dataInd.transactionId = new byte[12];
                            random.nextBytes(dataInd.transactionId);
                            dataInd.putXorMappedAddress(StunConstants.ATTR_XOR_PEER_ADDRESS, peerAddr);
                            byte[] payload = new byte[pkt.getLength()];
                            System.arraycopy(buf, 0, payload, 0, pkt.getLength());
                            dataInd.attributes.put(StunConstants.ATTR_DATA, payload);
                            sendTo(dataInd.encode(), alloc.clientAddr);
                        }
                    } catch (SocketTimeoutException e) {
                        // Normal timeout, loop to check expiry
                    } catch (SocketException e) {
                        break;
                    } catch (Exception e) {
                        if (running) Log.warn(TAG + "Relay error: " + e.getMessage());
                    }
                }
            }
        }, "TurnRelay-" + alloc.relayAddr.getPort());
        t.setDaemon(true);
        t.start();
    }

    /**
     * Sends a UDP datagram to the specified address via the server's main
     * socket. Send failures are silently ignored.
     *
     * @param data the data to send
     * @param to   the destination transport address
     */
    private void sendTo(byte[] data, InetSocketAddress to) {
        try {
            DatagramPacket pkt = new DatagramPacket(data, data.length, to);
            socket.send(pkt);
        } catch (Exception e) {
            // Ignore send failures
        }
    }

    /**
     * Generates a random 32-character hexadecimal nonce string for use in
     * the STUN long-term credential challenge mechanism.
     *
     * @return a 32-character lowercase hex string
     */
    private String generateNonce() {
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        StringBuilder sb = new StringBuilder(32);
        for (int i = 0; i < bytes.length; i++) {
            sb.append(String.format("%02x", bytes[i] & 0xFF));
        }
        return sb.toString();
    }

    /**
     * Creates a string key from a socket address for use as a map key.
     *
     * @param addr the socket address
     * @return a string in the format {@code "ip:port"}
     */
    private static String addrKey(InetSocketAddress addr) {
        return addr.getAddress().getHostAddress() + ":" + addr.getPort();
    }
}
