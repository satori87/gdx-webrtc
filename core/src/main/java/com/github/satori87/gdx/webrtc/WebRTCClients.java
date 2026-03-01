package com.github.satori87.gdx.webrtc;

/**
 * Static accessor for WebRTC client creation.
 * Platform modules must set the FACTORY field during initialization.
 *
 * <pre>
 * // Desktop:
 * WebRTCClients.FACTORY = new DesktopWebRTCFactory();
 *
 * // TeaVM:
 * WebRTCClients.FACTORY = new TeaVMWebRTCFactory();
 *
 * // Then in platform-agnostic code:
 * WebRTCClient client = WebRTCClients.newClient(config, listener);
 * </pre>
 */
public class WebRTCClients {

    /** Platform-specific factory. Must be set before calling newClient(). */
    public static WebRTCFactory FACTORY;

    /**
     * Create a new WebRTC client using the platform factory.
     * @throws IllegalStateException if FACTORY has not been set
     */
    public static WebRTCClient newClient(WebRTCConfiguration config, WebRTCClientListener listener) {
        if (FACTORY == null) {
            throw new IllegalStateException(
                "WebRTCClients.FACTORY is not set. "
                + "Call WebRTCClients.FACTORY = new DesktopWebRTCFactory() "
                + "or the platform equivalent before use.");
        }
        return FACTORY.createClient(config, listener);
    }
}
