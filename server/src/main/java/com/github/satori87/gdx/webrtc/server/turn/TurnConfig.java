package com.github.satori87.gdx.webrtc.server.turn;

/**
 * Configuration for the embedded TURN server.
 */
public class TurnConfig {
    /** Public hostname/IP that clients use to reach this TURN server. */
    public String host = "0.0.0.0";
    /** UDP port for TURN (default 3478). */
    public int port = 3478;
    /** TURN realm for long-term credentials. */
    public String realm = "webrtc";
    /** Fixed username for long-term credentials. */
    public String username = "webrtc";
    /** Fixed password for long-term credentials. */
    public String password = "webrtc";
}
