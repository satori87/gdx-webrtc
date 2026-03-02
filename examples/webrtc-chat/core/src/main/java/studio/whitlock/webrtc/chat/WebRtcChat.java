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

    private final ChatSignalClient signalClient;
    private Stage stage;
    private Skin skin;
    private ConnectionManager connectionManager;
    private EmbeddedChatServer embeddedServer;
    private boolean hosting;

    private enum AppState { CONNECT, CONNECTING, CHATTING }
    private AppState state = AppState.CONNECT;

    private Label statusLabel;
    private Label messageLog;
    private ScrollPane scrollPane;
    private TextField inputField;
    private StringBuilder messageBuffer = new StringBuilder();

    public WebRtcChat(ChatSignalClient signalClient) {
        this.signalClient = signalClient;
    }

    @Override
    public void create() {
        stage = new Stage(new ScreenViewport());
        Gdx.input.setInputProcessor(stage);
        skin = new Skin(Gdx.files.internal("uiskin.json"));
        buildConnectUI();
    }

    private void buildConnectUI() {
        stage.clear();
        state = AppState.CONNECT;
        hosting = false;

        Table root = new Table();
        root.setFillParent(true);
        root.center();
        stage.addActor(root);

        Label title = new Label("WebRTC Chat (Client/Server)", skin);
        title.setFontScale(2f);
        root.add(title).padBottom(40).row();

        Label addressLabel = new Label("Signal server address:", skin);
        root.add(addressLabel).padBottom(10).row();

        Table addressRow = new Table();
        final TextField ipField = new TextField("localhost", skin);
        ipField.setMessageText("Signal server IP");
        addressRow.add(ipField).width(250).height(40);
        root.add(addressRow).padBottom(20).row();

        Table buttonRow = new Table();
        TextButton hostButton = new TextButton("Host Server", skin);
        hostButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                String ip = ipField.getText().trim();
                if (ip.isEmpty()) ip = "localhost";
                startHosting(ip);
            }
        });
        TextButton connectButton = new TextButton("Connect", skin);
        connectButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                String ip = ipField.getText().trim();
                if (ip.isEmpty()) ip = "localhost";
                startConnection(ip);
            }
        });
        buttonRow.add(hostButton).width(150).height(50).padRight(10);
        buttonRow.add(connectButton).width(150).height(50);
        root.add(buttonRow).row();
    }

    private void startHosting(String ip) {
        hosting = true;
        embeddedServer = new EmbeddedChatServer(signalClient, new EmbeddedChatServer.HostCallback() {
            public void onStarted() {
                buildChatUI();
            }

            public void onMessageReceived(String message) {
                if (state == AppState.CHATTING) {
                    appendMessage(message);
                }
            }

            public void onError(String error) {
                if (statusLabel != null) {
                    statusLabel.setText("Error: " + error);
                    statusLabel.setColor(Color.RED);
                }
            }
        });
        embeddedServer.start("ws://" + ip + ":9090");
        buildConnectingUI("Connecting to signal server " + ip + ":9090...");
    }

    private void startConnection(String ip) {
        connectionManager = new ConnectionManager(signalClient, this);
        connectionManager.connect("ws://" + ip + ":9090");
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
                if (hosting && embeddedServer != null) {
                    embeddedServer.stop();
                    embeddedServer = null;
                }
                if (connectionManager != null) {
                    connectionManager.disconnect();
                    connectionManager = null;
                }
                buildConnectUI();
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

        if (hosting) {
            statusLabel = new Label("Hosting — waiting for clients", skin);
        } else {
            statusLabel = new Label("Connected to server", skin);
        }
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

        TextButton disconnectButton = new TextButton(hosting ? "Stop" : "Disconnect", skin);
        disconnectButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (hosting && embeddedServer != null) {
                    embeddedServer.stop();
                    embeddedServer = null;
                }
                if (connectionManager != null) {
                    connectionManager.disconnect();
                    connectionManager = null;
                }
                buildConnectUI();
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
        if (text.isEmpty()) return;

        if (hosting) {
            if (embeddedServer != null && embeddedServer.isRunning()) {
                embeddedServer.broadcastChat(text);
            }
        } else {
            if (connectionManager != null && connectionManager.isConnected()) {
                connectionManager.sendMessage(text);
                appendMessage("You: " + text);
            }
        }

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
        if (embeddedServer != null) {
            embeddedServer.stop();
        }
        if (connectionManager != null) {
            connectionManager.disconnect();
        }
        stage.dispose();
        skin.dispose();
    }

    // --- ConnectionManager.ChatCallback (client mode) ---

    @Override
    public void onConnected() {
        buildChatUI();
    }

    @Override
    public void onDisconnected() {
        if (state == AppState.CHATTING) {
            appendMessage("-- Disconnected from server --");
            statusLabel.setText("Disconnected");
            statusLabel.setColor(Color.RED);
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
