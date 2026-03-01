package com.github.satori87.gdx.webrtc.server.turn;

/**
 * Constants for the STUN and TURN protocols as defined in
 * <a href="https://tools.ietf.org/html/rfc5389">RFC 5389</a> (STUN) and
 * <a href="https://tools.ietf.org/html/rfc5766">RFC 5766</a> (TURN).
 *
 * <p>This class is a pure constant holder and provides utility methods for
 * encoding and decoding STUN message types. STUN message types are composed
 * from a method and a class using a bit-interleaving scheme defined in
 * RFC 5389 Section 6. The helper methods {@link #getMethod(int)},
 * {@link #getClass(int)}, and {@link #buildMessageType(int, int)} handle
 * this encoding.</p>
 *
 * <p>The constants cover:</p>
 * <ul>
 *   <li>STUN header fields (magic cookie, header size)</li>
 *   <li>Message classes (request, indication, success response, error response)</li>
 *   <li>Methods (Binding, Allocate, Refresh, Send, Data, CreatePermission, ChannelBind)</li>
 *   <li>Pre-built full message types combining method + class</li>
 *   <li>STUN/TURN attribute type codes</li>
 *   <li>Error response codes</li>
 *   <li>Channel number ranges and allocation lifetimes</li>
 * </ul>
 *
 * @see StunMessage
 * @see TurnServer
 */
public final class StunConstants {

    /** Private constructor to prevent instantiation of this utility class. */
    private StunConstants() {}

    // -------------------------------------------------------------------------
    // Header fields (RFC 5389, Section 6)
    // -------------------------------------------------------------------------

    /**
     * The fixed 32-bit magic cookie value ({@code 0x2112A442}) present in
     * every STUN message header. Used to distinguish STUN packets from other
     * protocols and as part of XOR-based address obfuscation.
     * <p>Defined in RFC 5389 Section 6.</p>
     */
    public static final int MAGIC_COOKIE = 0x2112A442;

    /**
     * Size of the STUN message header in bytes (20 bytes): 2 bytes message type,
     * 2 bytes message length, 4 bytes magic cookie, 12 bytes transaction ID.
     * <p>Defined in RFC 5389 Section 6.</p>
     */
    public static final int HEADER_SIZE = 20;

    // -------------------------------------------------------------------------
    // Message classes (RFC 5389, Section 6)
    // -------------------------------------------------------------------------

    /**
     * STUN message class: Request ({@code 0b00}).
     * Sent by a client to initiate a transaction.
     */
    public static final int CLASS_REQUEST    = 0x0000;

    /**
     * STUN message class: Indication ({@code 0b01}).
     * A fire-and-forget message that does not elicit a response.
     */
    public static final int CLASS_INDICATION = 0x0010;

    /**
     * STUN message class: Success Response ({@code 0b10}).
     * Sent by the server when a request is processed successfully.
     */
    public static final int CLASS_SUCCESS    = 0x0100;

    /**
     * STUN message class: Error Response ({@code 0b11}).
     * Sent by the server when a request cannot be fulfilled.
     */
    public static final int CLASS_ERROR      = 0x0110;

    // -------------------------------------------------------------------------
    // Methods (RFC 5389 Section 18.1, RFC 5766 Section 13)
    // -------------------------------------------------------------------------

    /**
     * STUN Binding method ({@code 0x001}). Used for NAT keepalives and
     * server-reflexive address discovery.
     * <p>Defined in RFC 5389 Section 3.</p>
     */
    public static final int METHOD_BINDING           = 0x0001;

    /**
     * TURN Allocate method ({@code 0x003}). Requests the server to allocate
     * a relay transport address for the client.
     * <p>Defined in RFC 5766 Section 6.</p>
     */
    public static final int METHOD_ALLOCATE          = 0x0003;

    /**
     * TURN Refresh method ({@code 0x004}). Refreshes an existing allocation's
     * lifetime, or deletes it when lifetime is set to zero.
     * <p>Defined in RFC 5766 Section 7.</p>
     */
    public static final int METHOD_REFRESH           = 0x0004;

    /**
     * TURN Send method ({@code 0x006}). Used in Send indications to transmit
     * data from the client to a peer through the relay.
     * <p>Defined in RFC 5766 Section 10.</p>
     */
    public static final int METHOD_SEND              = 0x0006;

    /**
     * TURN Data method ({@code 0x007}). Used in Data indications to deliver
     * data from a peer to the client through the relay.
     * <p>Defined in RFC 5766 Section 10.</p>
     */
    public static final int METHOD_DATA              = 0x0007;

