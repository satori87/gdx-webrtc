package com.github.satori87.gdx.webrtc.ios.bindings;

import org.robovm.apple.foundation.NSDictionary;
import org.robovm.apple.foundation.NSObject;
import org.robovm.apple.foundation.NSString;
import org.robovm.objc.annotation.Method;
import org.robovm.objc.annotation.NativeClass;

/** Binding for WebRTC's RTCMediaConstraints. */
@NativeClass
public class RTCMediaConstraints extends NSObject {

    @Method(selector = "initWithMandatoryConstraints:optionalConstraints:")
    protected native long initWithConstraints(
            NSDictionary<NSString, NSString> mandatory,
            NSDictionary<NSString, NSString> optional);

    public static RTCMediaConstraints create() {
        RTCMediaConstraints constraints = new RTCMediaConstraints();
        constraints.initWithConstraints(null, null);
        return constraints;
    }

    public static RTCMediaConstraints createWithIceRestart() {
        NSDictionary<NSString, NSString> mandatory = new NSDictionary<NSString, NSString>();
        mandatory.put(new NSString("IceRestart"), new NSString("true"));
        RTCMediaConstraints constraints = new RTCMediaConstraints();
        constraints.initWithConstraints(mandatory, null);
        return constraints;
    }
}
