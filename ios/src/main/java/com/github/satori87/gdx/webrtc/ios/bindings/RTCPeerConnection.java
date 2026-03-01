package com.github.satori87.gdx.webrtc.ios.bindings;

import org.robovm.apple.foundation.NSObject;
import org.robovm.objc.annotation.Block;
import org.robovm.objc.annotation.Method;
import org.robovm.objc.annotation.NativeClass;
import org.robovm.objc.annotation.Property;
import org.robovm.objc.block.VoidBlock1;
import org.robovm.objc.block.VoidBlock2;

/** Binding for WebRTC's RTCPeerConnection. */
@NativeClass
public class RTCPeerConnection extends NSObject {

    @Property(selector = "connectionState")
    public native int getConnectionState();

    @Property(selector = "delegate")
    public native RTCPeerConnectionDelegate getDelegate();

    @Property(selector = "setDelegate:")
    public native void setDelegate(RTCPeerConnectionDelegate delegate);

    @Method(selector = "dataChannelForLabel:configuration:")
    public native RTCDataChannel createDataChannel(String label, RTCDataChannelConfiguration config);

    @Method(selector = "offerForConstraints:completionHandler:")
    public native void createOffer(RTCMediaConstraints constraints,
                                   @Block VoidBlock2<RTCSessionDescription, NSObject> handler);

    @Method(selector = "answerForConstraints:completionHandler:")
    public native void createAnswer(RTCMediaConstraints constraints,
                                    @Block VoidBlock2<RTCSessionDescription, NSObject> handler);

    @Method(selector = "setLocalDescription:completionHandler:")
    public native void setLocalDescription(RTCSessionDescription sdp,
                                           @Block VoidBlock1<NSObject> handler);

    @Method(selector = "setRemoteDescription:completionHandler:")
    public native void setRemoteDescription(RTCSessionDescription sdp,
                                            @Block VoidBlock1<NSObject> handler);

    @Method(selector = "addIceCandidate:completionHandler:")
    public native void addIceCandidate(RTCIceCandidate candidate,
                                       @Block VoidBlock1<NSObject> handler);

    @Method(selector = "restartIce")
    public native void restartIce();

    @Method(selector = "close")
    public native void close();
}