    /**
     * TURN CreatePermission method ({@code 0x008}). Installs a permission
     * for a specific peer IP address on the allocation.
     * <p>Defined in RFC 5766 Section 9.</p>
     */
    public static final int METHOD_CREATE_PERMISSION = 0x0008;

    /**
     * TURN ChannelBind method ({@code 0x009}). Binds a channel number to a
     * peer address, enabling the more efficient ChannelData framing.
     * <p>Defined in RFC 5766 Section 11.</p>
     */
    public static final int METHOD_CHANNEL_BIND      = 0x0009;

    // -------------------------------------------------------------------------
    // Pre-built full message types (method + class combined)
    // -------------------------------------------------------------------------

    /** STUN Binding Request (method=Binding, class=Request). */
    public static final int BINDING_REQUEST          = 0x0001;
    /** STUN Binding Success Response (method=Binding, class=Success). */
    public static final int BINDING_SUCCESS          = 0x0101;
    /** STUN Binding Error Response (method=Binding, class=Error). */
    public static final int BINDING_ERROR            = 0x0111;

    /** TURN Allocate Request (method=Allocate, class=Request). */
    public static final int ALLOCATE_REQUEST         = 0x0003;
    /** TURN Allocate Success Response (method=Allocate, class=Success). */
    public static final int ALLOCATE_SUCCESS         = 0x0103;
    /** TURN Allocate Error Response (method=Allocate, class=Error). */
    public static final int ALLOCATE_ERROR           = 0x0113;

    /** TURN Refresh Request (method=Refresh, class=Request). */
    public static final int REFRESH_REQUEST          = 0x0004;
    /** TURN Refresh Success Response (method=Refresh, class=Success). */
    public static final int REFRESH_SUCCESS          = 0x0104;

    /**
     * TURN Send Indication (method=Send, class=Indication).
     * Used by clients to send data to a peer via the relay.
     */
    public static final int SEND_INDICATION          = 0x0016;
    /**
     * TURN Data Indication (method=Data, class=Indication).
     * Used by the server to forward peer data to the client.
     */
    public static final int DATA_INDICATION          = 0x0017;

    /** TURN CreatePermission Request (method=CreatePermission, class=Request). */
    public static final int CREATE_PERMISSION_REQUEST = 0x0008;
    /** TURN CreatePermission Success Response (method=CreatePermission, class=Success). */
    public static final int CREATE_PERMISSION_SUCCESS = 0x0108;

    /** TURN ChannelBind Request (method=ChannelBind, class=Request). */
    public static final int CHANNEL_BIND_REQUEST     = 0x0009;
    /** TURN ChannelBind Success Response (method=ChannelBind, class=Success). */
    public static final int CHANNEL_BIND_SUCCESS     = 0x0109;

    // -------------------------------------------------------------------------
    // Attribute types (RFC 5389 Section 18.2, RFC 5766 Section 14)
    // -------------------------------------------------------------------------

    /**
     * MAPPED-ADDRESS attribute ({@code 0x0001}). Contains the client's
     * reflexive transport address as seen by the server (not XOR-obfuscated).
     * <p>Defined in RFC 5389 Section 15.1.</p>
     */
    public static final int ATTR_MAPPED_ADDRESS       = 0x0001;

    /**
     * USERNAME attribute ({@code 0x0006}). Contains the username for
     * message integrity verification using long-term credentials.
     * <p>Defined in RFC 5389 Section 15.3.</p>
     */
    public static final int ATTR_USERNAME             = 0x0006;

    /**
     * MESSAGE-INTEGRITY attribute ({@code 0x0008}). Contains an HMAC-SHA1
     * hash over the STUN message for authentication and tamper detection.
     * <p>Defined in RFC 5389 Section 15.4.</p>
     */
    public static final int ATTR_MESSAGE_INTEGRITY    = 0x0008;

    /**
     * ERROR-CODE attribute ({@code 0x0009}). Contains a numeric error code
     * (class and number) plus a UTF-8 reason phrase.
     * <p>Defined in RFC 5389 Section 15.6.</p>
     */
    public static final int ATTR_ERROR_CODE           = 0x0009;

    /**
     * UNKNOWN-ATTRIBUTES attribute ({@code 0x000A}). Lists the attribute
     * types that were not understood by the server, included in 420 error
     * responses.
     * <p>Defined in RFC 5389 Section 15.9.</p>
     */
    public static final int ATTR_UNKNOWN_ATTRIBUTES   = 0x000A;

