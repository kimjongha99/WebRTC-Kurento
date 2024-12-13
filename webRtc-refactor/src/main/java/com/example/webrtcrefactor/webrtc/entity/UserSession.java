package com.example.webrtcrefactor.webrtc.entity;

import lombok.Getter;
import lombok.Setter;
import org.kurento.client.MediaPipeline;
import org.kurento.client.WebRtcEndpoint;

@Getter
@Setter
public class UserSession {
    private MediaPipeline mediaPipeline;
    private WebRtcEndpoint webRtcEndpoint;
}
