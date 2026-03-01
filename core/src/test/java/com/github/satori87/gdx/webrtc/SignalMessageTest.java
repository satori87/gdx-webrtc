package com.github.satori87.gdx.webrtc;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SignalMessageTest {

    @Test
    void toJsonAndFromJsonRoundtrip() {
        SignalMessage msg = new SignalMessage(SignalMessage.TYPE_OFFER, 1, 2, "sdp-data");
        String json = msg.toJson();
        SignalMessage parsed = SignalMessage.fromJson(json);
        assertNotNull(parsed);
        assertEquals(SignalMessage.TYPE_OFFER, parsed.type);
        assertEquals(1, parsed.source);
        assertEquals(2, parsed.target);
        assertEquals("sdp-data", parsed.data);
    }

    @Test
    void toJsonWithSpecialCharacters() {
        SignalMessage msg = new SignalMessage(0, 0, 0, "line1\nline2\r\"quoted\"\\back");
        String json = msg.toJson();
        SignalMessage parsed = SignalMessage.fromJson(json);
        assertNotNull(parsed);
        assertEquals("line1\nline2\r\"quoted\"\\back", parsed.data);
    }

    @Test
    void toJsonWithNullData() {
        SignalMessage msg = new SignalMessage(0, 0, 0, null);
        String json = msg.toJson();
        assertNotNull(json);
        assertTrue(json.contains("\"data\":\"\""));
    }

    @Test
    void fromJsonWithNullReturnsNull() {
        assertNull(SignalMessage.fromJson(null));
    }

    @Test
    void fromJsonWithEmptyStringReturnsNull() {
        assertNull(SignalMessage.fromJson(""));
        assertNull(SignalMessage.fromJson("x"));
    }

    @Test
    void fromJsonWithAllMessageTypes() {
        for (int type = 0; type <= 8; type++) {
            SignalMessage msg = new SignalMessage(type, 10, 20, "test");
            SignalMessage parsed = SignalMessage.fromJson(msg.toJson());
            assertNotNull(parsed);
            assertEquals(type, parsed.type);
        }
    }

    @Test
    void escapeJsonHandlesNull() {
        // escapeJson is called internally by toJson with null guard
        SignalMessage msg = new SignalMessage(0, 0, 0, null);
        assertDoesNotThrow(() -> msg.toJson());
    }

    @Test
    void escapeJsonEscapesSpecialCharacters() {
        String escaped = SignalMessage.escapeJson("a\"b\\c\nd\re");
        assertEquals("a\\\"b\\\\c\\nd\\re", escaped);
    }

    @Test
    void extractStringFindsValue() {
        String json = "{\"name\":\"hello\",\"other\":\"world\"}";
        assertEquals("hello", SignalMessage.extractString(json, "name"));
        assertEquals("world", SignalMessage.extractString(json, "other"));
    }

    @Test
    void extractStringReturnEmptyWhenKeyMissing() {
        assertEquals("", SignalMessage.extractString("{\"a\":\"b\"}", "missing"));
    }

    @Test
    void extractStringHandlesEscapedQuotes() {
        String json = "{\"val\":\"hello\\\"world\"}";
        assertEquals("hello\"world", SignalMessage.extractString(json, "val"));
    }

    @Test
    void extractIntFindsValue() {
        String json = "{\"count\":42,\"neg\":-5}";
        assertEquals(42, SignalMessage.extractInt(json, "count"));
        assertEquals(-5, SignalMessage.extractInt(json, "neg"));
    }

    @Test
    void extractIntReturnsZeroWhenMissing() {
        assertEquals(0, SignalMessage.extractInt("{\"a\":1}", "missing"));
    }

    @Test
    void extractIntReturnsZeroForNonNumeric() {
        assertEquals(0, SignalMessage.extractInt("{\"a\":\"text\"}", "a"));
    }

    @Test
    void buildIceCandidateJsonFormatsCorrectly() {
        String json = SignalMessage.buildIceCandidateJson("cand1", "audio", 0);
        assertTrue(json.contains("\"candidate\":\"cand1\""));
        assertTrue(json.contains("\"sdpMid\":\"audio\""));
        assertTrue(json.contains("\"sdpMLineIndex\":0"));
    }

    @Test
    void buildIceCandidateJsonEscapesSpecialChars() {
        String json = SignalMessage.buildIceCandidateJson("a\"b", "c\\d", 1);
        assertTrue(json.contains("a\\\"b"));
        assertTrue(json.contains("c\\\\d"));
    }

    @Test
    void buildIceCandidateJsonRoundtrip() {
        String json = SignalMessage.buildIceCandidateJson("candidate:123", "video", 1);
        assertEquals("candidate:123", SignalMessage.extractString(json, "candidate"));
        assertEquals("video", SignalMessage.extractString(json, "sdpMid"));
        assertEquals(1, SignalMessage.extractInt(json, "sdpMLineIndex"));
    }

    @Test
    void defaultConstructorCreatesEmptyMessage() {
        SignalMessage msg = new SignalMessage();
        assertEquals(0, msg.type);
        assertEquals(0, msg.source);
        assertEquals(0, msg.target);
        assertNull(msg.data);
    }
}
