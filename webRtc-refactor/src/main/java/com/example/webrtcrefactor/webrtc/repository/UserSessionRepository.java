package com.example.webrtcrefactor.webrtc.repository;

import com.example.webrtcrefactor.webrtc.domain.UserSession;
import org.springframework.stereotype.Repository;

import java.util.concurrent.ConcurrentHashMap;

@Repository
public class UserSessionRepository {
    private final ConcurrentHashMap<String, UserSession> sessions = new ConcurrentHashMap<>();

    public void save(String sessionId, UserSession session) {
        sessions.put(sessionId, session);
    }

    public UserSession find(String sessionId) {
        return sessions.get(sessionId);
    }

    public UserSession remove(String sessionId) {
        return sessions.remove(sessionId);
    }

    public boolean exists(String sessionId) {
        return sessions.containsKey(sessionId);
    }
}
