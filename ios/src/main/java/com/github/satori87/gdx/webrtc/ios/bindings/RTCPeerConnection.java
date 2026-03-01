package com.github.satori87.gdx.webrtc.ios.bindings;

import org.robovm.apple.foundation.NSObject;
import org.robovm.objc.annotation.Block;
import org.robovm.objc.annotation.Method;
import org.robovm.objc.annotation.NativeClass;
import org.robovm.objc.annotation.Property;
import org.robovm.objc.block.VoidBlock1;
import org.robovm.objc.block.VoidBlock2;

/**
 * RoboVM Java binding for the Objective-C {@code RTCPeerConnection} class
 * from Apple's WebRTC.framework.
 *
 * <p>An RTCPeerConnection represents a WebRTC connection between the local device
 * and a remote peer. It manages ICE candidate gathering, SDP offer/answer exchange,
 * and data channel creation. Lifecycle events are delivered via the
 * {@link RTCPeerConnectionDelegate} set on this connection.</p>
 *
 * <p>Instances are created by
 * {@link RTCPeerConnectionFactory#createPeerConnection(RTCConfiguration, RTCMediaConstraints, RTCPeerConnectionDelegate)}.</p>
 *
 * @see RTCPeerConnectionFactory
 * @see RTCPeerConnectionDelegate
 * @see RTCPeerConnectionState
 */
@NativeClass
public class RTCPeerConnection extends NSObject {

    /**
     * Returns the current overall connection state.
     *
     * <p>The returned integer corresponds to one of the constants in
     * {@link RTCPeerConnectionState} (NEW, CONNECTING, CONNECTED,
     * DISCONNECTED, FAILED, CLOSED).</p>
     *
     * @return the connection state as a native enum ordinal
     * @see RTCPeerConnectionState
     */
    @Property(selector = "connectionState")
    public native int getConnectionState();

    /**
     * Returns the delegate currently receiving callbacks for this peer connection.
     *
     * @return the current delegate, or {@code null} if none is set
     */
    @Property(selector = "delegate")
    public native RTCPeerConnectionDelegate getDelegate();

    /**
     * Sets the delegate that will receive ICE candidate, data channel, and
     * connection state change callbacks for this peer connection.
     *
     * @param delegate the delegate to receive callbacks
     * @see RTCPeerConnectionDelegate
     */
    @Property(selector = "setDelegate:")
    public native void setDelegate(RTCPeerConnectionDelegate delegate);

    /**
     * Creates a new data channel on this peer connection with the given label
     * and configuration.
     *
     * <p>gdx-webrtc creates two channels per peer: "reliable" (ordered, unlimited
     * retransmits) and "unreliable" (unordered, zero retransmits).</p>
     *
     * @param label  the channel label (e.g. "reliable" or "unreliable")
     * @param config the data channel configuration specifying ordering and
     *               retransmit behavior
     * @return the newly created data channel
     * @see RTCDataChannelConfiguration
     */
    @Method(selector = "dataChannelForLabel:configuration:")
    public native RTCDataChannel createDataChannel(String label, RTCDataChannelConfiguration config);

    /**
     * Creates an SDP offer asynchronously.
     *
     * <p>The completion handler receives the generated {@link RTCSessionDescription}
     * on success, or an error object on failure. The SDP offer should be set as the
     * local description before being sent to the remote peer via the signaling channel.</p>
     *
     * @param constraints media constraints for the offer (typically created via
     *                    {@link RTCMediaConstraints#create()})
     * @param handler     completion handler receiving (sessionDescription, error)
     */
    @Method(selector = "offerForConstraints:completionHandler:")
    public native void createOffer(RTCMediaConstraints constraints,
                                   @Block VoidBlock2<RTCSessionDescription, NSObject> handler);

    /**
     * Creates an SDP answer asynchronously in response to a received offer.
     *
     * <p>The completion handler receives the generated {@link RTCSessionDescription}
     * on success, or an error object on failure. The remote description (the offer)
     * must be set before calling this method.</p>
     *
     * @param constraints media constraints for the answer (typically created via
     *                    {@link RTCMediaConstraints#create()})
     * @param handler     completion handler receiving (sessionDescription, error)
     */
    @Method(selector = "answerForConstraints:completionHandler:")
    public native void createAnswer(RTCMediaConstraints constraints,
                                    @Block VoidBlock2<RTCSessionDescription, NSObject> handler);

    /**
     * Sets the local SDP description on this peer connection.
     *
     * <p>Called after creating an offer or answer. The completion handler receives
     * an error object on failure, or {@code null} on success.</p>
     *
     * @param sdp     the session description to set as the local description
     * @param handler completion handler receiving an error (or {@code null} on success)
     */
    @Method(selector = "setLocalDescription:completionHandler:")
    public native void setLocalDescription(RTCSessionDescription sdp,
                                           @Block VoidBlock1<NSObject> handler);

    /**
     * Sets the remote SDP description on this peer connection.
     *
     * <p>Called when an offer or answer is received from the remote peer via the
     * signaling channel. The completion handler receives an error object on failure,
     * or {@code null} on success.</p>
     *
     * @param sdp     the session description received from the remote peer
     * @param handler completion handler receiving an error (or {@code null} on success)
     */
    @Method(selector = "setRemoteDescription:completionHandler:")
    public native void setRemoteDescription(RTCSessionDescription sdp,
                                            @Block VoidBlock1<NSObject> handler);

    /**
     * Adds a remote ICE candidate to this peer connection.
     *
     * <p>Called when an ICE candidate is received from the remote peer via the
     * signaling channel. The completion handler receives an error object on failure,
     * or {@code null} on success.</p>
     *
     * @param candidate the ICE candidate received from the remote peer
     * @param handler   completion handler receiving an error (or {@code null} on success)
     */
    @Method(selector = "addIceCandidate:completionHandler:")
    public native void addIceCandidate(RTCIceCandidate candidate,
                                       @Block VoidBlock1<NSObject> handler);

    /**
     * Triggers an ICE restart on this peer connection.
     *
     * <p>Called when the connection enters a DISCONNECTED or FAILED state and
     * the ICE state machine decides to attempt recovery. The next offer created
     * after this call will include new ICE credentials.</p>
     */
    @Method(selector = "restartIce")
    public native void restartIce();

    /**
     * Closes this peer connection and releases all associated native resources.
     *
     * <p>After calling close, the connection state transitions to CLOSED and
     * no further events will be delivered to the delegate.</p>
     */
    @Method(selector = "close")
    public native void close();
}
