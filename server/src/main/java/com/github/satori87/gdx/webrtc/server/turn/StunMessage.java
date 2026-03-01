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
 * Minimal STUN message parser/encoder (RFC 5389 / RFC 5766).
 */
public class StunMessage {

    private static final Charset UTF8 = Charset.forName("UTF-8");

    public int messageType;
    public byte[] transactionId;
    public final Map<Integer, byte[]> attributes = new LinkedHashMap<Integer, byte[]>();

    private byte[] rawBytes;
    private int rawLength;

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

    public byte[] encode() {
        return encodeInternal(null);
    }

    public byte[] encodeWithIntegrity(byte[] key) {
        return encodeInternal(key);
    }

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

    public void putString(int attrType, String value) {
        attributes.put(attrType, value.getBytes(UTF8));
    }

    public String getString(int attrType) {
        byte[] val = attributes.get(attrType);
        if (val == null) return null;
        return new String(val, UTF8);
    }

    public void putInt(int attrType, int value) {
        attributes.put(attrType, ByteBuffer.allocate(4).putInt(value).array());
    }

    public int getInt(int attrType) {
        byte[] val = attributes.get(attrType);
        if (val == null || val.length < 4) return 0;
        return ByteBuffer.wrap(val).getInt();
    }

    public void putErrorCode(int code, String reason) {
        byte[] reasonBytes = reason.getBytes(UTF8);
        ByteBuffer buf = ByteBuffer.allocate(4 + reasonBytes.length);
        buf.putShort((short) 0);
        buf.put((byte) (code / 100));
        buf.put((byte) (code % 100));
        buf.put(reasonBytes);
        attributes.put(StunConstants.ATTR_ERROR_CODE, buf.array());
    }

    public boolean hasAttribute(int attrType) {
        return attributes.containsKey(attrType);
    }

    public static byte[] computeKey(String username, String realm, String password) {
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            String input = username + ":" + realm + ":" + password;
            return md5.digest(input.getBytes(UTF8));
        } catch (Exception e) {
            throw new RuntimeException("MD5 not available", e);
        }
    }

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

    public StunMessage createSuccessResponse() {
        StunMessage resp = new StunMessage();
        resp.messageType = messageType | 0x0100;
        resp.transactionId = transactionId.clone();
        return resp;
    }

    public StunMessage createErrorResponse(int errorCode, String reason) {
        StunMessage resp = new StunMessage();
        int method = StunConstants.getMethod(messageType);
        resp.messageType = StunConstants.buildMessageType(method, 3);
        resp.transactionId = transactionId.clone();
        resp.putErrorCode(errorCode, reason);
        return resp;
    }

    static byte[] hmacSha1(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new RuntimeException("HmacSHA1 not available", e);
        }
    }

    private static int paddingFor(int length) {
        return (4 - (length & 0x03)) & 0x03;
    }
}
