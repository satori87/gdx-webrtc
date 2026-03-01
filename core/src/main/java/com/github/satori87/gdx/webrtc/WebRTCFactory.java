package com.github.satori87.gdx.webrtc;

/**
 * Factory for creating platform-specific WebRTC clients.
 * Set by platform initialization (e.g. DesktopWebRTCFactory, TeaVMWebRTCFactory).
 */
public interface WebRTCFactory {

    WebRTCClient createClient(WebRTCConfiguration config, WebRTCClientListener listener);
}
