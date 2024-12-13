//package com.example.webrtcrefactor;
//
//
//import com.google.gson.Gson;
//import com.google.gson.JsonObject;
//
//import java.io.IOException;
//import java.util.concurrent.ConcurrentHashMap;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.web.socket.CloseStatus;
//import org.springframework.web.socket.TextMessage;
//import org.springframework.web.socket.WebSocketSession;
//import org.springframework.web.socket.handler.TextWebSocketHandler;
//import org.kurento.client.*;
//import org.kurento.jsonrpc.JsonUtils;
//
//public class HelloWorldHandler extends TextWebSocketHandler {
//    private static final Logger log = LoggerFactory.getLogger(HelloWorldHandler.class);
//    private static final Gson gson = new Gson();
//    private final ConcurrentHashMap<String, UserSession> users = new ConcurrentHashMap<>();
//
//    @Autowired
//    private KurentoClient kurento;
//
//    @Override
//    public void afterConnectionEstablished(WebSocketSession session) {
//        log.info("새 WebSocket 연결: {}", session.getId());
//    }
//
//    @Override
//    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
//        JsonObject jsonMessage = gson.fromJson(message.getPayload(), JsonObject.class);
//        String sessionId = session.getId();
//        log.info("메시지 수신 - type: {}, sessionId: {}", jsonMessage.get("id"), sessionId);
//        try {
//            switch (jsonMessage.get("id").getAsString()) {
//                case "PROCESS_SDP_OFFER":
//                    handleProcessSdpOffer(session, jsonMessage);
//                    break;
//                case "ADD_ICE_CANDIDATE":
//                    handleAddIceCandidate(session, jsonMessage);
//                    break;
//                case "STOP":
//                    stop(session);
//                    break;
//                default:
//                    break;
//            }
//        } catch (Exception e) {
//            log.error("에러 발생: {}", e.getMessage());
//            stop(session);
//        }
//    }
//
//
//    private void handleProcessSdpOffer(WebSocketSession session, JsonObject jsonMessage) {
//        UserSession user = new UserSession();
//        MediaPipeline pipeline = kurento.createMediaPipeline();
//        WebRtcEndpoint webRtcEp = new WebRtcEndpoint.Builder(pipeline).build();
//
//        user.setMediaPipeline(pipeline);
//        user.setWebRtcEndpoint(webRtcEp);
//        users.put(session.getId(), user);
//
//        webRtcEp.connect(webRtcEp);
//
//        // ICE 후보 처리를 위한 이벤트 리스너
//        webRtcEp.addIceCandidateFoundListener(event -> {
//            JsonObject response = new JsonObject();
//            response.addProperty("id", "ADD_ICE_CANDIDATE");
//            response.add("candidate", JsonUtils.toJsonObject(event.getCandidate()));
//            sendMessage(session, response.toString());
//        });
//
//        // SDP 협상
//        String sdpOffer = jsonMessage.get("sdpOffer").getAsString();
//        String sdpAnswer = webRtcEp.processOffer(sdpOffer);
//
//        JsonObject response = new JsonObject();
//        response.addProperty("id", "PROCESS_SDP_ANSWER");
//        response.addProperty("sdpAnswer", sdpAnswer);
//        sendMessage(session, response.toString());
//
//        webRtcEp.gatherCandidates();
//    }
//
//    private void handleAddIceCandidate(WebSocketSession session, JsonObject jsonMessage) {
//        UserSession user = users.get(session.getId());
//        if (user != null) {
//            JsonObject candidateJson = jsonMessage.get("candidate").getAsJsonObject();
//            IceCandidate candidate = new IceCandidate(
//                    candidateJson.get("candidate").getAsString(),
//                    candidateJson.get("sdpMid").getAsString(),
//                    candidateJson.get("sdpMLineIndex").getAsInt());
//            user.getWebRtcEndpoint().addIceCandidate(candidate);
//        }
//    }
//
//    private void stop(WebSocketSession session) {
//        UserSession user = users.remove(session.getId());
//        if (user != null && user.getMediaPipeline() != null) {
//            user.getMediaPipeline().release();
//        }
//    }
//
//
//    private synchronized void sendMessage(final WebSocketSession session, String message) {
//        try {
//            if (session.isOpen()) {
//                synchronized (session) {
//                    session.sendMessage(new TextMessage(message));
//                }
//            }
//        } catch (IOException e) {
//            log.error("메시지 전송 실패: {}", e.getMessage());
//        }
//    }
//
//    @Override
//    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
//        stop(session);
//    }
//}