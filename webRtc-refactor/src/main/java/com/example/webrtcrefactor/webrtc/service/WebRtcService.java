package com.example.webrtcrefactor.webrtc.service;

import com.example.webrtcrefactor.webrtc.domain.UserSession;
import com.example.webrtcrefactor.webrtc.dto.WebRtcMessage;
import com.example.webrtcrefactor.webrtc.handler.WebRtcEventHandler;
import com.example.webrtcrefactor.webrtc.handler.WebSocketMessageSender;
import com.example.webrtcrefactor.webrtc.repository.UserSessionRepository;
import com.google.gson.JsonObject;
import lombok.RequiredArgsConstructor;
import org.kurento.client.KurentoClient;
import org.kurento.client.MediaPipeline;
import org.kurento.client.WebRtcEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;


@Service
@RequiredArgsConstructor
public class WebRtcService {
    private static final Logger log = LoggerFactory.getLogger(WebRtcService.class);
    private final WebSocketMessageSender messageSender;

    private final KurentoClient kurentoClient;
    private final UserSessionRepository userSessionRepository;
    private final WebRtcEventHandler webRtcEventHandler;

    public void processSdpOffer(WebSocketSession session, WebRtcMessage message) {
        UserSession userSession = createUserSession(session);
        MediaPipeline pipeline = kurentoClient.createMediaPipeline();
        WebRtcEndpoint webRtcEndpoint = createWebRtcEndpoint(pipeline);

        userSession.setMediaPipeline(pipeline);
        userSession.setWebRtcEndpoint(webRtcEndpoint);
        userSessionRepository.save(session.getId(), userSession);

        webRtcEventHandler.initializeEventListeners(session, webRtcEndpoint);
        String sdpAnswer = webRtcEndpoint.processOffer(message.getSdpOffer());
        webRtcEndpoint.gatherCandidates();

        sendSdpAnswer(session, sdpAnswer);
    }

    private UserSession createUserSession(WebSocketSession session) {
        log.info("새로운 사용자 세션 생성, 세션ID: {}", session.getId());
        return new UserSession();
    }

    private WebRtcEndpoint createWebRtcEndpoint(MediaPipeline pipeline) {
        WebRtcEndpoint webRtcEp = new WebRtcEndpoint.Builder(pipeline).build();
        webRtcEp.connect(webRtcEp);
        return webRtcEp;
    }

    private void sendSdpAnswer(WebSocketSession session, String sdpAnswer) {
        JsonObject message = new JsonObject();
        message.addProperty("id", "PROCESS_SDP_ANSWER");
        message.addProperty("sdpAnswer", sdpAnswer);
        sendMessage(session, message.toString());
    }

    public void sendError(WebSocketSession session, String errorMessage) {
        JsonObject message = new JsonObject();
        message.addProperty("id", "ERROR");
        message.addProperty("message", errorMessage);
        sendMessage(session, message.toString());
    }

    private void sendMessage(WebSocketSession session, String message) {
        messageSender.sendMessage(session, message);
    }

    public void addIceCandidate(WebSocketSession session, WebRtcMessage message) {
        UserSession userSession = userSessionRepository.find(session.getId());
        if (userSession != null) {
            userSession.getWebRtcEndpoint().addIceCandidate(message.getCandidate());
        }
    }

    public void stop(WebSocketSession session) {
        UserSession userSession = userSessionRepository.remove(session.getId());
        if (userSession != null && userSession.getMediaPipeline() != null) {
            userSession.getMediaPipeline().release();
        }
    }
}