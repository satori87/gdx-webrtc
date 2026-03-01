package com.github.satori87.gdx.webrtc.server.turn;

import java.net.*;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Minimal embedded TURN server (RFC 5766, UDP only).
 * Handles STUN Binding + TURN Allocate/Refresh/CreatePermission/ChannelBind/Send/Data
 * and ChannelData fast-path.
 */
public class TurnServer {

    private static final String TAG = "[TURN] ";

    private final TurnConfig config;
    private DatagramSocket socket;
    private volatile boolean running;
    private Thread mainThread;
    private Thread cleanupThread;

    private final Map<String, TurnAllocation> allocations = new ConcurrentHashMap<String, TurnAllocation>();
    private final Map<String, String> nonces = new ConcurrentHashMap<String, String>();

    private final byte[] authKey;
    private final Random random = new Random();

    public TurnServer(TurnConfig config) {
        this.config = config;
        this.authKey = StunMessage.computeKey(config.username, config.realm, config.password);
    }

    public void start() {
        try {
            socket = new DatagramSocket(config.port);
            socket.setReuseAddress(true);
        } catch (SocketException e) {
            System.err.println(TAG + "Failed to bind port " + config.port + ": " + e.getMessage());
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

        System.out.println(TAG + "Started on port " + config.port);
    }

    public void stop() {
        running = false;
        if (socket != null && !socket.isClosed()) socket.close();
        for (TurnAllocation alloc : allocations.values()) {
            alloc.close();
        }
        allocations.clear();
        System.out.println(TAG + "Stopped");
    }

    private void mainLoop() {
        byte[] buf = new byte[65536];
        while (running) {
            try {
                DatagramPacket pkt = new DatagramPacket(buf, buf.length);
                socket.receive(pkt);
                handlePacket(pkt.getData(), pkt.getLength(),
                        new InetSocketAddress(pkt.getAddress(), pkt.getPort()));
            } catch (SocketException e) {
                if (running) System.err.println(TAG + "Socket error: " + e.getMessage());
            } catch (Exception e) {
                System.err.println(TAG + "Error: " + e.getMessage());
            }
        }
    }

    private void cleanupLoop() {
        while (running) {
            try {
                Thread.sleep(30000);
                Iterator<Map.Entry<String, TurnAllocation>> it = allocations.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<String, TurnAllocation> entry = it.next();
                    TurnAllocation alloc = entry.getValue();
                    if (alloc.isExpired()) {
                        System.out.println(TAG + "Allocation expired: " + entry.getKey());
                        alloc.close();
                        it.remove();
                    }
                }
            } catch (InterruptedException e) {
                break;
            }
        }
    }

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
                System.out.println(TAG + "Unknown method: 0x" + Integer.toHexString(method));
                break;
        }
    }

    private void handleBinding(StunMessage req, InetSocketAddress from) {
        StunMessage resp = req.createSuccessResponse();
        resp.putXorMappedAddress(StunConstants.ATTR_XOR_MAPPED_ADDRESS, from);
        sendTo(resp.encode(), from);
    }

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
            lifetime = Math.max(lifetime, 60);
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

        System.out.println(TAG + "Allocation created: " + clientKey + " -> relay " + relayAddr);
    }

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
                lifetime = Math.max(lifetime, 60);
            }
        }

        if (lifetime == 0) {
            alloc.close();
            allocations.remove(clientKey);
            System.out.println(TAG + "Allocation deleted by refresh: " + clientKey);
        } else {
            alloc.refresh(lifetime);
        }

        StunMessage resp = req.createSuccessResponse();
        resp.putInt(StunConstants.ATTR_LIFETIME, lifetime);
        sendTo(resp.encodeWithIntegrity(alloc.key), from);
    }

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

    private void startRelayListener(final TurnAllocation alloc) {
        Thread t = new Thread(new Runnable() {
            public void run() {
                byte[] buf = new byte[65536];
                while (running && !alloc.relaySocket.isClosed() && !alloc.isExpired()) {
                    try {
                        DatagramPacket pkt = new DatagramPacket(buf, buf.length);
                        alloc.relaySocket.setSoTimeout(5000);
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
                        if (running) System.err.println(TAG + "Relay error: " + e.getMessage());
                    }
                }
            }
        }, "TurnRelay-" + alloc.relayAddr.getPort());
        t.setDaemon(true);
        t.start();
    }

    private void sendTo(byte[] data, InetSocketAddress to) {
        try {
            DatagramPacket pkt = new DatagramPacket(data, data.length, to);
            socket.send(pkt);
        } catch (Exception e) {
            // Ignore send failures
        }
    }

    private String generateNonce() {
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        StringBuilder sb = new StringBuilder(32);
        for (int i = 0; i < bytes.length; i++) {
            sb.append(String.format("%02x", bytes[i] & 0xFF));
        }
        return sb.toString();
    }

    private static String addrKey(InetSocketAddress addr) {
        return addr.getAddress().getHostAddress() + ":" + addr.getPort();
    }
}
