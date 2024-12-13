package com.example.webrtcrefactor.webrtc.repository;

import com.example.webrtcrefactor.webrtc.entity.UserSession;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class UserSessionRepository {
    private final ConcurrentHashMap<String, UserSession> sessions = new ConcurrentHashMap<>();

    public void save(String sessionId, UserSession session) {
        sessions.put(sessionId, session);
    }

    public Optional<UserSession> findById(String sessionId) {
        return Optional.ofNullable(sessions.get(sessionId));
    }

    public void deleteById(String sessionId) {
        sessions.remove(sessionId);
    }
}