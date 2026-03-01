package com.github.satori87.gdx.webrtc;

/**
 * Configuration for WebRTC connections.
 */
public class WebRTCConfiguration {

    /** URL of the signaling server (e.g. "ws://localhost:9090"). Required. */
    public String signalingServerUrl;

    /** STUN server URL. Default: Google's public STUN server. */
    public String stunServer = "stun:stun.l.google.com:19302";

    /** Optional TURN server URL (e.g. "turn:myserver.com:3478"). */
    public String turnServer;

    /** TURN username. */
    public String turnUsername;

    /** TURN password. */
    public String turnPassword;

    /** Force relay-only ICE transport (for TURN testing). Default false. */
    public boolean forceRelay = false;

    /** Delay in ms before ICE restart on temporary disconnect. Default 3500. */
    public int iceRestartDelayMs = 3500;

    /** Max ICE restart attempts after FAILED state. Default 3. */
    public int maxIceRestartAttempts = 3;
}
