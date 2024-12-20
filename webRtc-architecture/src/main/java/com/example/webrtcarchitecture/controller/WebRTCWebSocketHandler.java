package com.example.webrtcarchitecture.controller;


import com.example.webrtcarchitecture.dto.WebRTCMessage;
import com.example.webrtcarchitecture.service.WebRTCService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.kurento.client.IceCandidate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;


@Slf4j
@Component
public class WebRTCWebSocketHandler extends TextWebSocketHandler {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private WebRTCService webRTCService;

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            WebRTCMessage webRTCMessage = objectMapper.readValue(message.getPayload(), WebRTCMessage.class);

            switch (webRTCMessage.getId()) {
                case "PROCESS_SDP_OFFER":
                    handleSdpOffer(session, webRTCMessage);
                    break;
                case "ADD_ICE_CANDIDATE":
                    handleIceCandidate(session, webRTCMessage);
                    break;
                case "STOP":
                    webRTCService.stopSession(session.getId());
                    break;
                default:
                    log.warn("Invalid message Id: {}", webRTCMessage.getId());
            }
        } catch (Exception e) {
            log.error("Error processing message", e);
        }
    }

    private void handleSdpOffer(WebSocketSession session, WebRTCMessage message) {
        String sdpAnswer = webRTCService.handleSdpOffer(session.getId(), message.getSdpOffer());

        WebRTCMessage response = WebRTCMessage.builder()
                .id("PROCESS_SDP_ANSWER")
                .sdpAnswer(sdpAnswer)
                .build();

        sendMessage(session, response);
    }

    private void handleIceCandidate(WebSocketSession session, WebRTCMessage message) {
        if (message.getCandidate() != null) {
            IceCandidate candidate = new IceCandidate(
                    message.getCandidate().get("candidate").asText(),
                    message.getCandidate().get("sdpMid").asText(),
                    message.getCandidate().get("sdpMLineIndex").asInt()
            );
            webRTCService.handleIceCandidate(session.getId(), candidate);
        }
    }

    private void sendMessage(WebSocketSession session, WebRTCMessage message) {
        try {
            String jsonMessage = objectMapper.writeValueAsString(message);
            session.sendMessage(new TextMessage(jsonMessage));
        } catch (IOException e) {
            log.error("Error sending message", e);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        webRTCService.stopSession(session.getId());
    }
}