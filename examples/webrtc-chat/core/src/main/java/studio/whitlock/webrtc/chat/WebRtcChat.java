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
import com.github.satori87.gdx.webrtc.WebRTCClients;
import com.github.satori87.gdx.webrtc.WebRTCConfiguration;
import com.github.satori87.gdx.webrtc.WebRTCGameClient;
import com.github.satori87.gdx.webrtc.WebRTCGameClientListener;
import com.github.satori87.gdx.webrtc.WebRTCServer;
import com.github.satori87.gdx.webrtc.WebRTCServerListener;

public class WebRtcChat extends ApplicationAdapter {

    private Stage stage;
    private Skin skin;
    private WebRTCServer server;
    private WebRTCGameClient gameClient;
    private boolean hosting;

    private enum AppState { CONNECT, CONNECTING, CHATTING }
    private AppState state = AppState.CONNECT;

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

        Label title = new Label("WebRTC Chat", skin);
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
        TextButton hostButton = new TextButton("Host", skin);
        hostButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                String ip = ipField.getText().trim();
                if (ip.isEmpty()) ip = "localhost";
                startAsServer(ip);
            }
        });
        TextButton connectButton = new TextButton("Connect", skin);
        connectButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                String ip = ipField.getText().trim();
                if (ip.isEmpty()) ip = "localhost";
                startAsClient(ip);
            }
        });
        buttonRow.add(hostButton).width(150).height(50).padRight(10);
        buttonRow.add(connectButton).width(150).height(50);
        root.add(buttonRow).row();
    }

    private void startAsServer(String ip) {
        hosting = true;
        WebRTCConfiguration config = new WebRTCConfiguration();
        config.signalingServerUrl = "ws://" + ip + ":9090";

        server = WebRTCClients.newServer(config, new WebRTCServerListener() {
            public void onStarted(final int serverId) {
                Gdx.app.postRunnable(new Runnable() {
                    public void run() {
                        buildChatUI();
                    }
                });
            }

            public void onClientConnected(final int clientId) {
                Gdx.app.postRunnable(new Runnable() {
                    public void run() {
                        appendMessage("-- Client " + clientId + " connected --");
                    }
                });
            }

            public void onClientDisconnected(final int clientId) {
                Gdx.app.postRunnable(new Runnable() {
                    public void run() {
                        appendMessage("-- Client " + clientId + " disconnected --");
                    }
                });
            }

            public void onClientMessage(final int clientId, final byte[] data, boolean reliable) {
                Gdx.app.postRunnable(new Runnable() {
                    public void run() {
                        String text = new String(data);
                        appendMessage("Client " + clientId + ": " + text);
                        // Relay to all other clients
                        byte[] relayMsg = ("Client " + clientId + ": " + text).getBytes();
                        server.broadcastReliableExcept(clientId, relayMsg);
                    }
                });
            }

            public void onError(final String error) {
                Gdx.app.postRunnable(new Runnable() {
                    public void run() {
                        if (statusLabel != null) {
                            statusLabel.setText("Error: " + error);
                            statusLabel.setColor(Color.RED);
                        }
                    }
                });
            }
        });

        server.start();
        buildConnectingUI("Starting server on " + ip + ":9090...");
    }

    private void startAsClient(String ip) {
        hosting = false;
        WebRTCConfiguration config = new WebRTCConfiguration();
        config.signalingServerUrl = "ws://" + ip + ":9090";

        gameClient = WebRTCClients.newGameClient(config, new WebRTCGameClientListener() {
            public void onConnected() {
                Gdx.app.postRunnable(new Runnable() {
                    public void run() {
                        buildChatUI();
                        appendMessage("-- Connected to server --");
                    }
                });
            }

            public void onDisconnected() {
                Gdx.app.postRunnable(new Runnable() {
                    public void run() {
                        appendMessage("-- Disconnected from server --");
                    }
                });
            }

            public void onMessage(final byte[] data, boolean reliable) {
                Gdx.app.postRunnable(new Runnable() {
                    public void run() {
                        String text = new String(data);
                        appendMessage(text);
                    }
                });
            }

            public void onError(final String error) {
                Gdx.app.postRunnable(new Runnable() {
                    public void run() {
                        if (statusLabel != null) {
                            statusLabel.setText("Error: " + error);
                            statusLabel.setColor(Color.RED);
                        }
                    }
                });
            }
        });

        gameClient.connect();
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
                disconnectAndReset();
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

        String label;
        if (hosting) {
            label = "Hosting (ID: " + server.getServerId() + ")";
        } else {
            label = "Connected to server";
        }
        statusLabel = new Label(label, skin);
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
                disconnectAndReset();
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
            // Server broadcasts "Host: text" to all clients
            byte[] broadcastMsg = ("Host: " + text).getBytes();
            server.broadcastReliable(broadcastMsg);
            appendMessage("You (Host): " + text);
        } else {
            // Client sends raw text to server
            byte[] data = text.getBytes();
            gameClient.sendReliable(data);
            appendMessage("You: " + text);
        }

        inputField.setText("");
        stage.setKeyboardFocus(inputField);
    }

    private void appendMessage(String message) {
        if (messageBuffer.length() > 0) {
            messageBuffer.append("\n");
        }
        messageBuffer.append(message);
        if (messageLog != null) {
            messageLog.setText(messageBuffer.toString());
            scrollPane.layout();
            scrollPane.setScrollPercentY(1f);
        }
    }

    private void disconnectAndReset() {
        if (server != null) {
            server.stop();
            server = null;
        }
        if (gameClient != null) {
            gameClient.disconnect();
            gameClient = null;
        }
        buildConnectUI();
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
        if (server != null) {
            server.stop();
        }
        if (gameClient != null) {
            gameClient.disconnect();
        }
        stage.dispose();
        skin.dispose();
    }
}
