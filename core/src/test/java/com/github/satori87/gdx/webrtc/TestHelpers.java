package com.github.satori87.gdx.webrtc;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared mock implementations of strategy interfaces for unit tests.
 *
 * <p>Public so that tests in the {@code transport} subpackage can reuse
 * these mocks.</p>
 */
public class TestHelpers {

    /** Mock PeerConnectionProvider that records calls. */
    public static class MockPeerConnectionProvider implements PeerConnectionProvider {
        public boolean initialized;
        public boolean initializeResult = true;
        public Object lastCreatedPc = new Object();
        public List<String> calls = new ArrayList<String>();
        public SdpResultCallback lastOfferCallback;
        public SdpResultCallback lastHandleOfferCallback;
        public String lastRemoteAnswerSdp;
        public String lastIceCandidateJson;
        public DataChannelEventHandler lastDcHandler;
        public PeerEventHandler lastPcHandler;
        public long bufferedAmount = 0;
        public boolean channelOpen = true;
        public Object reliableChannel = new Object();
        public Object unreliableChannel = new Object();
        public List<byte[]> sentData = new ArrayList<byte[]>();

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
    public static class MockSignalingProvider implements SignalingProvider {
        public SignalingEventHandler handler;
        public boolean open;
        public List<SignalMessage> sentMessages = new ArrayList<SignalMessage>();
        public List<String> calls = new ArrayList<String>();

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
        public void simulateMessage(SignalMessage msg) {
            handler.onMessage(msg);
        }
    }

    /** Mock Scheduler that captures scheduled tasks for manual execution. */
    public static class MockScheduler implements Scheduler {
        public List<ScheduledTask> tasks = new ArrayList<ScheduledTask>();
        public List<String> calls = new ArrayList<String>();
        public int nextId = 1;

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
        public void runLast() {
            if (!tasks.isEmpty()) {
                ScheduledTask last = tasks.get(tasks.size() - 1);
                if (!last.cancelled) {
                    last.task.run();
                }
            }
        }

        /** Run all non-cancelled tasks. */
        public void runAll() {
            for (int i = 0; i < tasks.size(); i++) {
                ScheduledTask t = tasks.get(i);
                if (!t.cancelled) {
                    t.task.run();
                }
            }
        }
    }

    public static class ScheduledTask {
        public final int id;
        public final Runnable task;
        public final long delayMs;
        public boolean cancelled;

        ScheduledTask(int id, Runnable task, long delayMs) {
            this.id = id;
            this.task = task;
            this.delayMs = delayMs;
        }
    }

    /** Mock WebRTCClientListener that records calls. */
    public static class MockListener implements WebRTCClientListener {
        public int signalingConnectedId = -1;
        public List<WebRTCPeer> connectedPeers = new ArrayList<WebRTCPeer>();
        public List<WebRTCPeer> disconnectedPeers = new ArrayList<WebRTCPeer>();
        public List<Integer> joinedPeers = new ArrayList<Integer>();
        public List<Integer> leftPeers = new ArrayList<Integer>();
        public List<String> errors = new ArrayList<String>();
        public List<byte[]> receivedData = new ArrayList<byte[]>();
        public List<Boolean> receivedReliable = new ArrayList<Boolean>();

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
        config.signalingKeepaliveMs = 0; // Disable keepalive in tests
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