    /**
     * REALM attribute ({@code 0x0014}). Contains the realm string for
     * long-term credential authentication.
     * <p>Defined in RFC 5389 Section 15.7.</p>
     */
    public static final int ATTR_REALM                = 0x0014;

    /**
     * NONCE attribute ({@code 0x0015}). Contains a server-generated nonce
     * for replay protection in long-term credential authentication.
     * <p>Defined in RFC 5389 Section 15.8.</p>
     */
    public static final int ATTR_NONCE                = 0x0015;

    /**
     * XOR-MAPPED-ADDRESS attribute ({@code 0x0020}). Contains the client's
     * reflexive transport address XOR'd with the magic cookie (and
     * transaction ID for IPv6) to avoid NAT ALG interference.
     * <p>Defined in RFC 5389 Section 15.2.</p>
     */
    public static final int ATTR_XOR_MAPPED_ADDRESS   = 0x0020;

    /**
     * CHANNEL-NUMBER attribute ({@code 0x000C}). Contains the channel number
     * to bind to a peer address for ChannelData fast-path framing.
     * <p>Defined in RFC 5766 Section 14.1.</p>
     */
    public static final int ATTR_CHANNEL_NUMBER       = 0x000C;

    /**
     * LIFETIME attribute ({@code 0x000D}). Contains the desired or granted
     * allocation lifetime in seconds.
     * <p>Defined in RFC 5766 Section 14.2.</p>
     */
    public static final int ATTR_LIFETIME             = 0x000D;

    /**
     * XOR-PEER-ADDRESS attribute ({@code 0x0012}). Contains a peer's
     * transport address XOR'd with the magic cookie, used in TURN
     * CreatePermission, ChannelBind, Send, and Data messages.
     * <p>Defined in RFC 5766 Section 14.3.</p>
     */
    public static final int ATTR_XOR_PEER_ADDRESS     = 0x0012;

    /**
     * DATA attribute ({@code 0x0013}). Contains the application data payload
     * in Send and Data indications.
     * <p>Defined in RFC 5766 Section 14.4.</p>
     */
    public static final int ATTR_DATA                 = 0x0013;

    /**
     * XOR-RELAYED-ADDRESS attribute ({@code 0x0016}). Contains the relay
     * transport address allocated by the TURN server, XOR'd with the
     * magic cookie.
     * <p>Defined in RFC 5766 Section 14.5.</p>
     */
    public static final int ATTR_XOR_RELAYED_ADDRESS  = 0x0016;

    /**
     * REQUESTED-TRANSPORT attribute ({@code 0x0019}). Specifies the
     * transport protocol requested for the allocation (e.g. UDP = 17).
     * <p>Defined in RFC 5766 Section 14.7.</p>
     */
    public static final int ATTR_REQUESTED_TRANSPORT  = 0x0019;

    /**
     * SOFTWARE attribute ({@code 0x8022}). Contains a textual description
     * of the software being used. This is a comprehension-optional attribute.
     * <p>Defined in RFC 5389 Section 15.10.</p>
     */
    public static final int ATTR_SOFTWARE             = 0x8022;

    /**
     * FINGERPRINT attribute ({@code 0x8028}). Contains a CRC-32 checksum
     * of the STUN message XOR'd with {@code 0x5354554E}, used to
     * distinguish STUN packets from other protocols on the same port.
     * <p>Defined in RFC 5389 Section 15.5.</p>
     */
    public static final int ATTR_FINGERPRINT          = 0x8028;

    // -------------------------------------------------------------------------
    // Error codes (RFC 5389 Section 15.6, RFC 5766 Section 14.8)
    // -------------------------------------------------------------------------

    /**
     * Error 400: Bad Request. The request was malformed or missing required
     * attributes.
     * <p>Defined in RFC 5389 Section 15.6.</p>
     */
    public static final int ERR_BAD_REQUEST           = 400;

    /**
     * Error 401: Unauthorized. The request did not contain valid credentials
     * (long-term). The response includes REALM and NONCE attributes for the
     * client to retry with.
     * <p>Defined in RFC 5389 Section 15.6.</p>
     */
    public static final int ERR_UNAUTHORIZED          = 401;

    /**
     * Error 403: Forbidden. The request was valid but the server refuses to
     * carry it out due to administrative policy.
     * <p>Defined in RFC 5766 Section 14.8.</p>
     */
    public static final int ERR_FORBIDDEN             = 403;

