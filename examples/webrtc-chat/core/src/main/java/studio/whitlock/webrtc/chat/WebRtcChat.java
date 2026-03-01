package studio.whitlock.webrtc.chat;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.ScreenViewport;

public class WebRtcChat extends ApplicationAdapter implements ConnectionManager.ChatCallback {

    private Stage stage;
    private Skin skin;
    private ConnectionManager connectionManager;

    private enum AppState { ROLE_SELECT, CONNECTING, CHATTING }
    private AppState state = AppState.ROLE_SELECT;

    private int localId = -1;

    // Chat UI references
    private Label statusLabel;
    private Label messageLog;
    private ScrollPane scrollPane;
    private TextField inputField;
    private StringBuilder messageBuffer = new StringBuilder();

    @Override
    public void create() {
        stage = new Stage(new ScreenViewport());
        Gdx.input.setInputProcessor(stage);
        skin = new Skin(Gdx.files.internal("uiskin.json"));
        buildRoleSelectUI();
    }

    private void buildRoleSelectUI() {
        stage.clear();
        state = AppState.ROLE_SELECT;

        Table root = new Table();
        root.setFillParent(true);
        root.center();
        stage.addActor(root);

        Label title = new Label("WebRTC Chat", skin);
        title.setFontScale(2f);
        root.add(title).padBottom(40).row();

        TextButton hostButton = new TextButton("Host (localhost:9090)", skin);
        hostButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                startConnection("localhost");
            }
        });
        root.add(hostButton).width(300).height(50).padBottom(20).row();

        Label connectLabel = new Label("-- or connect to a host --", skin);
        connectLabel.setColor(Color.LIGHT_GRAY);
        root.add(connectLabel).padBottom(10).row();

        Table connectRow = new Table();
        TextField ipField = new TextField("localhost", skin);
        ipField.setMessageText("Host IP address");
        TextButton connectButton = new TextButton("Connect", skin);
        connectButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                String ip = ipField.getText().trim();
                if (ip.isEmpty()) ip = "localhost";
                startConnection(ip);
            }
        });
        connectRow.add(ipField).width(200).height(40).padRight(10);
        connectRow.add(connectButton).width(100).height(40);
        root.add(connectRow).row();
    }

    private void startConnection(String ip) {
        localId = -1;
        connectionManager = new ConnectionManager("ws://" + ip + ":9090", this);
        connectionManager.connect();
        buildConnectingUI("Connecting to " + ip + ":9090...");
    }

    private void buildConnectingUI(String message) {
        stage.clear();
        state = AppState.CONNECTING;

        Table root = new Table();
        root.setFillParent(true);
        root.center();
        stage.addActor(root);

        statusLabel = new Label(message, skin);
        root.add(statusLabel).padBottom(20).row();

        TextButton backButton = new TextButton("Back", skin);
        backButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (connectionManager != null) {
                    connectionManager.disconnect();
                    connectionManager = null;
                }
                buildRoleSelectUI();
            }
        });
        root.add(backButton).width(100).height(40);
    }

    private void buildChatUI() {
        stage.clear();
        state = AppState.CHATTING;
        messageBuffer.setLength(0);

        Table root = new Table();
        root.setFillParent(true);
        root.pad(10);
        stage.addActor(root);

        statusLabel = new Label("Connected (You are peer " + localId + ")", skin);
        statusLabel.setColor(Color.GREEN);
        root.add(statusLabel).expandX().fillX().padBottom(5).row();

        messageLog = new Label("", skin);
        messageLog.setWrap(true);
        messageLog.setColor(Color.WHITE);

        scrollPane = new ScrollPane(messageLog, skin);
        scrollPane.setFadeScrollBars(false);
        scrollPane.setScrollingDisabled(true, false);
        root.add(scrollPane).expand().fill().padBottom(5).row();

        Table inputRow = new Table();
        inputField = new TextField("", skin);
        inputField.setMessageText("Type a message...");
        inputField.addListener(new InputListener() {
            @Override
            public boolean keyDown(InputEvent event, int keycode) {
                if (keycode == Input.Keys.ENTER) {
                    sendCurrentMessage();
                    return true;
                }
                return false;
            }
        });

        TextButton sendButton = new TextButton("Send", skin);
        sendButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                sendCurrentMessage();
            }
        });

        TextButton disconnectButton = new TextButton("Disconnect", skin);
        disconnectButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (connectionManager != null) {
                    connectionManager.disconnect();
                    connectionManager = null;
                }
                buildRoleSelectUI();
            }
        });

        inputRow.add(inputField).expandX().fillX().height(40).padRight(5);
        inputRow.add(sendButton).width(80).height(40).padRight(5);
        inputRow.add(disconnectButton).width(100).height(40);
        root.add(inputRow).expandX().fillX();

        stage.setKeyboardFocus(inputField);
    }

    private void sendCurrentMessage() {
        String text = inputField.getText().trim();
        if (text.isEmpty() || connectionManager == null || !connectionManager.isConnected()) return;
        connectionManager.sendMessage(text);
        appendMessage("You: " + text);
        inputField.setText("");
        stage.setKeyboardFocus(inputField);
    }

    private void appendMessage(String message) {
        if (messageBuffer.length() > 0) {
            messageBuffer.append("\n");
        }
        messageBuffer.append(message);
        messageLog.setText(messageBuffer.toString());
        scrollPane.layout();
        scrollPane.setScrollPercentY(1f);
    }

    @Override
    public void render() {
        ScreenUtils.clear(0.15f, 0.15f, 0.2f, 1f);
        stage.act(Gdx.graphics.getDeltaTime());
        stage.draw();
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    @Override
    public void dispose() {
        if (connectionManager != null) {
            connectionManager.disconnect();
        }
        stage.dispose();
        skin.dispose();
    }

    // --- ConnectionManager.ChatCallback ---

    @Override
    public void onSignalingConnected(int localId) {
        this.localId = localId;
        if (state == AppState.CONNECTING && statusLabel != null) {
            statusLabel.setText("Connected to server (ID: " + localId + "). Waiting for peers...");
        }
    }

    @Override
    public void onPeerJoined(int peerId) {
        // Lower ID initiates connection to avoid duplicate handshakes
        if (localId < peerId) {
            connectionManager.connectToPeer(peerId);
        }
    }

    @Override
    public void onPeerLeft(int peerId) {
    }

    @Override
    public void onPeerConnected(int peerId) {
        if (state == AppState.CHATTING) {
            appendMessage("-- Peer " + peerId + " joined --");
        } else {
            buildChatUI();
        }
    }

    @Override
    public void onPeerDisconnected(int peerId) {
        if (state == AppState.CHATTING) {
            appendMessage("-- Peer " + peerId + " left --");
            if (!connectionManager.isConnected()) {
                statusLabel.setText("All peers disconnected");
                statusLabel.setColor(Color.RED);
            }
        }
    }

    @Override
    public void onMessageReceived(String message) {
        if (state == AppState.CHATTING) {
            appendMessage(message);
        }
    }

    @Override
    public void onError(String error) {
        if (statusLabel != null) {
            statusLabel.setText("Error: " + error);
            statusLabel.setColor(Color.RED);
        }
    }
}
