package com.github.satori87.gdx.webrtc;

/**
 * Represents a signaling message exchanged between clients and the signaling server.
 *
 * <p>Messages are serialized as simple JSON objects with four fields:
 * {@code {"type":N,"source":S,"target":T,"data":"..."}}</p>
 *
 * <p>The signaling server acts as a dumb relay: it stamps the sender's ID into
 * the {@code source} field and forwards the message to the peer identified by
 * {@code target}. Broadcast messages (like PEER_JOINED/PEER_LEFT) use
 * {@code target=0}.</p>
 *
 * <h3>Message Types</h3>
 * <table>
 *   <tr><td>{@link #TYPE_WELCOME}</td><td>Server assigns a peer ID to a newly connected client</td></tr>
 *   <tr><td>{@link #TYPE_CONNECT_REQUEST}</td><td>Client requests a peer connection (receiver creates the SDP offer)</td></tr>
 *   <tr><td>{@link #TYPE_OFFER}</td><td>SDP offer from the offerer to the answerer</td></tr>
 *   <tr><td>{@link #TYPE_ANSWER}</td><td>SDP answer from the answerer to the offerer</td></tr>
 *   <tr><td>{@link #TYPE_ICE}</td><td>ICE candidate exchange between peers</td></tr>
 *   <tr><td>{@link #TYPE_PEER_LIST}</td><td>List of currently connected peer IDs (currently unused by clients)</td></tr>
 *   <tr><td>{@link #TYPE_ERROR}</td><td>Error message from the server</td></tr>
 *   <tr><td>{@link #TYPE_PEER_JOINED}</td><td>Notification that a new peer joined the server</td></tr>
 *   <tr><td>{@link #TYPE_PEER_LEFT}</td><td>Notification that a peer left the server</td></tr>
 * </table>
 *
 * <p>JSON parsing is hand-rolled (no external JSON library dependency) to keep
 * the core module dependency-free.</p>
 *
 * @see SignalingProvider
 * @see SignalingEventHandler
 */
public class SignalMessage {

    /** Server assigns a peer ID to a newly connected client. Data contains the ID as a string. */
    public static final int TYPE_WELCOME = 0;

    /** Client requests a peer-to-peer connection. The receiver becomes the SDP offerer. */
    public static final int TYPE_CONNECT_REQUEST = 1;

    /** SDP offer. Data contains the SDP string. */
    public static final int TYPE_OFFER = 2;

    /** SDP answer. Data contains the SDP string. */
    public static final int TYPE_ANSWER = 3;

    /** ICE candidate. Data contains JSON with candidate, sdpMid, and sdpMLineIndex. */
    public static final int TYPE_ICE = 4;

    /** List of connected peer IDs. Currently ignored by clients. */
    public static final int TYPE_PEER_LIST = 5;

    /** Error message from the signaling server. Data contains the error description. */
    public static final int TYPE_ERROR = 6;

    /** Notification that a new peer joined. Source contains the peer's ID. */
    public static final int TYPE_PEER_JOINED = 7;

    /** Notification that a peer left. Source contains the peer's ID. */
    public static final int TYPE_PEER_LEFT = 8;

    /** The message type (one of the TYPE_* constants). */
    public int type;

    /** The sender's peer ID (stamped by the signaling server). */
    public int source;

    /** The intended recipient's peer ID (0 for broadcast messages). */
    public int target;

    /** The message payload (SDP string, ICE candidate JSON, error text, etc.). */
    public String data;

    /**
     * Creates an empty signal message with all fields at their defaults.
     */
    public SignalMessage() {
    }

    /**
     * Creates a signal message with the specified fields.
     *
     * @param type   the message type (one of the TYPE_* constants)
     * @param source the sender's peer ID
     * @param target the recipient's peer ID (0 for broadcast)
     * @param data   the message payload
     */
    public SignalMessage(int type, int source, int target, String data) {
        this.type = type;
        this.source = source;
        this.target = target;
        this.data = data;
    }

