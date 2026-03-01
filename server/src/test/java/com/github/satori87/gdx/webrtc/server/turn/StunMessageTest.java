package com.github.satori87.gdx.webrtc.server.turn;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class StunMessageTest {

    private byte[] buildBindingRequest(byte[] txnId) {
        ByteBuffer buf = ByteBuffer.allocate(20);
        buf.putShort((short) StunConstants.BINDING_REQUEST);
        buf.putShort((short) 0); // body length
        buf.putInt(StunConstants.MAGIC_COOKIE);
        buf.put(txnId);
        return buf.array();
    }

    private byte[] newTxnId() {
        byte[] txnId = new byte[12];
        for (int i = 0; i < 12; i++) txnId[i] = (byte) (i + 1);
        return txnId;
    }

    // --- Parse tests ---

    @Test
    void parseValidBindingRequest() {
        byte[] data = buildBindingRequest(newTxnId());
        StunMessage msg = StunMessage.parse(data, 0, data.length);
        assertNotNull(msg);
        assertEquals(StunConstants.BINDING_REQUEST, msg.messageType);
        assertArrayEquals(newTxnId(), msg.transactionId);
        assertTrue(msg.attributes.isEmpty());
    }

    @Test
    void parseReturnsNullForTooShortData() {
        assertNull(StunMessage.parse(new byte[10], 0, 10));
    }

    @Test
    void parseReturnsNullForBadMagicCookie() {
        byte[] data = buildBindingRequest(newTxnId());
        data[4] = 0; // corrupt magic cookie
        assertNull(StunMessage.parse(data, 0, data.length));
    }

    @Test
    void parseReturnsNullForMisalignedLength() {
        ByteBuffer buf = ByteBuffer.allocate(24);
        buf.putShort((short) StunConstants.BINDING_REQUEST);
        buf.putShort((short) 3); // misaligned body length
        buf.putInt(StunConstants.MAGIC_COOKIE);
        buf.put(newTxnId());
        assertNull(StunMessage.parse(buf.array(), 0, 24));
    }

    @Test
    void parseReturnsNullForLengthExceedingData() {
        ByteBuffer buf = ByteBuffer.allocate(24);
        buf.putShort((short) StunConstants.BINDING_REQUEST);
        buf.putShort((short) 100); // claims 100 bytes of body but only 4 available
        buf.putInt(StunConstants.MAGIC_COOKIE);
        buf.put(newTxnId());
        assertNull(StunMessage.parse(buf.array(), 0, 24));
    }

    @Test
    void parseReturnsNullIfFirstTwoBitsNotZero() {
        byte[] data = buildBindingRequest(newTxnId());
        data[0] = (byte) 0xC0; // set top 2 bits
        assertNull(StunMessage.parse(data, 0, data.length));
    }

    @Test
    void parseWithAttributes() {
        // Build a message with a LIFETIME attribute (4 bytes)
        ByteBuffer buf = ByteBuffer.allocate(28);
        buf.putShort((short) StunConstants.BINDING_REQUEST);
        buf.putShort((short) 8); // body length = 4 (attr header) + 4 (attr value)
        buf.putInt(StunConstants.MAGIC_COOKIE);
        buf.put(newTxnId());
        // LIFETIME attribute: type=0x000D, length=4, value=600
        buf.putShort((short) StunConstants.ATTR_LIFETIME);
        buf.putShort((short) 4);
        buf.putInt(600);

        StunMessage msg = StunMessage.parse(buf.array(), 0, 28);
        assertNotNull(msg);
        assertTrue(msg.hasAttribute(StunConstants.ATTR_LIFETIME));
        assertEquals(600, msg.getInt(StunConstants.ATTR_LIFETIME));
    }

    @Test
    void parseWithPaddedStringAttribute() {
        // STRING attribute with 5 bytes (needs 3 bytes padding to align to 4)
        String value = "hello";
        byte[] valueBytes = value.getBytes();
        int padded = 5 + 3; // pad to 8 bytes
        ByteBuffer buf = ByteBuffer.allocate(20 + 4 + padded);
        buf.putShort((short) StunConstants.BINDING_REQUEST);
        buf.putShort((short) (4 + padded));
        buf.putInt(StunConstants.MAGIC_COOKIE);
        buf.put(newTxnId());
        buf.putShort((short) StunConstants.ATTR_USERNAME);
        buf.putShort((short) 5);
        buf.put(valueBytes);
        buf.put(new byte[3]); // padding

        StunMessage msg = StunMessage.parse(buf.array(), 0, buf.capacity());
        assertNotNull(msg);
        assertEquals("hello", msg.getString(StunConstants.ATTR_USERNAME));
    }

    @Test
    void parseWithOffset() {
        byte[] data = buildBindingRequest(newTxnId());
        byte[] padded = new byte[data.length + 10];
        System.arraycopy(data, 0, padded, 10, data.length);
        StunMessage msg = StunMessage.parse(padded, 10, data.length);
        assertNotNull(msg);
        assertEquals(StunConstants.BINDING_REQUEST, msg.messageType);
    }

    // --- Encode tests ---

    @Test
    void encodeRoundTrip() {
        StunMessage msg = new StunMessage();
        msg.messageType = StunConstants.BINDING_SUCCESS;
        msg.transactionId = newTxnId();
        msg.putInt(StunConstants.ATTR_LIFETIME, 600);
        msg.putString(StunConstants.ATTR_REALM, "webrtc");

        byte[] encoded = msg.encode();
        StunMessage parsed = StunMessage.parse(encoded, 0, encoded.length);
        assertNotNull(parsed);
        assertEquals(StunConstants.BINDING_SUCCESS, parsed.messageType);
        assertArrayEquals(newTxnId(), parsed.transactionId);
        assertEquals(600, parsed.getInt(StunConstants.ATTR_LIFETIME));
        assertEquals("webrtc", parsed.getString(StunConstants.ATTR_REALM));
    }

    @Test
    void encodeEmptyMessage() {
        StunMessage msg = new StunMessage();
        msg.messageType = StunConstants.BINDING_REQUEST;
        msg.transactionId = newTxnId();
        byte[] encoded = msg.encode();
        assertEquals(20, encoded.length); // just the header
    }

    // --- XOR-MAPPED-ADDRESS tests ---

    @Test
    void xorMappedAddressRoundTripIPv4() throws Exception {
        StunMessage msg = new StunMessage();
        msg.messageType = StunConstants.BINDING_SUCCESS;
        msg.transactionId = newTxnId();

        InetSocketAddress addr = new InetSocketAddress(InetAddress.getByName("192.168.1.100"), 12345);
        msg.putXorMappedAddress(StunConstants.ATTR_XOR_MAPPED_ADDRESS, addr);

        InetSocketAddress decoded = msg.getXorAddress(StunConstants.ATTR_XOR_MAPPED_ADDRESS);
        assertNotNull(decoded);
        assertEquals(addr.getAddress(), decoded.getAddress());
        assertEquals(addr.getPort(), decoded.getPort());
    }

    @Test
    void xorMappedAddressRoundTripIPv6() throws Exception {
        StunMessage msg = new StunMessage();
        msg.messageType = StunConstants.BINDING_SUCCESS;
        msg.transactionId = newTxnId();

        InetSocketAddress addr = new InetSocketAddress(InetAddress.getByName("2001:db8::1"), 54321);
        msg.putXorMappedAddress(StunConstants.ATTR_XOR_MAPPED_ADDRESS, addr);

        InetSocketAddress decoded = msg.getXorAddress(StunConstants.ATTR_XOR_MAPPED_ADDRESS);
        assertNotNull(decoded);
        assertEquals(addr.getAddress(), decoded.getAddress());
        assertEquals(addr.getPort(), decoded.getPort());
    }

    @Test
    void getXorAddressReturnsNullForMissing() {
        StunMessage msg = new StunMessage();
        msg.messageType = StunConstants.BINDING_SUCCESS;
        msg.transactionId = newTxnId();
        assertNull(msg.getXorAddress(StunConstants.ATTR_XOR_MAPPED_ADDRESS));
    }

    @Test
    void getXorAddressReturnsNullForTooShort() {
        StunMessage msg = new StunMessage();
        msg.messageType = StunConstants.BINDING_SUCCESS;
        msg.transactionId = newTxnId();
        msg.attributes.put(StunConstants.ATTR_XOR_MAPPED_ADDRESS, new byte[4]);
        assertNull(msg.getXorAddress(StunConstants.ATTR_XOR_MAPPED_ADDRESS));
    }

    // --- String attribute tests ---

    @Test
    void putAndGetString() {
        StunMessage msg = new StunMessage();
        msg.messageType = StunConstants.BINDING_REQUEST;
        msg.transactionId = newTxnId();
        msg.putString(StunConstants.ATTR_USERNAME, "testuser");
        assertEquals("testuser", msg.getString(StunConstants.ATTR_USERNAME));
    }

    @Test
    void getStringReturnsNullForMissing() {
        StunMessage msg = new StunMessage();
        msg.messageType = StunConstants.BINDING_REQUEST;
        msg.transactionId = newTxnId();
        assertNull(msg.getString(StunConstants.ATTR_USERNAME));
    }

    // --- Int attribute tests ---

    @Test
    void putAndGetInt() {
        StunMessage msg = new StunMessage();
        msg.messageType = StunConstants.BINDING_REQUEST;
        msg.transactionId = newTxnId();
        msg.putInt(StunConstants.ATTR_LIFETIME, 3600);
        assertEquals(3600, msg.getInt(StunConstants.ATTR_LIFETIME));
    }

    @Test
    void getIntReturnsZeroForMissing() {
        StunMessage msg = new StunMessage();
        msg.messageType = StunConstants.BINDING_REQUEST;
        msg.transactionId = newTxnId();
        assertEquals(0, msg.getInt(StunConstants.ATTR_LIFETIME));
    }

    @Test
    void getIntReturnsZeroForTooShort() {
        StunMessage msg = new StunMessage();
        msg.messageType = StunConstants.BINDING_REQUEST;
        msg.transactionId = newTxnId();
        msg.attributes.put(StunConstants.ATTR_LIFETIME, new byte[2]);
        assertEquals(0, msg.getInt(StunConstants.ATTR_LIFETIME));
    }

    // --- Error code tests ---

    @Test
    void putErrorCode() {
        StunMessage msg = new StunMessage();
        msg.messageType = StunConstants.BINDING_ERROR;
        msg.transactionId = newTxnId();
        msg.putErrorCode(401, "Unauthorized");
        byte[] errorAttr = msg.attributes.get(StunConstants.ATTR_ERROR_CODE);
        assertNotNull(errorAttr);
        // bytes 2-3 should be class=4 and number=01
        assertEquals(4, errorAttr[2] & 0xFF);
        assertEquals(1, errorAttr[3] & 0xFF);
    }

    @Test
    void hasAttribute() {
        StunMessage msg = new StunMessage();
        msg.messageType = StunConstants.BINDING_REQUEST;
        msg.transactionId = newTxnId();
        assertFalse(msg.hasAttribute(StunConstants.ATTR_LIFETIME));
        msg.putInt(StunConstants.ATTR_LIFETIME, 600);
        assertTrue(msg.hasAttribute(StunConstants.ATTR_LIFETIME));
    }

    // --- Success/Error response creation ---

    @Test
    void createSuccessResponse() {
        StunMessage req = new StunMessage();
        req.messageType = StunConstants.BINDING_REQUEST;
        req.transactionId = newTxnId();

        StunMessage resp = req.createSuccessResponse();
        assertEquals(StunConstants.BINDING_SUCCESS, resp.messageType);
        assertArrayEquals(req.transactionId, resp.transactionId);
        // Ensure transactionId is a clone, not the same reference
        assertNotSame(req.transactionId, resp.transactionId);
    }

    @Test
    void createErrorResponse() {
        StunMessage req = new StunMessage();
        req.messageType = StunConstants.ALLOCATE_REQUEST;
        req.transactionId = newTxnId();

        StunMessage resp = req.createErrorResponse(437, "Allocation mismatch");
        int method = StunConstants.getMethod(resp.messageType);
        int clazz = StunConstants.getClass(resp.messageType);
        assertEquals(StunConstants.METHOD_ALLOCATE, method);
        assertEquals(3, clazz); // error class
        assertTrue(resp.hasAttribute(StunConstants.ATTR_ERROR_CODE));
    }

    // --- computeKey tests ---

    @Test
    void computeKeyReturnsSixteenBytes() {
        byte[] key = StunMessage.computeKey("user", "realm", "pass");
        assertNotNull(key);
        assertEquals(16, key.length); // MD5 = 16 bytes
    }

    @Test
    void computeKeyDeterministic() {
        byte[] key1 = StunMessage.computeKey("user", "realm", "pass");
        byte[] key2 = StunMessage.computeKey("user", "realm", "pass");
        assertArrayEquals(key1, key2);
    }

    @Test
    void computeKeyDiffersForDifferentInputs() {
        byte[] key1 = StunMessage.computeKey("user1", "realm", "pass");
        byte[] key2 = StunMessage.computeKey("user2", "realm", "pass");
        assertFalse(Arrays.equals(key1, key2));
    }

    // --- MESSAGE-INTEGRITY tests ---

    @Test
    void encodeWithIntegrityAndVerify() {
        byte[] key = StunMessage.computeKey("user", "realm", "pass");

        StunMessage msg = new StunMessage();
        msg.messageType = StunConstants.BINDING_SUCCESS;
        msg.transactionId = newTxnId();
        msg.putInt(StunConstants.ATTR_LIFETIME, 600);

        byte[] encoded = msg.encodeWithIntegrity(key);
        StunMessage parsed = StunMessage.parse(encoded, 0, encoded.length);
        assertNotNull(parsed);
        assertTrue(parsed.verifyIntegrity(key));
    }

    @Test
    void verifyIntegrityFailsWithWrongKey() {
        byte[] key = StunMessage.computeKey("user", "realm", "pass");
        byte[] wrongKey = StunMessage.computeKey("user", "realm", "wrong");

        StunMessage msg = new StunMessage();
        msg.messageType = StunConstants.BINDING_SUCCESS;
        msg.transactionId = newTxnId();
        msg.putInt(StunConstants.ATTR_LIFETIME, 600);

        byte[] encoded = msg.encodeWithIntegrity(key);
        StunMessage parsed = StunMessage.parse(encoded, 0, encoded.length);
        assertNotNull(parsed);
        assertFalse(parsed.verifyIntegrity(wrongKey));
    }

    @Test
    void verifyIntegrityReturnsFalseWithoutIntegrityAttr() {
        byte[] key = StunMessage.computeKey("user", "realm", "pass");

        byte[] data = buildBindingRequest(newTxnId());
        StunMessage parsed = StunMessage.parse(data, 0, data.length);
        assertNotNull(parsed);
        assertFalse(parsed.verifyIntegrity(key));
    }

    @Test
    void verifyIntegrityReturnsFalseForConstructedMessage() {
        byte[] key = StunMessage.computeKey("user", "realm", "pass");

        StunMessage msg = new StunMessage();
        msg.messageType = StunConstants.BINDING_SUCCESS;
        msg.transactionId = newTxnId();
        // No rawBytes since not parsed
        assertFalse(msg.verifyIntegrity(key));
    }

    @Test
    void encodeWithIntegrityAddsIntegrityAttribute() {
        byte[] key = StunMessage.computeKey("user", "realm", "pass");

        StunMessage msg = new StunMessage();
        msg.messageType = StunConstants.BINDING_SUCCESS;
        msg.transactionId = newTxnId();

        byte[] encoded = msg.encodeWithIntegrity(key);
        StunMessage parsed = StunMessage.parse(encoded, 0, encoded.length);
        assertNotNull(parsed);
        assertTrue(parsed.hasAttribute(StunConstants.ATTR_MESSAGE_INTEGRITY));
        assertEquals(20, parsed.attributes.get(StunConstants.ATTR_MESSAGE_INTEGRITY).length);
    }

    // --- XOR address encode/decode roundtrip through full encode ---

    @Test
    void xorAddressRoundTripThroughEncoding() throws Exception {
        StunMessage msg = new StunMessage();
        msg.messageType = StunConstants.ALLOCATE_SUCCESS;
        msg.transactionId = newTxnId();

        InetSocketAddress relayAddr = new InetSocketAddress(InetAddress.getByName("10.0.0.1"), 49152);
        InetSocketAddress clientAddr = new InetSocketAddress(InetAddress.getByName("192.168.1.1"), 12345);

        msg.putXorMappedAddress(StunConstants.ATTR_XOR_RELAYED_ADDRESS, relayAddr);
        msg.putXorMappedAddress(StunConstants.ATTR_XOR_MAPPED_ADDRESS, clientAddr);

        byte[] encoded = msg.encode();
        StunMessage parsed = StunMessage.parse(encoded, 0, encoded.length);
        assertNotNull(parsed);

        InetSocketAddress decodedRelay = parsed.getXorAddress(StunConstants.ATTR_XOR_RELAYED_ADDRESS);
        InetSocketAddress decodedClient = parsed.getXorAddress(StunConstants.ATTR_XOR_MAPPED_ADDRESS);

        assertNotNull(decodedRelay);
        assertNotNull(decodedClient);
        assertEquals(relayAddr.getAddress(), decodedRelay.getAddress());
        assertEquals(relayAddr.getPort(), decodedRelay.getPort());
        assertEquals(clientAddr.getAddress(), decodedClient.getAddress());
        assertEquals(clientAddr.getPort(), decodedClient.getPort());
    }

    // --- hmacSha1 ---

    @Test
    void hmacSha1ReturnsTwentyBytes() {
        byte[] key = new byte[]{1, 2, 3, 4};
        byte[] data = new byte[]{5, 6, 7, 8};
        byte[] result = StunMessage.hmacSha1(key, data);
        assertEquals(20, result.length);
    }

    @Test
    void hmacSha1IsDeterministic() {
        byte[] key = new byte[]{1, 2, 3, 4};
        byte[] data = new byte[]{5, 6, 7, 8};
        assertArrayEquals(StunMessage.hmacSha1(key, data), StunMessage.hmacSha1(key, data));
    }
}
