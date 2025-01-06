package com.example.webrtcsfu;

import com.google.gson.JsonObject;
import lombok.Getter;
import org.kurento.client.EventListener;
import org.kurento.client.IceCandidate;
import org.kurento.client.IceCandidateFoundEvent;
import org.kurento.client.WebRtcEndpoint;
import org.kurento.jsonrpc.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 한 명의 유저 세션을 나타내는 클래스
 * 유저의 WebRTC 연결과 미디어 스트림을 관리
 */
@Getter
public class UserSession {
    private static final Logger log = LoggerFactory.getLogger(UserSession.class);

    private final MessageSender messageSender;  // MessageSender 추가

    // 유저 이름
    private final String name;
    // WebSocket 세션
    private final WebSocketSession session;
    // 참여중인 방 이름
    private final String roomName;
    // 유저가 송신하는 미디어를 위한 WebRTC 엔드포인트
    private final WebRtcEndpoint outgoingMedia;
    // 다른 참가자들로부터 수신하는 미디어를 위한 WebRTC 엔드포인트들
    // 키: 송신자의 이름, 값: WebRTC 엔드포인트
    private final ConcurrentHashMap<String, WebRtcEndpoint> incomingMedia = new ConcurrentHashMap<>();

    public UserSession(String name, WebSocketSession session, String roomName,
                       WebRtcEndpoint outgoingMedia, MessageSender messageSender) {
        this.name = name;
        this.session = session;
        this.roomName = roomName;
        this.outgoingMedia = outgoingMedia;
        this.messageSender = messageSender;

        this.outgoingMedia.addIceCandidateFoundListener(new EventListener<IceCandidateFoundEvent>() {
            @Override
            public void onEvent(IceCandidateFoundEvent event) {
                try {
                    messageSender.sendIceCandidate(session, name, event.getCandidate());
                } catch (IOException e) {
                    log.error("Error sending ICE candidate: {}", e.getMessage());
                }
            }
        });
    }


}