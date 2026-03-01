package com.github.satori87.gdx.webrtc.ios.bindings;

import org.robovm.apple.foundation.NSArray;
import org.robovm.apple.foundation.NSObject;
import org.robovm.apple.foundation.NSString;
import org.robovm.objc.annotation.Method;
import org.robovm.objc.annotation.NativeClass;

/** Binding for WebRTC's RTCIceServer. */
@NativeClass
public class RTCIceServer extends NSObject {

    @Method(selector = "initWithURLStrings:")
    protected native long initWithURLStrings(NSArray<NSString> urlStrings);

    @Method(selector = "initWithURLStrings:username:credential:")
    protected native long initWithCredentials(NSArray<NSString> urlStrings, String username, String credential);

    public static RTCIceServer create(String url) {
        RTCIceServer server = new RTCIceServer();
        NSArray<NSString> urls = new NSArray<NSString>(new NSString(url));
        server.initWithURLStrings(urls);
        return server;
    }

    public static RTCIceServer create(String url, String username, String credential) {
        RTCIceServer server = new RTCIceServer();
        NSArray<NSString> urls = new NSArray<NSString>(new NSString(url));
        server.initWithCredentials(urls, username, credential);
        return server;
    }
}
