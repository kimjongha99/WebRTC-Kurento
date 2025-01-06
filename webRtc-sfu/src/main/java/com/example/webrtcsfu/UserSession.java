package com.example.webrtcsfu;

import lombok.Getter;
import org.kurento.client.WebRtcEndpoint;
import org.springframework.web.socket.WebSocketSession;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 한 명의 유저 세션을 나타내는 클래스
 * 유저의 WebRTC 연결과 미디어 스트림을 관리
 */
@Getter
public class UserSession {
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

    public UserSession(String name, WebSocketSession session, String roomName, WebRtcEndpoint outgoingMedia) {
        this.name = name;
        this.session = session;
        this.roomName = roomName;
        this.outgoingMedia = outgoingMedia;
    }



}