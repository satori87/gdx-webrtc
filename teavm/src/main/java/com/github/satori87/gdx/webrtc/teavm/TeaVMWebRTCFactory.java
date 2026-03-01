package com.github.satori87.gdx.webrtc.teavm;

import com.github.satori87.gdx.webrtc.WebRTCClient;
import com.github.satori87.gdx.webrtc.WebRTCClientListener;
import com.github.satori87.gdx.webrtc.WebRTCConfiguration;
import com.github.satori87.gdx.webrtc.WebRTCFactory;

/**
 * TeaVM (browser) WebRTC factory using native browser RTCPeerConnection.
 *
 * <pre>
 * // In your TeaVM launcher:
 * WebRTCClients.FACTORY = new TeaVMWebRTCFactory();
 * </pre>
 */
public class TeaVMWebRTCFactory implements WebRTCFactory {

    public WebRTCClient createClient(WebRTCConfiguration config, WebRTCClientListener listener) {
        return new TeaVMWebRTCClient(config, listener);
    }
}
