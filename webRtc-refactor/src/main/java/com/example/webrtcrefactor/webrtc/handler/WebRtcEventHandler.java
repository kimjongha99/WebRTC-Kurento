package com.example.webrtcrefactor.webrtc.handler;


import com.google.gson.JsonObject;
import lombok.RequiredArgsConstructor;
import org.kurento.client.BaseRtpEndpoint;
import org.kurento.client.WebRtcEndpoint;
import org.kurento.jsonrpc.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class WebRtcEventHandler {
    private static final Logger log = LoggerFactory.getLogger(WebRtcEventHandler.class);
    private final WebSocketMessageSender messageSender;

    public void initializeEventListeners(final WebSocketSession session, final WebRtcEndpoint webRtcEp) {
        initBaseEventListeners(session, webRtcEp);
        initWebRtcEventListeners(session, webRtcEp);
    }

    private void initBaseEventListeners(final WebSocketSession session, BaseRtpEndpoint baseRtpEp) {
        String className = baseRtpEp.getClass().getSimpleName();
        log.info("기본 이벤트 리스너 초기화 - 이름: {}, 클래스: {}, 세션ID: {}",
                baseRtpEp.getName(), className, session.getId());

        // 에러 이벤트 리스너
        baseRtpEp.addErrorListener(ev -> {
            log.error("에러 발생 - 코드: {}, 타입: {}, 소스: {}, 설명: {}",
                    className, ev.getErrorCode(), ev.getType(), ev.getSource().getName(),
                    ev.getTimestampMillis(), ev.getTags(), ev.getDescription());

            sendErrorMessage(session, "[Kurento] " + ev.getDescription());
        });

        // 미디어 입력 상태 변경 이벤트
        baseRtpEp.addMediaFlowInStateChangedListener(ev ->
                log.info("미디어 입력 상태 변경 - 상태: {}, 미디어타입: {}", ev.getState(), ev.getMediaType())
        );

        // 미디어 출력 상태 변경 이벤트
        baseRtpEp.addMediaFlowOutStateChangedListener(ev ->
                log.info("미디어 출력 상태 변경 - 상태: {}, 미디어타입: {}", ev.getState(), ev.getMediaType())
        );

        // 연결 상태 변경 이벤트
        baseRtpEp.addConnectionStateChangedListener(ev ->
                log.info("연결 상태 변경 - 이전상태: {}, 새상태: {}", ev.getOldState(), ev.getNewState())
        );

        // 미디어 상태 변경 이벤트
        baseRtpEp.addMediaStateChangedListener(ev ->
                log.info("미디어 상태 변경 - 이전상태: {}, 새상태: {}", ev.getOldState(), ev.getNewState())
        );

        // 미디어 트랜스코딩 상태 변경 이벤트
        baseRtpEp.addMediaTranscodingStateChangedListener(ev ->
                log.info("트랜스코딩 상태 변경 - 상태: {}, 미디어타입: {}", ev.getState(), ev.getMediaType())
        );
    }

    private void initWebRtcEventListeners(final WebSocketSession session, final WebRtcEndpoint webRtcEp) {
        log.info("WebRTC 이벤트 리스너 초기화 - 이름: {}, 세션ID: {}",
                webRtcEp.getName(), session.getId());

        // ICE 후보 발견 이벤트
        webRtcEp.addIceCandidateFoundListener(ev -> {
            log.debug("ICE 후보 발견: {}", JsonUtils.toJson(ev.getCandidate()));

            JsonObject message = new JsonObject();
            message.addProperty("id", "ADD_ICE_CANDIDATE");
            message.add("candidate", JsonUtils.toJsonObject(ev.getCandidate()));
            sendMessage(session, message.toString());
        });

        // ICE 상태 변경 이벤트
        webRtcEp.addIceComponentStateChangedListener(ev ->
                log.debug("ICE 상태 변경 - 스트림ID: {}, 상태: {}", ev.getStreamId(), ev.getState())
        );

        // ICE 후보 수집 완료 이벤트
        webRtcEp.addIceGatheringDoneListener(ev ->
                log.info("ICE 후보 수집 완료")
        );

        // 새로운 ICE 후보 쌍 선택 이벤트
        webRtcEp.addNewCandidatePairSelectedListener(ev ->
                log.info("새로운 ICE 후보 쌍 선택 - 로컬: {}, 리모트: {}",
                        ev.getCandidatePair().getLocalCandidate(),
                        ev.getCandidatePair().getRemoteCandidate())
        );
    }

    private void sendMessage(WebSocketSession session, String message) {
        messageSender.sendMessage(session, message);
    }

    private void sendErrorMessage(WebSocketSession session, String errorMessage) {
        JsonObject message = new JsonObject();
        message.addProperty("id", "ERROR");
        message.addProperty("message", errorMessage);
        sendMessage(session, message.toString());
    }
}