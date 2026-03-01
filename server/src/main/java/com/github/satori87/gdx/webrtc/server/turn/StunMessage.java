package com.github.satori87.gdx.webrtc.server.turn;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Represents a single STUN/TURN message as defined in
 * <a href="https://tools.ietf.org/html/rfc5389">RFC 5389</a> and
 * <a href="https://tools.ietf.org/html/rfc5766">RFC 5766</a>.
 *
 * <p>This class provides both parsing and encoding capabilities for STUN
 * messages. A STUN message consists of a 20-byte header (message type,
 * message length, magic cookie, and 12-byte transaction ID) followed by
 * zero or more TLV-encoded attributes.</p>
 *
 * <h3>Parsing</h3>
 * <p>Use {@link #parse(byte[], int, int)} to decode a STUN message from
 * raw bytes. The parser validates the magic cookie, message length, and
 * 4-byte alignment. Parsed messages retain a reference to the raw bytes
 * for use in {@link #verifyIntegrity(byte[])}.</p>
 *
 * <h3>Encoding</h3>
 * <p>Use {@link #encode()} for messages that do not require authentication,
 * or {@link #encodeWithIntegrity(byte[])} to append a MESSAGE-INTEGRITY
 * HMAC-SHA1 attribute. Attributes are written in insertion order.</p>
 *
 * <h3>Address handling</h3>
 * <p>XOR-obfuscated addresses (XOR-MAPPED-ADDRESS, XOR-PEER-ADDRESS,
 * XOR-RELAYED-ADDRESS) are handled by {@link #putXorMappedAddress(int, InetSocketAddress)}
 * and {@link #getXorAddress(int)}. Both IPv4 and IPv6 addresses are
 * supported.</p>
 *
 * <h3>Authentication</h3>
 * <p>Long-term credential keys are computed via {@link #computeKey(String, String, String)}
 * (MD5 of username:realm:password). Message integrity is verified with
 * {@link #verifyIntegrity(byte[])} and produced with
 * {@link #encodeWithIntegrity(byte[])}.</p>
 *
 * @see StunConstants
 * @see TurnServer
 */
public class StunMessage {

    /** UTF-8 charset instance used for string attribute encoding/decoding. */
    private static final Charset UTF8 = Charset.forName("UTF-8");

    /**
     * The 16-bit STUN message type, combining method and class bits.
     * Use {@link StunConstants#getMethod(int)} and
     * {@link StunConstants#getClass(int)} to decompose.
     */
    public int messageType;

    /**
     * The 12-byte transaction ID that uniquely identifies this STUN
     * transaction. Responses must echo the transaction ID of their
     * corresponding request.
     */
    public byte[] transactionId;

    /**
     * Ordered map of attribute type codes to their raw byte values.
     * Attributes are written in insertion order during encoding.
     * Attribute types are defined as constants in {@link StunConstants}.
     */
    public final Map<Integer, byte[]> attributes = new LinkedHashMap<Integer, byte[]>();

    /**
     * Raw bytes of the original parsed message, retained for
     * MESSAGE-INTEGRITY verification. {@code null} for constructed messages.
     */
    private byte[] rawBytes;

    /**
     * Length of the valid portion of {@link #rawBytes}, from offset 0 to
     * the end of the STUN message (header + body).
     */
    private int rawLength;

    /**
     * Parses a STUN message from raw bytes.
     *
     * <p>Validates the STUN header including the two most significant bits
     * (must be zero), the magic cookie, message length alignment (must be
     * a multiple of 4), and that the declared length fits within the
     * provided data. Attributes are extracted sequentially with proper
     * padding handling.</p>
     *
     * @param data   the byte array containing the STUN message
     * @param offset the starting offset within the array
     * @param length the number of bytes available from the offset
     * @return the parsed {@code StunMessage}, or {@code null} if the data
     *         is not a valid STUN message (too short, bad magic cookie,
     *         misaligned length, etc.)
     */
    public static StunMessage parse(byte[] data, int offset, int length) {
        if (length < StunConstants.HEADER_SIZE) return null;
        if ((data[offset] & 0xC0) != 0) return null;

        ByteBuffer buf = ByteBuffer.wrap(data, offset, length);
        int type = buf.getShort() & 0xFFFF;
        int msgLen = buf.getShort() & 0xFFFF;
        int cookie = buf.getInt();

        if (cookie != StunConstants.MAGIC_COOKIE) return null;
        if (msgLen + StunConstants.HEADER_SIZE > length) return null;
        if ((msgLen & 0x03) != 0) return null;

        StunMessage msg = new StunMessage();
        msg.messageType = type;
        msg.transactionId = new byte[12];
        buf.get(msg.transactionId);
        msg.rawBytes = data;
        msg.rawLength = offset + StunConstants.HEADER_SIZE + msgLen;

        int end = offset + StunConstants.HEADER_SIZE + msgLen;
        while (buf.position() + 4 <= end) {
            int attrType = buf.getShort() & 0xFFFF;
            int attrLen = buf.getShort() & 0xFFFF;
            if (buf.position() + attrLen > end) break;

            byte[] value = new byte[attrLen];
            buf.get(value);
            msg.attributes.put(attrType, value);

            int pad = (4 - (attrLen & 0x03)) & 0x03;
            if (buf.position() + pad <= end) {
                buf.position(buf.position() + pad);
            }
        }

        return msg;
    }

    /**
     * Encodes this STUN message into a byte array without message integrity.
     *
     * <p>The header and all attributes are written in their current order.
     * Attribute values are padded to 4-byte boundaries with zero bytes as
     * required by RFC 5389.</p>
     *
     * @return the encoded STUN message as a byte array
     */
    public byte[] encode() {
        return encodeInternal(null);
    }

    /**
     * Encodes this STUN message with a MESSAGE-INTEGRITY attribute appended.
     *
     * <p>The MESSAGE-INTEGRITY attribute contains an HMAC-SHA1 hash computed
     * over the message bytes (with the message length adjusted to include
     * the integrity attribute itself). This provides authentication and
     * tamper detection per RFC 5389 Section 15.4.</p>
     *
     * @param key the HMAC-SHA1 key, typically computed via
     *            {@link #computeKey(String, String, String)}
     * @return the encoded STUN message with MESSAGE-INTEGRITY as a byte array
     */
    public byte[] encodeWithIntegrity(byte[] key) {
        return encodeInternal(key);
    }

    /**
     * Internal encoding implementation shared by {@link #encode()} and
     * {@link #encodeWithIntegrity(byte[])}.
     *
     * @param key the HMAC-SHA1 key for MESSAGE-INTEGRITY, or {@code null}
     *            to omit the integrity attribute
     * @return the encoded STUN message as a byte array
     */
    private byte[] encodeInternal(byte[] key) {
        int bodySize = 0;
        for (Map.Entry<Integer, byte[]> e : attributes.entrySet()) {
            bodySize += 4 + e.getValue().length + paddingFor(e.getValue().length);
        }

        int integritySize = (key != null) ? 24 : 0;
        ByteBuffer buf = ByteBuffer.allocate(StunConstants.HEADER_SIZE + bodySize + integritySize);

        buf.putShort((short) messageType);
        buf.putShort((short) (bodySize + integritySize));
        buf.putInt(StunConstants.MAGIC_COOKIE);
        buf.put(transactionId);

        for (Map.Entry<Integer, byte[]> e : attributes.entrySet()) {
            buf.putShort((short) (e.getKey() & 0xFFFF));
            buf.putShort((short) (e.getValue().length & 0xFFFF));
            buf.put(e.getValue());
            int pad = paddingFor(e.getValue().length);
            for (int i = 0; i < pad; i++) buf.put((byte) 0);
        }

        if (key != null) {
            byte[] partial = new byte[buf.position()];
            buf.position(0);
            buf.get(partial);
            buf.position(partial.length);

            byte[] hmac = hmacSha1(key, partial);
            buf.putShort((short) StunConstants.ATTR_MESSAGE_INTEGRITY);
            buf.putShort((short) 20);
            buf.put(hmac);
        }

        byte[] result = new byte[buf.position()];
        buf.position(0);
        buf.get(result);
        return result;
    }

    /**
     * Encodes and stores an XOR-obfuscated address attribute.
     *
     * <p>The address is XOR'd with the magic cookie (and transaction ID for
     * IPv6) as specified in RFC 5389 Section 15.2. This method supports
     * both IPv4 (family 0x01) and IPv6 (family 0x02) addresses.</p>
     *
     * <p>Common attribute types for this method include
     * {@link StunConstants#ATTR_XOR_MAPPED_ADDRESS},
     * {@link StunConstants#ATTR_XOR_PEER_ADDRESS}, and
     * {@link StunConstants#ATTR_XOR_RELAYED_ADDRESS}.</p>
     *
     * @param attrType the attribute type code
     * @param addr     the socket address to encode
     */
    public void putXorMappedAddress(int attrType, InetSocketAddress addr) {
        byte[] addrBytes = addr.getAddress().getAddress();
        boolean ipv6 = addrBytes.length == 16;
        int family = ipv6 ? 0x02 : 0x01;
        int port = addr.getPort() ^ (StunConstants.MAGIC_COOKIE >> 16);

        ByteBuffer buf = ByteBuffer.allocate(ipv6 ? 20 : 8);
        buf.put((byte) 0);
        buf.put((byte) family);
        buf.putShort((short) port);

        byte[] cookieBytes = ByteBuffer.allocate(4).putInt(StunConstants.MAGIC_COOKIE).array();
        if (!ipv6) {
            for (int i = 0; i < 4; i++) {
                buf.put((byte) (addrBytes[i] ^ cookieBytes[i]));
            }
        } else {
            byte[] xorKey = new byte[16];
            System.arraycopy(cookieBytes, 0, xorKey, 0, 4);
            System.arraycopy(transactionId, 0, xorKey, 4, 12);
            for (int i = 0; i < 16; i++) {
                buf.put((byte) (addrBytes[i] ^ xorKey[i]));
            }
        }

        attributes.put(attrType, buf.array());
    }

    /**
     * Decodes an XOR-obfuscated address attribute.
     *
     * <p>Reverses the XOR encoding applied by the sender to recover the
     * original IP address and port. Supports both IPv4 and IPv6.</p>
     *
     * @param attrType the attribute type code to look up (e.g.
     *                 {@link StunConstants#ATTR_XOR_MAPPED_ADDRESS},
     *                 {@link StunConstants#ATTR_XOR_PEER_ADDRESS})
     * @return the decoded socket address, or {@code null} if the attribute
     *         is not present, too short, or the address cannot be resolved
     */
    public InetSocketAddress getXorAddress(int attrType) {
        byte[] val = attributes.get(attrType);
        if (val == null || val.length < 8) return null;

        ByteBuffer buf = ByteBuffer.wrap(val);
        buf.get();
        int family = buf.get() & 0xFF;
        int xPort = buf.getShort() & 0xFFFF;
        int port = xPort ^ (StunConstants.MAGIC_COOKIE >> 16);

        byte[] cookieBytes = ByteBuffer.allocate(4).putInt(StunConstants.MAGIC_COOKIE).array();
        byte[] addr;
        if (family == 0x01) {
            addr = new byte[4];
            buf.get(addr);
            for (int i = 0; i < 4; i++) addr[i] ^= cookieBytes[i];
        } else {
            addr = new byte[16];
            buf.get(addr);
            byte[] xorKey = new byte[16];
            System.arraycopy(cookieBytes, 0, xorKey, 0, 4);
            System.arraycopy(transactionId, 0, xorKey, 4, 12);
            for (int i = 0; i < 16; i++) addr[i] ^= xorKey[i];
        }

        try {
            return new InetSocketAddress(InetAddress.getByAddress(addr), port);
        } catch (UnknownHostException e) {
            return null;
        }
    }

    /**
     * Stores a string attribute encoded as UTF-8 bytes.
     *
     * @param attrType the attribute type code (e.g.
     *                 {@link StunConstants#ATTR_USERNAME},
     *                 {@link StunConstants#ATTR_REALM},
     *                 {@link StunConstants#ATTR_NONCE})
     * @param value    the string value to store
     */
    public void putString(int attrType, String value) {
        attributes.put(attrType, value.getBytes(UTF8));
    }

    /**
     * Retrieves a string attribute, decoding it from UTF-8 bytes.
     *
     * @param attrType the attribute type code to look up
     * @return the string value, or {@code null} if the attribute is not present
     */
    public String getString(int attrType) {
        byte[] val = attributes.get(attrType);
        if (val == null) return null;
        return new String(val, UTF8);
    }

    /**
     * Stores a 32-bit integer attribute in network byte order (big-endian).
     *
     * @param attrType the attribute type code (e.g.
     *                 {@link StunConstants#ATTR_LIFETIME})
     * @param value    the integer value to store
     */
    public void putInt(int attrType, int value) {
        attributes.put(attrType, ByteBuffer.allocate(4).putInt(value).array());
    }

    /**
     * Retrieves a 32-bit integer attribute.
     *
     * @param attrType the attribute type code to look up
     * @return the integer value, or {@code 0} if the attribute is not present
     *         or its value is shorter than 4 bytes
     */
    public int getInt(int attrType) {
        byte[] val = attributes.get(attrType);
        if (val == null || val.length < 4) return 0;
        return ByteBuffer.wrap(val).getInt();
    }

    /**
     * Stores an ERROR-CODE attribute (RFC 5389 Section 15.6).
     *
     * <p>The error code is split into a class (hundreds digit) and number
     * (remaining two digits), followed by the UTF-8 encoded reason phrase.</p>
     *
     * @param code   the numeric error code (e.g. 401, 438)
     * @param reason a human-readable reason phrase
     */
    public void putErrorCode(int code, String reason) {
        byte[] reasonBytes = reason.getBytes(UTF8);
        ByteBuffer buf = ByteBuffer.allocate(4 + reasonBytes.length);
        buf.putShort((short) 0);
        buf.put((byte) (code / 100));
        buf.put((byte) (code % 100));
        buf.put(reasonBytes);
        attributes.put(StunConstants.ATTR_ERROR_CODE, buf.array());
    }

    /**
     * Checks whether this message contains an attribute of the given type.
     *
     * @param attrType the attribute type code to check for
     * @return {@code true} if the attribute is present, {@code false} otherwise
     */
    public boolean hasAttribute(int attrType) {
        return attributes.containsKey(attrType);
    }

    /**
     * Computes the long-term credential key used for MESSAGE-INTEGRITY.
     *
     * <p>The key is the MD5 hash of {@code username:realm:password}, as
     * defined in RFC 5389 Section 15.4 for the long-term credential
     * mechanism.</p>
     *
     * @param username the STUN/TURN username
     * @param realm    the STUN/TURN realm
     * @param password the STUN/TURN password
     * @return the 16-byte MD5 key
     * @throws RuntimeException if the MD5 algorithm is not available
     */
    public static byte[] computeKey(String username, String realm, String password) {
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            String input = username + ":" + realm + ":" + password;
            return md5.digest(input.getBytes(UTF8));
        } catch (Exception e) {
            throw new RuntimeException("MD5 not available", e);
        }
    }

    /**
     * Verifies the MESSAGE-INTEGRITY attribute of a parsed STUN message.
     *
     * <p>This method recomputes the HMAC-SHA1 over the raw message bytes
     * (up to but not including the MESSAGE-INTEGRITY attribute itself,
     * with the message length adjusted to include the integrity attribute)
     * and compares it to the stored value. This only works on messages
     * obtained via {@link #parse(byte[], int, int)}, since the raw bytes
     * are needed.</p>
     *
     * @param key the HMAC-SHA1 key, typically computed via
     *            {@link #computeKey(String, String, String)}
     * @return {@code true} if the integrity check passes, {@code false} if
     *         the attribute is missing, malformed, or the HMAC does not match
     */
    public boolean verifyIntegrity(byte[] key) {
        byte[] integrityValue = attributes.get(StunConstants.ATTR_MESSAGE_INTEGRITY);
        if (integrityValue == null || integrityValue.length != 20) return false;
        if (rawBytes == null) return false;

        int integrityPos = -1;
        ByteBuffer scan = ByteBuffer.wrap(rawBytes, 0, rawLength);
        scan.position(StunConstants.HEADER_SIZE);

        while (scan.position() + 4 <= rawLength) {
            int attrStart = scan.position();
            int aType = scan.getShort() & 0xFFFF;
            int aLen = scan.getShort() & 0xFFFF;
            if (aType == StunConstants.ATTR_MESSAGE_INTEGRITY) {
                integrityPos = attrStart;
                break;
            }
            int padded = aLen + ((4 - (aLen & 0x03)) & 0x03);
            scan.position(scan.position() + padded);
        }

        if (integrityPos < 0) return false;

        byte[] toHash = Arrays.copyOf(rawBytes, integrityPos);
        int adjustedLen = integrityPos - StunConstants.HEADER_SIZE + 24;
        toHash[2] = (byte) ((adjustedLen >> 8) & 0xFF);
        toHash[3] = (byte) (adjustedLen & 0xFF);

        byte[] computed = hmacSha1(key, toHash);
        return Arrays.equals(computed, integrityValue);
    }

    /**
     * Creates a new STUN success response message for this request.
     *
     * <p>The response copies the transaction ID from this message and sets
     * the message type to the corresponding success response type by
     * OR'ing with {@code 0x0100}.</p>
     *
     * @return a new {@code StunMessage} configured as a success response
     */
    public StunMessage createSuccessResponse() {
        StunMessage resp = new StunMessage();
        resp.messageType = messageType | 0x0100;
        resp.transactionId = transactionId.clone();
        return resp;
    }

    /**
     * Creates a new STUN error response message for this request.
     *
     * <p>The response copies the transaction ID from this message, sets the
     * message type to the corresponding error response type (class=3), and
     * includes an ERROR-CODE attribute with the given code and reason.</p>
     *
     * @param errorCode the numeric error code (e.g.
     *                  {@link StunConstants#ERR_UNAUTHORIZED})
     * @param reason    a human-readable reason phrase
     * @return a new {@code StunMessage} configured as an error response
     */
    public StunMessage createErrorResponse(int errorCode, String reason) {
        StunMessage resp = new StunMessage();
        int method = StunConstants.getMethod(messageType);
        resp.messageType = StunConstants.buildMessageType(method, 3);
        resp.transactionId = transactionId.clone();
        resp.putErrorCode(errorCode, reason);
        return resp;
    }

    /**
     * Computes an HMAC-SHA1 message authentication code.
     *
     * @param key  the secret key
     * @param data the data to authenticate
     * @return the 20-byte HMAC-SHA1 value
     * @throws RuntimeException if the HmacSHA1 algorithm is not available
     */
    static byte[] hmacSha1(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new RuntimeException("HmacSHA1 not available", e);
        }
    }

    /**
     * Calculates the number of padding bytes needed to align a value to a
     * 4-byte boundary, as required by the STUN attribute encoding.
     *
     * @param length the attribute value length in bytes
     * @return the number of padding bytes (0-3)
     */
    private static int paddingFor(int length) {
        return (4 - (length & 0x03)) & 0x03;
    }
}
