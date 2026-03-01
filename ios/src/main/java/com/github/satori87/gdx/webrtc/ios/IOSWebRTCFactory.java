package com.github.satori87.gdx.webrtc.ios;

import com.github.satori87.gdx.webrtc.WebRTCClient;
import com.github.satori87.gdx.webrtc.WebRTCClientListener;
import com.github.satori87.gdx.webrtc.WebRTCConfiguration;
import com.github.satori87.gdx.webrtc.WebRTCFactory;

/**
 * iOS (RoboVM) WebRTC factory using native WebRTC.framework.
 *
 * <pre>
 * // In your iOS launcher:
 * WebRTCClients.FACTORY = new IOSWebRTCFactory();
 * </pre>
 */
public class IOSWebRTCFactory implements WebRTCFactory {

    public WebRTCClient createClient(WebRTCConfiguration config, WebRTCClientListener listener) {
        return new IOSWebRTCClient(config, listener);
    }
}
