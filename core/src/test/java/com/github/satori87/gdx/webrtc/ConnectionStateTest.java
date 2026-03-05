package com.github.satori87.gdx.webrtc;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConnectionStateTest {

    @Test
    void constantValues() {
        assertEquals(0, ConnectionState.NEW);
        assertEquals(1, ConnectionState.CONNECTING);
        assertEquals(2, ConnectionState.CONNECTED);
        assertEquals(3, ConnectionState.DISCONNECTED);
        assertEquals(4, ConnectionState.FAILED);
        assertEquals(5, ConnectionState.CLOSED);
    }

    @Test
    void toStringReturnsCorrectNames() {
        assertEquals("NEW", ConnectionState.toString(ConnectionState.NEW));
        assertEquals("CONNECTING", ConnectionState.toString(ConnectionState.CONNECTING));
        assertEquals("CONNECTED", ConnectionState.toString(ConnectionState.CONNECTED));
        assertEquals("DISCONNECTED", ConnectionState.toString(ConnectionState.DISCONNECTED));
        assertEquals("FAILED", ConnectionState.toString(ConnectionState.FAILED));
        assertEquals("CLOSED", ConnectionState.toString(ConnectionState.CLOSED));
    }

    @Test
    void toStringReturnsUnknownForInvalidState() {
        assertEquals("UNKNOWN(99)", ConnectionState.toString(99));
        assertEquals("UNKNOWN(-1)", ConnectionState.toString(-1));
    }
}
