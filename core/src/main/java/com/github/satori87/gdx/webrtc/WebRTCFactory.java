package com.github.satori87.gdx.webrtc;

/**
 * Factory interface for creating platform-specific {@link WebRTCClient} instances.
 *
 * <p>Each platform module provides an implementation of this interface:</p>
 * <ul>
 *   <li>{@code DesktopWebRTCFactory} (common module) - Desktop/JVM via webrtc-java</li>
 *   <li>{@code TeaVMWebRTCFactory} (teavm module) - Browser via JavaScript WebRTC API</li>
 *   <li>{@code AndroidWebRTCFactory} (android module) - Android via Google WebRTC SDK</li>
 *   <li>{@code IOSWebRTCFactory} (ios module) - iOS via WebRTC.framework + RoboVM</li>
 * </ul>
 *
 * <p>Set the appropriate factory on {@link WebRTCClients#FACTORY} during platform
 * initialization, then use {@link WebRTCClients#newClient(WebRTCConfiguration, WebRTCClientListener)}
 * to create clients.</p>
 *
 * @see WebRTCClients
 */
public interface WebRTCFactory {

    /**
     * Creates a new {@link WebRTCClient} with the given configuration and listener.
     *
     * <p>The returned client is not yet connected; call {@link WebRTCClient#connect()}
     * to initiate the signaling connection.</p>
     *
     * @param config   the WebRTC and signaling configuration
     * @param listener the listener for connection and data events
     * @return a new, unconnected WebRTC client
     */
    WebRTCClient createClient(WebRTCConfiguration config, WebRTCClientListener listener);
}
