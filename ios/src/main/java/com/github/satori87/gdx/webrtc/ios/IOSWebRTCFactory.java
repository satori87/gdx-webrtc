package com.github.satori87.gdx.webrtc.ios;

import com.github.satori87.gdx.webrtc.BaseWebRTCClient;
import com.github.satori87.gdx.webrtc.WebRTCClient;
import com.github.satori87.gdx.webrtc.WebRTCClientListener;
import com.github.satori87.gdx.webrtc.WebRTCConfiguration;
import com.github.satori87.gdx.webrtc.WebRTCFactory;

/**
 * iOS (RoboVM) WebRTC factory using native WebRTC.framework.
 * Constructs a BaseWebRTCClient with iOS-specific strategy implementations.
 *
 * <pre>
 * // In your iOS launcher:
 * WebRTCClients.FACTORY = new IOSWebRTCFactory();
 * </pre>
 */
public class IOSWebRTCFactory implements WebRTCFactory {

    /**
     * {@inheritDoc}
     *
     * <p>Constructs a {@link BaseWebRTCClient} wired with iOS-specific strategy
     * implementations: {@link IOSPeerConnectionProvider} for native WebRTC operations,
     * {@link IOSSignalingProvider} for WebSocket signaling via Java-WebSocket, and
     * {@link ExecutorScheduler} for delayed task scheduling.</p>
     */
    public WebRTCClient createClient(WebRTCConfiguration config, WebRTCClientListener listener) {
        return new BaseWebRTCClient("[WebRTC-iOS] ", config, listener,
                new IOSPeerConnectionProvider(),
                new IOSSignalingProvider(),
                new ExecutorScheduler());
    }
}
