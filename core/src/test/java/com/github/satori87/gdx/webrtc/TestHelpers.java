package com.github.satori87.gdx.webrtc;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared mock implementations of strategy interfaces for unit tests.
 */
class TestHelpers {

    /** Mock PeerConnectionProvider that records calls. */
    static class MockPeerConnectionProvider implements PeerConnectionProvider {
        boolean initialized;
        boolean initializeResult = true;
        Object lastCreatedPc = new Object();
        List<String> calls = new ArrayList<String>();
        SdpResultCallback lastOfferCallback;
        SdpResultCallback lastHandleOfferCallback;
        String lastRemoteAnswerSdp;
        String lastIceCandidateJson;
        DataChannelEventHandler lastDcHandler;
        PeerEventHandler lastPcHandler;
        long bufferedAmount = 0;
        boolean channelOpen = true;
        Object reliableChannel = new Object();
        Object unreliableChannel = new Object();
        List<byte[]> sentData = new ArrayList<byte[]>();

        public boolean initialize() {
            calls.add("initialize");
            initialized = true;
            return initializeResult;
        }

        public Object createPeerConnection(WebRTCConfiguration config, PeerEventHandler handler) {
            calls.add("createPeerConnection");
            lastPcHandler = handler;
            return lastCreatedPc;
        }

        public void createOffer(Object pc, SdpResultCallback callback) {
            calls.add("createOffer");
            lastOfferCallback = callback;
        }

        public void handleOffer(Object pc, String remoteSdp, SdpResultCallback callback) {
            calls.add("handleOffer");
            lastHandleOfferCallback = callback;
        }

        public void setRemoteAnswer(Object pc, String sdp) {
            calls.add("setRemoteAnswer");
            lastRemoteAnswerSdp = sdp;
        }

        public void addIceCandidate(Object pc, String candidateJson) {
            calls.add("addIceCandidate");
            lastIceCandidateJson = candidateJson;
        }

        public void restartIce(Object pc) {
            calls.add("restartIce");
        }

        public void closePeerConnection(Object pc) {
            calls.add("closePeerConnection");
        }

        public ChannelPair createDataChannels(Object pc, int maxRetransmits,
                                               DataChannelEventHandler handler) {
            calls.add("createDataChannels");
            lastDcHandler = handler;
            return new ChannelPair(reliableChannel, unreliableChannel);
        }

        public void setupReceivedChannel(Object channel, boolean reliable,
                                          DataChannelEventHandler handler) {
            calls.add("setupReceivedChannel:" + (reliable ? "reliable" : "unreliable"));
            lastDcHandler = handler;
        }

        public void sendData(Object channel, byte[] data) {
            calls.add("sendData");
            sentData.add(data);
        }

        public long getBufferedAmount(Object channel) {
            return bufferedAmount;
        }

        public boolean isChannelOpen(Object channel) {
            return channelOpen;
        }
    }

    /** Mock SignalingProvider that records calls. */
    static class MockSignalingProvider implements SignalingProvider {
        SignalingEventHandler handler;
        boolean open;
        List<SignalMessage> sentMessages = new ArrayList<SignalMessage>();
        List<String> calls = new ArrayList<String>();

        public void connect(String url, SignalingEventHandler handler) {
            calls.add("connect");
            this.handler = handler;
            this.open = true;
        }

        public void send(SignalMessage msg) {
            calls.add("send");
            sentMessages.add(msg);
        }

        public void close() {
            calls.add("close");
            open = false;
        }

        public boolean isOpen() {
            return open;
        }

        /** Simulate receiving a signaling message. */
        void simulateMessage(SignalMessage msg) {
            handler.onMessage(msg);
        }
    }

    /** Mock Scheduler that captures scheduled tasks for manual execution. */
    static class MockScheduler implements Scheduler {
        List<ScheduledTask> tasks = new ArrayList<ScheduledTask>();
        List<String> calls = new ArrayList<String>();
        int nextId = 1;