    /**
     * Serializes this message to a JSON string.
     *
     * <p>The output format is:
     * {@code {"type":N,"source":S,"target":T,"data":"..."}}</p>
     *
     * <p>Null data is treated as an empty string. Special characters in data
     * are escaped via {@link #escapeJson(String)}.</p>
     *
     * @return the JSON representation of this message
     */
    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"type\":").append(type);
        sb.append(",\"source\":").append(source);
        sb.append(",\"target\":").append(target);
        sb.append(",\"data\":\"");
        sb.append(escapeJson(data != null ? data : ""));
        sb.append("\"}");
        return sb.toString();
    }

    /**
     * Parses a JSON string into a {@code SignalMessage}.
     *
     * <p>Expects the format produced by {@link #toJson()}. Missing fields
     * default to 0 (for integers) or empty string (for data).</p>
     *
     * @param json the JSON string to parse
     * @return the parsed message, or {@code null} if the input is null,
     *         empty, or too short to be valid JSON
     */
    public static SignalMessage fromJson(String json) {
        if (json == null || json.length() < 2) {
            return null;
        }
        SignalMessage msg = new SignalMessage();
        msg.type = extractInt(json, "type");
        msg.source = extractInt(json, "source");
        msg.target = extractInt(json, "target");
        msg.data = extractString(json, "data");
        return msg;
    }

    /**
     * Escapes a string for safe embedding within a JSON string value.
     *
     * <p>Escapes backslashes, double quotes, newlines, and carriage returns.</p>
     *
     * @param s the string to escape
     * @return the JSON-escaped string
     */
    public static String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    /**
     * Extracts a string value from a JSON object by key name.
     *
     * <p>Handles escaped quotes within the value. This is a lightweight
     * parser that avoids external JSON library dependencies.</p>
     *
     * @param json the JSON string to search
     * @param key  the key name to look for (without quotes)
     * @return the unescaped string value, or an empty string if the key is not found
     */
    public static String extractString(String json, String key) {
        String search = "\"" + key + "\":\"";
        int start = json.indexOf(search);
        if (start < 0) {
            return "";
        }
        start += search.length();
        int end = start;
        while (end < json.length()) {
            char c = json.charAt(end);
            if (c == '\\') {
                end += 2;
                continue;
            }
            if (c == '"') {
                break;
            }
            end++;
        }
        if (end >= json.length()) {
            return "";
        }
        return json.substring(start, end)
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\n", "\n")
                .replace("\\r", "\r");
    }

    /**
     * Extracts an integer value from a JSON object by key name.
     *
     * <p>Supports negative numbers. Returns 0 if the key is not found
     * or the value is not a valid integer.</p>
     *
     * @param json the JSON string to search
     * @param key  the key name to look for (without quotes)
     * @return the integer value, or 0 if not found or not parseable
     */
    public static int extractInt(String json, String key) {
        String search = "\"" + key + "\":";
        int start = json.indexOf(search);
        if (start < 0) {
            return 0;
        }
        start += search.length();
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) {
            end++;
        }
        try {
            return Integer.parseInt(json.substring(start, end));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Builds an ICE candidate JSON string from its component parts.
     *
     * <p>Produces JSON in the format:
     * {@code {"candidate":"...","sdpMid":"...","sdpMLineIndex":N}}</p>
     *
     * <p>Used by platform {@link PeerConnectionProvider} implementations when
     * generating ICE candidate messages to send via the signaling server.</p>
     *
     * @param candidate     the ICE candidate string (e.g. {@code "candidate:123 1 udp ..."})
     * @param sdpMid        the SDP media stream ID (e.g. {@code "audio"}, {@code "video"},
     *                      or {@code "0"})
     * @param sdpMLineIndex the zero-based index of the media description in the SDP
     * @return the JSON-encoded ICE candidate string
     */
    public static String buildIceCandidateJson(String candidate, String sdpMid, int sdpMLineIndex) {
        return "{\"candidate\":\"" + escapeJson(candidate) + "\","
                + "\"sdpMid\":\"" + escapeJson(sdpMid) + "\","
                + "\"sdpMLineIndex\":" + sdpMLineIndex + "}";
    }
}
