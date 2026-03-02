package com.github.satori87.gdx.webrtc.transport;

import com.github.satori87.gdx.webrtc.TestHelpers;
import com.github.satori87.gdx.webrtc.WebRTCConfiguration;

import java.util.ArrayList;
import java.util.List;

/**
 * Mock listener implementations for transport unit tests.
 */
public class TransportTestHelpers {

    /** Mock TransportListener that records all callback invocations. */
    public static class MockTransportListener implements TransportListener {
        public int connectedCount;
        public int disconnectedCount;
        public List<byte[]> receivedData = new ArrayList<byte[]>();
        public List<Boolean> receivedReliable = new ArrayList<Boolean>();
        public List<String> errors = new ArrayList<String>();

        public void onConnected() {
            connectedCount++;
        }

        public void onDisconnected() {
            disconnectedCount++;
        }

        public void onMessage(byte[] data, boolean reliable) {
            receivedData.add(data);
            receivedReliable.add(Boolean.valueOf(reliable));
        }

        public void onError(String message) {
            errors.add(message);
        }
    }

    /** Mock ServerTransportListener that records all callback invocations. */
    public static class MockServerTransportListener implements ServerTransportListener {
        public List<Integer> connectedIds = new ArrayList<Integer>();
        public List<Integer> disconnectedIds = new ArrayList<Integer>();
        public List<Integer> messageConnIds = new ArrayList<Integer>();
        public List<byte[]> receivedData = new ArrayList<byte[]>();
        public List<Boolean> receivedReliable = new ArrayList<Boolean>();

        public void onClientConnected(int connId) {
            connectedIds.add(Integer.valueOf(connId));
        }

        public void onClientDisconnected(int connId) {
            disconnectedIds.add(Integer.valueOf(connId));
        }

        public void onClientMessage(int connId, byte[] data, boolean reliable) {
            messageConnIds.add(Integer.valueOf(connId));
            receivedData.add(data);
            receivedReliable.add(Boolean.valueOf(reliable));
        }
    }

    /** Mock SignalCallback for WebRTCClientTransport. */
    public static class MockClientSignalCallback implements WebRTCClientTransport.SignalCallback {
        public List<String> answers = new ArrayList<String>();
        public List<String> iceCandidates = new ArrayList<String>();

        public void onAnswer(String sdpAnswer) {
            answers.add(sdpAnswer);
        }

        public void onIceCandidate(String iceJson) {
            iceCandidates.add(iceJson);
        }
    }

    /** Mock SignalCallback for WebRTCServerTransport. */
    public static class MockServerSignalCallback implements WebRTCServerTransport.SignalCallback {
        public List<Integer> offerConnIds = new ArrayList<Integer>();
        public List<String> offers = new ArrayList<String>();
        public List<Integer> iceConnIds = new ArrayList<Integer>();
        public List<String> iceCandidates = new ArrayList<String>();

        public void onOffer(int connId, String sdpOffer) {
            offerConnIds.add(Integer.valueOf(connId));
            offers.add(sdpOffer);
        }

        public void onIceCandidate(int connId, String iceJson) {
            iceConnIds.add(Integer.valueOf(connId));
            iceCandidates.add(iceJson);
        }
    }

    /** Creates a BaseWebRTCClientTransport with mocks. */
    public static BaseWebRTCClientTransport createClientTransport(
            TestHelpers.MockPeerConnectionProvider pc,
            TestHelpers.MockScheduler sched,
            MockTransportListener listener) {
        WebRTCConfiguration config = new WebRTCConfiguration();
        BaseWebRTCClientTransport transport = new BaseWebRTCClientTransport(
                "[Test] ", config, pc, sched);
        transport.setListener(listener);
        return transport;
    }

    /** Creates a BaseWebRTCServerTransport with mocks. */
    public static BaseWebRTCServerTransport createServerTransport(
            TestHelpers.MockPeerConnectionProvider pc,
            TestHelpers.MockScheduler sched,
            MockServerTransportListener listener) {
        WebRTCConfiguration config = new WebRTCConfiguration();
        BaseWebRTCServerTransport transport = new BaseWebRTCServerTransport(
                "[Test] ", config, pc, sched);
        transport.setListener(listener);
        return transport;
    }
}
