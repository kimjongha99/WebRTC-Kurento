package com.example.webrtcsimplify;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import org.kurento.client.IceCandidate;
import org.kurento.client.KurentoClient;
import org.kurento.client.MediaPipeline;
import org.kurento.client.WebRtcEndpoint;
import org.kurento.jsonrpc.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

public class WebRtcHandler extends TextWebSocketHandler {
    private static final Logger log = LoggerFactory.getLogger(WebRtcHandler.class);
    private static final Gson gson = new GsonBuilder().create();
    private final ConcurrentHashMap<String, UserSession> users = new ConcurrentHashMap<>();

    @Autowired
    private KurentoClient kurento;

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonObject jsonMessage = gson.fromJson(message.getPayload(), JsonObject.class);
        String messageId = jsonMessage.get("id").getAsString();

        log.info("Received message: {} from session: {}", messageId, session.getId());

        switch (messageId) {
            case "PROCESS_SDP_OFFER":
                handleProcessSdpOffer(session, jsonMessage);
                break;
            case "ADD_ICE_CANDIDATE":
                handleAddIceCandidate(session, jsonMessage);
                break;
            case "STOP":
                stop(session);
                break;
            default:
                log.warn("Invalid message Id: {}", messageId);
                break;
        }
    }

    private void handleProcessSdpOffer(WebSocketSession session, JsonObject jsonMessage) {
        UserSession user = new UserSession();
        users.put(session.getId(), user);

        MediaPipeline pipeline = kurento.createMediaPipeline();
        user.setMediaPipeline(pipeline);

        WebRtcEndpoint webRtcEp = new WebRtcEndpoint.Builder(pipeline).build();
        user.setWebRtcEndpoint(webRtcEp);
        webRtcEp.connect(webRtcEp);

        // ICE candidate 이벤트 처리
        webRtcEp.addIceCandidateFoundListener(event -> {
            JsonObject response = new JsonObject();
            response.addProperty("id", "ADD_ICE_CANDIDATE");
            response.add("candidate", JsonUtils.toJsonObject(event.getCandidate()));
            sendMessage(session, response.toString());
        });
        // SDP 처리
        String sdpOffer = jsonMessage.get("sdpOffer").getAsString();
        String sdpAnswer = webRtcEp.processOffer(sdpOffer);

        JsonObject response = new JsonObject();
        response.addProperty("id", "PROCESS_SDP_ANSWER");
        response.addProperty("sdpAnswer", sdpAnswer);
        sendMessage(session, response.toString());

        webRtcEp.gatherCandidates();
    }

    private void handleAddIceCandidate(WebSocketSession session, JsonObject jsonMessage) {
        UserSession user = users.get(session.getId());
        if (user != null) {
            JsonObject candidateJson = jsonMessage.get("candidate").getAsJsonObject();
            IceCandidate candidate = new IceCandidate(
                    candidateJson.get("candidate").getAsString(),
                    candidateJson.get("sdpMid").getAsString(),
                    candidateJson.get("sdpMLineIndex").getAsInt());
            user.getWebRtcEndpoint().addIceCandidate(candidate);
        }
    }

    private void stop(WebSocketSession session) {
        UserSession user = users.remove(session.getId());
        if (user != null && user.getMediaPipeline() != null) {
            user.getMediaPipeline().release();
        }
    }

    private synchronized void sendMessage(WebSocketSession session, String message) {
        try {
            session.sendMessage(new TextMessage(message));
        } catch (IOException e) {
            log.error("Error sending message", e);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        stop(session);
    }
}
