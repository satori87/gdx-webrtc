package studio.whitlock.webrtc.chat.android;

import android.os.Bundle;
import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;
import com.github.satori87.gdx.webrtc.WebRTCClients;
import com.github.satori87.gdx.webrtc.android.AndroidWebRTCFactory;
import studio.whitlock.webrtc.chat.WebRtcChat;

public class AndroidLauncher extends AndroidApplication {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AndroidApplicationConfiguration config = new AndroidApplicationConfiguration();
        config.useImmersiveMode = true;
        WebRTCClients.FACTORY = new AndroidWebRTCFactory(this);
        initialize(new WebRtcChat(new AndroidSignalClient()), config);
    }
}
