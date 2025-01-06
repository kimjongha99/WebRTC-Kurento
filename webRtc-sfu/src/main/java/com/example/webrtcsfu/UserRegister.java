package com.example.webrtcsfu;

import org.springframework.web.socket.WebSocketSession;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 모든 유저를 관리하는 클래스
 * WebSocket 세션 ID로 유저를 찾을 수 있게 함
 */
public class UserRegister {
    // WebSocket 세션 ID를 키로 하여 유저 세션을 저장하는 맵
    private final ConcurrentHashMap<String, UserSession> usersBySessionId = new ConcurrentHashMap<>();

    /**
     * 세션 ID로 사용자 조회
     */
    public UserSession getBySession(WebSocketSession sessionId) {
        return usersBySessionId.get(sessionId);
    }

    /**
     * 새로운 사용자 등록
     */
    public void register(UserSession user) {
        usersBySessionId.put(user.getSession().getId(), user);
    }


    /**
     * 사용자 존재 여부 확인 (세션 ID 기준)
     */
    public boolean exists(String sessionId) {
        return usersBySessionId.containsKey(sessionId);
    }

    /**
     * 사용자 세션 삭제
     *
     * @param sessionId
     */
    public void removeBySession(String sessionId) {
        usersBySessionId.remove(sessionId);
    }
}
