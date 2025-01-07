package com.example.webrtcsfu;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.WebSocketSession;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 모든 유저를 관리하는 클래스
 * WebSocket 세션 ID로 유저를 찾을 수 있게 함
 */
public class UserRegister {
    private static final Logger log = LoggerFactory.getLogger(UserRegister.class);

    // WebSocket 세션 ID를 키로 하여 유저 세션을 저장하는 맵
    private final ConcurrentHashMap<String, UserSession> usersBySessionId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, UserSession> usersByName = new ConcurrentHashMap<>();

    /**
     * 세션 ID로 사용자 조회
     */
    public UserSession getBySession(WebSocketSession session) {
        return usersBySessionId.get(session.getId());
    }

    /**
     * 새로운 사용자 등록
     */
    public void register(UserSession user) {
        usersByName.put(user.getName(), user);
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
     * @param
     */
    public UserSession removeBySession(WebSocketSession session) {
        UserSession user = getBySession(session);
        usersByName.remove(user.getName());
        usersBySessionId.remove(session.getId());
        return user;
    }


    public UserSession getByName(String senderName) {
        return usersByName.get(senderName);
    }


}
