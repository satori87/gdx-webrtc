package com.github.satori87.gdx.webrtc;

/**
 * Internal signaling message exchanged between clients and the signaling server.
 * Simple JSON format: {"type":N,"source":S,"target":T,"data":"..."}
 */
public class SignalMessage {

    public static final int TYPE_WELCOME = 0;
    public static final int TYPE_CONNECT_REQUEST = 1;
    public static final int TYPE_OFFER = 2;
    public static final int TYPE_ANSWER = 3;
    public static final int TYPE_ICE = 4;
    public static final int TYPE_PEER_LIST = 5;
    public static final int TYPE_ERROR = 6;
    public static final int TYPE_PEER_JOINED = 7;
    public static final int TYPE_PEER_LEFT = 8;

    public int type;
    public int source;
    public int target;
    public String data;

    public SignalMessage() {
    }

    public SignalMessage(int type, int source, int target, String data) {
        this.type = type;
        this.source = source;
        this.target = target;
        this.data = data;
    }

    /** Encode to JSON string. */
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

    /** Parse from JSON string. Returns null on failure. */
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

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    static String extractString(String json, String key) {
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

    static int extractInt(String json, String key) {
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
}
