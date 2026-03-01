package com.github.satori87.gdx.webrtc.server;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SignalingServerConfigTest {

    @Test
    void defaultPortIs9090() {
        assertEquals(9090, SignalingServerConfig.DEFAULT_PORT);
    }

    @Test
    void defaultConnectionLostTimeoutIs30() {
        assertEquals(30, SignalingServerConfig.DEFAULT_CONNECTION_LOST_TIMEOUT);
    }

    @Test
    void defaultStopTimeoutIs1000() {
        assertEquals(1000, SignalingServerConfig.DEFAULT_STOP_TIMEOUT_MS);
    }

    @Test
    void fieldDefaultsMatchConstants() {
        SignalingServerConfig config = new SignalingServerConfig();
        assertEquals(SignalingServerConfig.DEFAULT_PORT, config.port);
        assertEquals(SignalingServerConfig.DEFAULT_CONNECTION_LOST_TIMEOUT, config.connectionLostTimeout);
        assertEquals(SignalingServerConfig.DEFAULT_STOP_TIMEOUT_MS, config.stopTimeoutMs);
    }

    @Test
    void fieldsAreModifiable() {
        SignalingServerConfig config = new SignalingServerConfig();
        config.port = 8080;
        config.connectionLostTimeout = 60;
        config.stopTimeoutMs = 5000;
        assertEquals(8080, config.port);
        assertEquals(60, config.connectionLostTimeout);
        assertEquals(5000, config.stopTimeoutMs);
    }
}
