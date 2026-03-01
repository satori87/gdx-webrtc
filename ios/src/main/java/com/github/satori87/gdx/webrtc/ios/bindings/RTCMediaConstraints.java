package com.github.satori87.gdx.webrtc.ios.bindings;

import org.robovm.apple.foundation.NSDictionary;
import org.robovm.apple.foundation.NSObject;
import org.robovm.apple.foundation.NSString;
import org.robovm.objc.annotation.Method;
import org.robovm.objc.annotation.NativeClass;

/**
 * RoboVM Java binding for the Objective-C {@code RTCMediaConstraints} class
 * from Apple's WebRTC.framework.
 *
 * <p>RTCMediaConstraints specifies mandatory and optional constraints for
 * creating SDP offers and answers. In gdx-webrtc (which uses data channels
 * only, not audio/video), constraints are typically empty or contain only
 * the ICE restart flag.</p>
 *
 * @see RTCPeerConnection#createOffer(RTCMediaConstraints, org.robovm.objc.block.VoidBlock2)
 * @see RTCPeerConnection#createAnswer(RTCMediaConstraints, org.robovm.objc.block.VoidBlock2)
 */
@NativeClass
public class RTCMediaConstraints extends NSObject {

    /**
     * Initializes this constraints object with mandatory and optional constraint dictionaries.
     *
     * <p>This is the native Objective-C initializer. Use {@link #create()} or
     * {@link #createWithIceRestart()} instead for convenience.</p>
     *
     * @param mandatory dictionary of mandatory constraints, or {@code null} for none
     * @param optional  dictionary of optional constraints, or {@code null} for none
     * @return the native object pointer
     */
    @Method(selector = "initWithMandatoryConstraints:optionalConstraints:")
    protected native long initWithConstraints(
            NSDictionary<NSString, NSString> mandatory,
            NSDictionary<NSString, NSString> optional);

    /**
     * Creates empty media constraints with no mandatory or optional entries.
     *
     * <p>This is the default used for most SDP offer/answer creation in gdx-webrtc.</p>
     *
     * @return a new empty constraints instance
     */
    public static RTCMediaConstraints create() {
        RTCMediaConstraints constraints = new RTCMediaConstraints();
        constraints.initWithConstraints(null, null);
        return constraints;
    }

    /**
     * Creates media constraints with the mandatory "IceRestart" flag set to "true".
     *
     * <p>When these constraints are used to create an SDP offer, the resulting offer
     * will include new ICE credentials, triggering an ICE restart. This is used
     * by the ICE state machine when recovering from DISCONNECTED or FAILED states.</p>
     *
     * @return a new constraints instance with ICE restart enabled
     */
    public static RTCMediaConstraints createWithIceRestart() {
        NSDictionary<NSString, NSString> mandatory = new NSDictionary<NSString, NSString>();
        mandatory.put(new NSString("IceRestart"), new NSString("true"));
        RTCMediaConstraints constraints = new RTCMediaConstraints();
        constraints.initWithConstraints(mandatory, null);
        return constraints;
    }
}
