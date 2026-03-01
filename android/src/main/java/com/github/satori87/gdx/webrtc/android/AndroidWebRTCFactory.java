package com.github.satori87.gdx.webrtc.android;

import android.content.Context;

import com.github.satori87.gdx.webrtc.WebRTCClient;
import com.github.satori87.gdx.webrtc.WebRTCClientListener;
import com.github.satori87.gdx.webrtc.WebRTCConfiguration;
import com.github.satori87.gdx.webrtc.WebRTCFactory;

/**
 * Android WebRTC factory using Google's WebRTC SDK.
 *
 * <pre>
 * // In your Android launcher:
 * WebRTCClients.FACTORY = new AndroidWebRTCFactory(this);
 * </pre>
 */
public class AndroidWebRTCFactory implements WebRTCFactory {

    private final Context applicationContext;

    public AndroidWebRTCFactory(Context context) {
        this.applicationContext = context.getApplicationContext();
    }

    public WebRTCClient createClient(WebRTCConfiguration config, WebRTCClientListener listener) {
        return new AndroidWebRTCClient(applicationContext, config, listener);
    }
}
