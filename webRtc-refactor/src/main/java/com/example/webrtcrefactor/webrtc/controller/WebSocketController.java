package com.example.webrtcrefactor.webrtc.controller;

import com.example.webrtcrefactor.webrtc.dto.WebRtcMessage;
import com.example.webrtcrefactor.webrtc.service.WebRtcService;
import com.google.gson.Gson;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;


@Component
@RequiredArgsConstructor
public class WebSocketController extends TextWebSocketHandler {
    private static final Logger log = LoggerFactory.getLogger(WebSocketController.class);
    private static final Gson gson = new Gson();

    private final WebRtcService webRtcService;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("새로운 WebSocket 연결 생성됨, 세션ID: {}", session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        if (!status.equalsCode(CloseStatus.NORMAL)) {
            log.warn("비정상적인 연결 종료, 상태: {}, 세션ID: {}", status, session.getId());
        }
        webRtcService.stop(session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        final String sessionId = session.getId();
        WebRtcMessage webRtcMessage = gson.fromJson(message.getPayload(), WebRtcMessage.class);

        log.info("클라이언트에서 메시지 수신됨: {}, 세션ID: {}", webRtcMessage.getId(), sessionId);

        try {
            switch (webRtcMessage.getId()) {
                case "PROCESS_SDP_OFFER":
                    webRtcService.processSdpOffer(session, webRtcMessage);
                    break;
                case "ADD_ICE_CANDIDATE":
                    webRtcService.addIceCandidate(session, webRtcMessage);
                    break;
                case "STOP":
                    webRtcService.stop(session);
                    break;
                default:
                    log.warn("잘못된 메시지 ID: {}", webRtcMessage.getId());
            }
        } catch (Exception e) {
            log.error("메시지 처리 중 오류 발생: {}, 세션ID: {}", e, sessionId);
            webRtcService.sendError(session, e.getMessage());
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("전송 오류 발생, 세션ID: {}, 오류: {}", session.getId(), exception.getMessage());
        webRtcService.stop(session);
    }
}