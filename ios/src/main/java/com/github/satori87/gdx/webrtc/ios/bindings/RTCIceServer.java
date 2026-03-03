package com.github.satori87.gdx.webrtc.ios.bindings;

import org.robovm.apple.foundation.NSArray;
import org.robovm.apple.foundation.NSObject;
import org.robovm.apple.foundation.NSString;
import org.robovm.objc.annotation.Method;
import org.robovm.objc.annotation.NativeClass;

/**
 * RoboVM Java binding for the Objective-C {@code RTCIceServer} class
 * from Apple's WebRTC.framework.
 *
 * <p>An RTCIceServer represents a STUN or TURN server used during ICE candidate
 * gathering to discover network paths between peers. STUN servers are used
 * to discover the public address of the device, while TURN servers provide
 * relay candidates when direct connectivity is not possible.</p>
 *
 * <p>ICE servers are added to an {@link RTCConfiguration} before creating
 * a peer connection.</p>
 *
 * @see RTCConfiguration#setIceServers(NSArray)
 */
@NativeClass
public class RTCIceServer extends NSObject {

    /**
     * Initializes this ICE server with the given URL strings (no authentication).
     *
     * <p>This is the native Objective-C initializer. Use
     * {@link #create(String)} instead for convenience.</p>
     *
     * @param urlStrings an array of URL strings (e.g. "stun:stun.l.google.com:19302")
     * @return the native object pointer
     */
    @Method(selector = "initWithURLStrings:")
    protected native long initWithURLStrings(NSArray<NSString> urlStrings);

    /**
     * Initializes this ICE server with the given URL strings and TURN credentials.
     *
     * <p>This is the native Objective-C initializer. Use
     * {@link #create(String, String, String)} instead for convenience.</p>
     *
     * @param urlStrings an array of URL strings (e.g. "turn:your-server:3478")
     * @param username   the TURN username for authentication
     * @param credential the TURN password/credential for authentication
     * @return the native object pointer
     */
    @Method(selector = "initWithURLStrings:username:credential:")
    protected native long initWithCredentials(NSArray<NSString> urlStrings, String username, String credential);

    /**
     * Creates a STUN ICE server with the given URL (no authentication).
     *
     * @param url the STUN server URL (e.g. "stun:stun.l.google.com:19302")
     * @return a new ICE server instance
     */
    public static RTCIceServer create(String url) {
        RTCIceServer server = new RTCIceServer();
        NSArray<NSString> urls = new NSArray<NSString>(new NSString(url));
        server.initWithURLStrings(urls);
        return server;
    }

    /**
     * Creates a STUN ICE server with multiple URLs (no authentication).
     *
     * @param urls the STUN server URLs
     * @return a new ICE server instance
     */
    public static RTCIceServer create(String[] urls) {
        RTCIceServer server = new RTCIceServer();
        NSString[] nsStrings = new NSString[urls.length];
        for (int i = 0; i < urls.length; i++) {
            nsStrings[i] = new NSString(urls[i]);
        }
        server.initWithURLStrings(new NSArray<NSString>(nsStrings));
        return server;
    }

    /**
     * Creates a TURN ICE server with the given URL and authentication credentials.
     *
     * @param url        the TURN server URL (e.g. "turn:your-server:3478")
     * @param username   the TURN username for authentication
     * @param credential the TURN password/credential for authentication
     * @return a new ICE server instance with credentials
     */
    public static RTCIceServer create(String url, String username, String credential) {
        RTCIceServer server = new RTCIceServer();
        NSArray<NSString> urls = new NSArray<NSString>(new NSString(url));
        server.initWithCredentials(urls, username, credential);
        return server;
    }
}