    /**
     * Error 420: Unknown Attribute. The server did not understand a
     * comprehension-required attribute in the request.
     * <p>Defined in RFC 5389 Section 15.6.</p>
     */
    public static final int ERR_UNKNOWN_ATTRIBUTE     = 420;

    /**
     * Error 437: Allocation Mismatch. The client attempted to create an
     * allocation that conflicts with an existing one, or tried to refresh
     * a non-existent allocation.
     * <p>Defined in RFC 5766 Section 6.2.</p>
     */
    public static final int ERR_ALLOCATION_MISMATCH   = 437;

    /**
     * Error 438: Stale Nonce. The nonce used in the request is no longer
     * valid; the client should retry with the new nonce in the response.
     * <p>Defined in RFC 5389 Section 15.6.</p>
     */
    public static final int ERR_STALE_NONCE           = 438;

    /**
     * Error 508: Insufficient Capacity. The server is unable to allocate
     * the requested relay resources (e.g. no available ports).
     * <p>Defined in RFC 5766 Section 6.2.</p>
     */
    public static final int ERR_INSUFFICIENT_CAPACITY = 508;

    // -------------------------------------------------------------------------
    // Transport protocol numbers
    // -------------------------------------------------------------------------

    /**
     * IANA protocol number for UDP (17), used in the REQUESTED-TRANSPORT
     * attribute. This TURN server implementation only supports UDP.
     */
    public static final int TRANSPORT_UDP = 17;

    // -------------------------------------------------------------------------
    // Channel number range (RFC 5766, Section 11)
    // -------------------------------------------------------------------------

    /**
     * Minimum valid TURN channel number ({@code 0x4000}).
     * Channel numbers below this value are reserved.
     * <p>Defined in RFC 5766 Section 11.</p>
     */
    public static final int CHANNEL_MIN = 0x4000;

    /**
     * Maximum valid TURN channel number ({@code 0x7FFF}).
     * Channel numbers above this value are reserved.
     * <p>Defined in RFC 5766 Section 11.</p>
     */
    public static final int CHANNEL_MAX = 0x7FFF;

    // -------------------------------------------------------------------------
    // Allocation lifetime defaults (RFC 5766, Section 6.2)
    // -------------------------------------------------------------------------

    /**
     * Default allocation lifetime in seconds (600 = 10 minutes).
     * Used when the client does not specify a LIFETIME attribute.
     * <p>Defined in RFC 5766 Section 6.2.</p>
     */
    public static final int DEFAULT_LIFETIME = 600;

    /**
     * Maximum allocation lifetime in seconds (3600 = 1 hour).
     * Client-requested lifetimes are capped at this value.
     */
    public static final int MAX_LIFETIME     = 3600;

    /**
     * Extracts the STUN method from a full 16-bit message type.
     *
     * <p>The method bits are interleaved with the class bits in the message
     * type field (RFC 5389 Section 6). This method reverses the interleaving
     * to recover the 12-bit method value.</p>
     *
     * @param messageType the 16-bit STUN message type
     * @return the extracted method (e.g. {@link #METHOD_BINDING},
     *         {@link #METHOD_ALLOCATE})
     */
    public static int getMethod(int messageType) {
        return (messageType & 0x000F)
             | ((messageType & 0x00E0) >> 1)
             | ((messageType & 0x3E00) >> 2);
    }

    /**
     * Extracts the STUN message class from a full 16-bit message type.
     *
     * <p>The class is encoded as two bits interleaved within the message type
     * (RFC 5389 Section 6). This method extracts those bits and returns a
     * 2-bit class value: 0 = Request, 1 = Indication, 2 = Success Response,
     * 3 = Error Response.</p>
     *
     * @param messageType the 16-bit STUN message type
     * @return the extracted class (0-3)
     */
    public static int getClass(int messageType) {
        return ((messageType & 0x0010) >> 4)
             | ((messageType & 0x0100) >> 7);
    }

    /**
     * Builds a full 16-bit STUN message type from a method and class by
     * interleaving their bits according to RFC 5389 Section 6.
     *
     * @param method the STUN method (e.g. {@link #METHOD_BINDING})
     * @param clazz  the STUN message class (0 = Request, 1 = Indication,
     *               2 = Success Response, 3 = Error Response)
     * @return the combined 16-bit message type
     */
    public static int buildMessageType(int method, int clazz) {
        int type = (method & 0x000F)
                 | ((method & 0x0070) << 1)
                 | ((method & 0x0F80) << 2)
                 | ((clazz & 0x01) << 4)
                 | ((clazz & 0x02) << 7);
        return type;
    }
}
