package com.github.satori87.gdx.webrtc.ios.bindings;

import org.robovm.apple.foundation.NSObject;
import org.robovm.objc.annotation.Method;
import org.robovm.objc.annotation.NativeClass;

/**
 * RoboVM Java binding for the Objective-C {@code RTCPeerConnectionFactory} class
 * from Apple's WebRTC.framework.
 *
 * <p>RTCPeerConnectionFactory is the entry point for creating {@link RTCPeerConnection}
 * instances on iOS. It encapsulates the native WebRTC runtime initialization
 * and manages the underlying audio/video/network resources.</p>
 *
 * <p>{@code IOSPeerConnectionProvider} maintains a singleton instance of this factory,
 * initialized lazily with synchronized access, and reuses it for all peer connections
 * within the application lifecycle.</p>
 *
 * @see RTCPeerConnection
 * @see RTCConfiguration
 * @see RTCPeerConnectionDelegate
 */
@NativeClass
public class RTCPeerConnectionFactory extends NSObject {

    /**
     * Creates a new peer connection with the given configuration, constraints,
     * and delegate.
     *
     * <p>The delegate begins receiving callbacks (ICE candidates, connection state
     * changes, incoming data channels) immediately after creation.</p>
     *
     * @param configuration the ICE and transport configuration for the connection
     * @param constraints   media constraints for the connection (typically created via
     *                      {@link RTCMediaConstraints#create()})
     * @param delegate      the delegate to receive peer connection lifecycle callbacks
     * @return a new peer connection, or {@code null} on failure
     * @see RTCPeerConnection
     */
    @Method(selector = "peerConnectionWithConfiguration:constraints:delegate:")
    public native RTCPeerConnection createPeerConnection(
            RTCConfiguration configuration,
            RTCMediaConstraints constraints,
            RTCPeerConnectionDelegate delegate);

    /**
     * Creates a new RTCPeerConnectionFactory instance.
     *
     * <p>Initializes the native WebRTC runtime. This is a heavyweight operation
     * and should typically be done once per application lifecycle.</p>
     *
     * @return a new factory instance
     */
    public static RTCPeerConnectionFactory create() {
        return new RTCPeerConnectionFactory();
    }
}
