package com.github.satori87.gdx.webrtc;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WebRTCConfigurationTest {

    @Test
    void defaultsMatchConstants() {
        WebRTCConfiguration config = new WebRTCConfiguration();
        assertEquals(WebRTCConfiguration.DEFAULT_ICE_RESTART_DELAY_MS, config.iceRestartDelayMs);
        assertEquals(WebRTCConfiguration.DEFAULT_MAX_ICE_RESTART_ATTEMPTS, config.maxIceRestartAttempts);
        assertEquals(WebRTCConfiguration.DEFAULT_UNRELIABLE_BUFFER_LIMIT, config.unreliableBufferLimit);
        assertEquals(WebRTCConfiguration.DEFAULT_ICE_BACKOFF_BASE_MS, config.iceBackoffBaseMs);
        assertEquals(WebRTCConfiguration.DEFAULT_UNRELIABLE_MAX_RETRANSMITS, config.unreliableMaxRetransmits);
    }

    @Test
    void defaultConstantValues() {
        assertEquals(3500, WebRTCConfiguration.DEFAULT_ICE_RESTART_DELAY_MS);
        assertEquals(3, WebRTCConfiguration.DEFAULT_MAX_ICE_RESTART_ATTEMPTS);
        assertEquals(65536L, WebRTCConfiguration.DEFAULT_UNRELIABLE_BUFFER_LIMIT);
        assertEquals(2000, WebRTCConfiguration.DEFAULT_ICE_BACKOFF_BASE_MS);
        assertEquals(0, WebRTCConfiguration.DEFAULT_UNRELIABLE_MAX_RETRANSMITS);
    }

    @Test
    void gettersReturnFieldValues() {
        WebRTCConfiguration config = new WebRTCConfiguration();
        config.iceRestartDelayMs = 5000;
        config.maxIceRestartAttempts = 5;
        config.unreliableBufferLimit = 131072;
        config.iceBackoffBaseMs = 3000;
        config.unreliableMaxRetransmits = 2;

        assertEquals(5000, config.getIceRestartDelayMs());
        assertEquals(5, config.getMaxIceRestartAttempts());
        assertEquals(131072L, config.getUnreliableBufferLimit());
        assertEquals(3000, config.getIceBackoffBaseMs());
        assertEquals(2, config.getUnreliableMaxRetransmits());
    }

    @Test
    void settersUpdateFields() {
        WebRTCConfiguration config = new WebRTCConfiguration();
        config.setIceRestartDelayMs(1000);
        config.setMaxIceRestartAttempts(10);
        config.setUnreliableBufferLimit(32768);
        config.setIceBackoffBaseMs(500);
        config.setUnreliableMaxRetransmits(3);

        assertEquals(1000, config.iceRestartDelayMs);
        assertEquals(10, config.maxIceRestartAttempts);
        assertEquals(32768L, config.unreliableBufferLimit);
        assertEquals(500, config.iceBackoffBaseMs);
        assertEquals(3, config.unreliableMaxRetransmits);
    }

    @Test
    void defaultStringFields() {
        WebRTCConfiguration config = new WebRTCConfiguration();
        assertNull(config.signalingServerUrl);
        assertEquals("stun:stun.l.google.com:19302", config.stunServer);
        assertNull(config.turnServer);
        assertNull(config.turnUsername);
        assertNull(config.turnPassword);
        assertFalse(config.forceRelay);
    }

    @Test
    void defaultStunServersHasThreeEntries() {
        String[] defaults = WebRTCConfiguration.DEFAULT_STUN_SERVERS;
        assertEquals(3, defaults.length);
        assertEquals("stun:stun.l.google.com:19302", defaults[0]);
        assertEquals("stun:stun1.l.google.com:19302", defaults[1]);
        assertEquals("stun:stun.cloudflare.com:3478", defaults[2]);
    }

    @Test
    void stunServersDefaultMatchesConstant() {
        WebRTCConfiguration config = new WebRTCConfiguration();
        assertArrayEquals(WebRTCConfiguration.DEFAULT_STUN_SERVERS, config.stunServers);
    }

    @Test
    void roomDefaultsToNull() {
        WebRTCConfiguration config = new WebRTCConfiguration();
        assertNull(config.room);
    }

    @Test
    void roomIsAssignable() {
        WebRTCConfiguration config = new WebRTCConfiguration();
        config.room = "my-game-room";
        assertEquals("my-game-room", config.room);
    }

    @Test
    void setStunServerUpdatesBothFields() {
        WebRTCConfiguration config = new WebRTCConfiguration();
        config.setStunServer("stun:custom.example.com:3478");
        assertEquals("stun:custom.example.com:3478", config.stunServer);
        assertEquals(1, config.stunServers.length);
        assertEquals("stun:custom.example.com:3478", config.stunServers[0]);
    }
}
