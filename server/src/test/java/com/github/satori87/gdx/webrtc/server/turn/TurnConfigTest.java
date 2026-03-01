package com.github.satori87.gdx.webrtc.server.turn;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TurnConfigTest {

    @Test
    void defaultHost() {
        assertEquals("0.0.0.0", TurnConfig.DEFAULT_HOST);
    }

    @Test
    void defaultPort() {
        assertEquals(3478, TurnConfig.DEFAULT_PORT);
    }

    @Test
    void defaultRealm() {
        assertEquals("webrtc", TurnConfig.DEFAULT_REALM);
    }

    @Test
    void defaultUsername() {
        assertEquals("webrtc", TurnConfig.DEFAULT_USERNAME);
    }

    @Test
    void defaultPassword() {
        assertEquals("webrtc", TurnConfig.DEFAULT_PASSWORD);
    }

    @Test
    void defaultCleanupInterval() {
        assertEquals(30000, TurnConfig.DEFAULT_CLEANUP_INTERVAL_MS);
    }

    @Test
    void defaultRelayTimeout() {
        assertEquals(5000, TurnConfig.DEFAULT_RELAY_TIMEOUT_MS);
    }

    @Test
    void defaultReceiveBufferSize() {
        assertEquals(65536, TurnConfig.DEFAULT_RECEIVE_BUFFER_SIZE);
    }

    @Test
    void defaultMinLifetime() {
        assertEquals(60, TurnConfig.DEFAULT_MIN_LIFETIME);
    }

    @Test
    void fieldDefaultsMatchConstants() {
        TurnConfig config = new TurnConfig();
        assertEquals(TurnConfig.DEFAULT_HOST, config.host);
        assertEquals(TurnConfig.DEFAULT_PORT, config.port);
        assertEquals(TurnConfig.DEFAULT_REALM, config.realm);
        assertEquals(TurnConfig.DEFAULT_USERNAME, config.username);
        assertEquals(TurnConfig.DEFAULT_PASSWORD, config.password);
        assertEquals(TurnConfig.DEFAULT_CLEANUP_INTERVAL_MS, config.cleanupIntervalMs);
        assertEquals(TurnConfig.DEFAULT_RELAY_TIMEOUT_MS, config.relayTimeoutMs);
        assertEquals(TurnConfig.DEFAULT_RECEIVE_BUFFER_SIZE, config.receiveBufferSize);
        assertEquals(TurnConfig.DEFAULT_MIN_LIFETIME, config.minLifetime);
    }

    @Test
    void fieldsAreModifiable() {
        TurnConfig config = new TurnConfig();
        config.host = "192.168.1.1";
        config.port = 5000;
        config.realm = "test-realm";
        config.username = "testuser";
        config.password = "testpass";
        config.cleanupIntervalMs = 60000;
        config.relayTimeoutMs = 10000;
        config.receiveBufferSize = 32768;
        config.minLifetime = 120;

        assertEquals("192.168.1.1", config.host);
        assertEquals(5000, config.port);
        assertEquals("test-realm", config.realm);
        assertEquals("testuser", config.username);
        assertEquals("testpass", config.password);
        assertEquals(60000, config.cleanupIntervalMs);
        assertEquals(10000, config.relayTimeoutMs);
        assertEquals(32768, config.receiveBufferSize);
        assertEquals(120, config.minLifetime);
    }
}