        public Object schedule(Runnable task, long delayMs) {
            calls.add("schedule:" + delayMs);
            ScheduledTask st = new ScheduledTask(nextId++, task, delayMs);
            tasks.add(st);
            return Integer.valueOf(st.id);
        }

        public void cancel(Object handle) {
            calls.add("cancel");
            int id = ((Integer) handle).intValue();
            for (int i = 0; i < tasks.size(); i++) {
                if (tasks.get(i).id == id) {
                    tasks.get(i).cancelled = true;
                }
            }
        }

        public void shutdown() {
            calls.add("shutdown");
        }

        /** Run the last scheduled task (if not cancelled). */
        void runLast() {
            if (!tasks.isEmpty()) {
                ScheduledTask last = tasks.get(tasks.size() - 1);
                if (!last.cancelled) {
                    last.task.run();
                }
            }
        }

        /** Run all non-cancelled tasks. */
        void runAll() {
            for (int i = 0; i < tasks.size(); i++) {
                ScheduledTask t = tasks.get(i);
                if (!t.cancelled) {
                    t.task.run();
                }
            }
        }
    }

    static class ScheduledTask {
        final int id;
        final Runnable task;
        final long delayMs;
        boolean cancelled;

        ScheduledTask(int id, Runnable task, long delayMs) {
            this.id = id;
            this.task = task;
            this.delayMs = delayMs;
        }
    }

    /** Mock WebRTCClientListener that records calls. */
    static class MockListener implements WebRTCClientListener {
        int signalingConnectedId = -1;
        List<WebRTCPeer> connectedPeers = new ArrayList<WebRTCPeer>();
        List<WebRTCPeer> disconnectedPeers = new ArrayList<WebRTCPeer>();
        List<Integer> joinedPeers = new ArrayList<Integer>();
        List<Integer> leftPeers = new ArrayList<Integer>();
        List<String> errors = new ArrayList<String>();
        List<byte[]> receivedData = new ArrayList<byte[]>();
        List<Boolean> receivedReliable = new ArrayList<Boolean>();

        public void onSignalingConnected(int localId) {
            signalingConnectedId = localId;
        }

        public void onConnected(WebRTCPeer peer) {
            connectedPeers.add(peer);
        }

        public void onDisconnected(WebRTCPeer peer) {
            disconnectedPeers.add(peer);
        }

        public void onMessage(WebRTCPeer peer, byte[] data, boolean reliable) {
            receivedData.add(data);
            receivedReliable.add(Boolean.valueOf(reliable));
        }

        public void onPeerJoined(int peerId) {
            joinedPeers.add(Integer.valueOf(peerId));
        }

        public void onPeerLeft(int peerId) {
            leftPeers.add(Integer.valueOf(peerId));
        }

        public void onError(String error) {
            errors.add(error);
        }
    }

    /** Create a BaseWebRTCClient with mocks. */
    static BaseWebRTCClient createClient(MockPeerConnectionProvider pc,
                                          MockSignalingProvider sig,
                                          MockScheduler sched,
                                          MockListener listener) {
        WebRTCConfiguration config = new WebRTCConfiguration();
        config.signalingServerUrl = "ws://test:9090";
        return new BaseWebRTCClient("[Test] ", config, listener, pc, sig, sched);
    }

    /** Create a client and connect it (triggers signaling connect). */
    static BaseWebRTCClient createConnectedClient(MockPeerConnectionProvider pc,
                                                   MockSignalingProvider sig,
                                                   MockScheduler sched,
                                                   MockListener listener) {
        BaseWebRTCClient client = createClient(pc, sig, sched, listener);
        client.connect();
        // Simulate WELCOME to assign localId
        sig.simulateMessage(new SignalMessage(SignalMessage.TYPE_WELCOME, 0, 0, "42"));
        return client;
    }
}
