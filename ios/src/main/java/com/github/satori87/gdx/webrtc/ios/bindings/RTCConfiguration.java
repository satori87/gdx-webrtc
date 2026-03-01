package com.github.satori87.gdx.webrtc.ios.bindings;

import org.robovm.apple.foundation.NSArray;
import org.robovm.apple.foundation.NSObject;
import org.robovm.objc.annotation.NativeClass;
import org.robovm.objc.annotation.Property;

/**
 * RoboVM Java binding for the Objective-C {@code RTCConfiguration} class
 * from Apple's WebRTC.framework.
 *
 * <p>An RTCConfiguration holds the settings used to create an {@link RTCPeerConnection},
 * including the list of ICE servers (STUN/TURN) and the ICE transport policy.
 * It is built by {@code IOSPeerConnectionProvider} from the library's
 * {@code WebRTCConfiguration} and passed to
 * {@link RTCPeerConnectionFactory#createPeerConnection(RTCConfiguration, RTCMediaConstraints, RTCPeerConnectionDelegate)}.</p>
 *
 * @see RTCIceServer
 * @see RTCIceTransportPolicy
 * @see RTCPeerConnectionFactory
 */
@NativeClass
public class RTCConfiguration extends NSObject {

    /**
     * Returns the list of ICE servers configured for this connection.
     *
     * @return an array of ICE servers (STUN and/or TURN)
     */
    @Property(selector = "iceServers")
    public native NSArray<RTCIceServer> getIceServers();

    /**
     * Sets the list of ICE servers used for candidate gathering.
     *
     * @param iceServers an array of STUN and/or TURN servers
     * @see RTCIceServer
     */
    @Property(selector = "setIceServers:")
    public native void setIceServers(NSArray<RTCIceServer> iceServers);

    /**
     * Returns the ICE transport policy controlling which candidate types are permitted.
     *
     * <p>The returned integer corresponds to one of the constants in
     * {@link RTCIceTransportPolicy}.</p>
     *
     * @return the ICE transport policy as a native enum ordinal
     * @see RTCIceTransportPolicy
     */
    @Property(selector = "iceTransportPolicy")
    public native int getIceTransportPolicy();

    /**
     * Sets the ICE transport policy controlling which candidate types are permitted.
     *
     * <p>For example, setting this to {@link RTCIceTransportPolicy#RELAY} forces
     * all traffic through TURN servers, which is useful for NAT traversal testing
     * or privacy.</p>
     *
     * @param policy one of the {@link RTCIceTransportPolicy} constants
     * @see RTCIceTransportPolicy
     */
    @Property(selector = "setIceTransportPolicy:")
    public native void setIceTransportPolicy(int policy);

    /**
     * Creates a new default RTCConfiguration instance.
     *
     * @return a new configuration with default settings
     */
    public static RTCConfiguration create() {
        return new RTCConfiguration();
    }
}
