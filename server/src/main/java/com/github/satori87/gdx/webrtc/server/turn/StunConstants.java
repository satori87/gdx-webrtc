package com.github.satori87.gdx.webrtc.server.turn;

/**
 * STUN/TURN protocol constants (RFC 5389, RFC 5766).
 */
public final class StunConstants {

    private StunConstants() {}

    public static final int MAGIC_COOKIE = 0x2112A442;
    public static final int HEADER_SIZE = 20;

    // Message classes
    public static final int CLASS_REQUEST    = 0x0000;
    public static final int CLASS_INDICATION = 0x0010;
    public static final int CLASS_SUCCESS    = 0x0100;
    public static final int CLASS_ERROR      = 0x0110;

    // Methods
    public static final int METHOD_BINDING           = 0x0001;
    public static final int METHOD_ALLOCATE          = 0x0003;
    public static final int METHOD_REFRESH           = 0x0004;
    public static final int METHOD_SEND              = 0x0006;
    public static final int METHOD_DATA              = 0x0007;
    public static final int METHOD_CREATE_PERMISSION = 0x0008;
    public static final int METHOD_CHANNEL_BIND      = 0x0009;

    // Full message types
    public static final int BINDING_REQUEST          = 0x0001;
    public static final int BINDING_SUCCESS          = 0x0101;
    public static final int BINDING_ERROR            = 0x0111;

    public static final int ALLOCATE_REQUEST         = 0x0003;
    public static final int ALLOCATE_SUCCESS         = 0x0103;
    public static final int ALLOCATE_ERROR           = 0x0113;

    public static final int REFRESH_REQUEST          = 0x0004;
    public static final int REFRESH_SUCCESS          = 0x0104;

    public static final int SEND_INDICATION          = 0x0016;
    public static final int DATA_INDICATION          = 0x0017;

    public static final int CREATE_PERMISSION_REQUEST = 0x0008;
    public static final int CREATE_PERMISSION_SUCCESS = 0x0108;

    public static final int CHANNEL_BIND_REQUEST     = 0x0009;
    public static final int CHANNEL_BIND_SUCCESS     = 0x0109;

    // Attribute types
    public static final int ATTR_MAPPED_ADDRESS       = 0x0001;
    public static final int ATTR_USERNAME             = 0x0006;
    public static final int ATTR_MESSAGE_INTEGRITY    = 0x0008;
    public static final int ATTR_ERROR_CODE           = 0x0009;
    public static final int ATTR_UNKNOWN_ATTRIBUTES   = 0x000A;
    public static final int ATTR_REALM                = 0x0014;
    public static final int ATTR_NONCE                = 0x0015;
    public static final int ATTR_XOR_MAPPED_ADDRESS   = 0x0020;
    public static final int ATTR_CHANNEL_NUMBER       = 0x000C;
    public static final int ATTR_LIFETIME             = 0x000D;
    public static final int ATTR_XOR_PEER_ADDRESS     = 0x0012;
    public static final int ATTR_DATA                 = 0x0013;
    public static final int ATTR_XOR_RELAYED_ADDRESS  = 0x0016;
    public static final int ATTR_REQUESTED_TRANSPORT  = 0x0019;
    public static final int ATTR_SOFTWARE             = 0x8022;
    public static final int ATTR_FINGERPRINT          = 0x8028;

    // Error codes
    public static final int ERR_BAD_REQUEST           = 400;
    public static final int ERR_UNAUTHORIZED          = 401;
    public static final int ERR_FORBIDDEN             = 403;
    public static final int ERR_UNKNOWN_ATTRIBUTE     = 420;
    public static final int ERR_ALLOCATION_MISMATCH   = 437;
    public static final int ERR_STALE_NONCE           = 438;
    public static final int ERR_INSUFFICIENT_CAPACITY = 508;

    // Transport protocol numbers
    public static final int TRANSPORT_UDP = 17;

    // Channel number range
    public static final int CHANNEL_MIN = 0x4000;
    public static final int CHANNEL_MAX = 0x7FFF;

    // Default allocation lifetime (seconds)
    public static final int DEFAULT_LIFETIME = 600;
    public static final int MAX_LIFETIME     = 3600;

    public static int getMethod(int messageType) {
        return (messageType & 0x000F)
             | ((messageType & 0x00E0) >> 1)
             | ((messageType & 0x3E00) >> 2);
    }

    public static int getClass(int messageType) {
        return ((messageType & 0x0010) >> 4)
             | ((messageType & 0x0100) >> 7);
    }

    public static int buildMessageType(int method, int clazz) {
        int type = (method & 0x000F)
                 | ((method & 0x0070) << 1)
                 | ((method & 0x0F80) << 2)
                 | ((clazz & 0x01) << 4)
                 | ((clazz & 0x02) << 7);
        return type;
    }
}
