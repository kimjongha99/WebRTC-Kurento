package com.example.webrtcarchitecture.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.kurento.client.MediaPipeline;
import org.kurento.client.WebRtcEndpoint;

@Getter
@Setter
@AllArgsConstructor
public class WebRTCSession {
    private String sessionId;
    private MediaPipeline mediaPipeline;
    private WebRtcEndpoint webRtcEndpoint;
}