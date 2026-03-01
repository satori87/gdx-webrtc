package com.github.satori87.gdx.webrtc.common;

import com.github.satori87.gdx.webrtc.WebRTCClient;
import com.github.satori87.gdx.webrtc.WebRTCClientListener;
import com.github.satori87.gdx.webrtc.WebRTCConfiguration;
import com.github.satori87.gdx.webrtc.WebRTCFactory;

/**
 * Desktop WebRTC factory using webrtc-java (dev.onvoid.webrtc).
 *
 * <pre>
 * // In your desktop launcher:
 * WebRTCClients.FACTORY = new DesktopWebRTCFactory();
 * </pre>
 */
public class DesktopWebRTCFactory implements WebRTCFactory {

    public WebRTCClient createClient(WebRTCConfiguration config, WebRTCClientListener listener) {
        return new DesktopWebRTCClient(config, listener);
    }
}
