package com.example.webrtcarchitecture.repository;

import com.example.webrtcarchitecture.domain.WebRTCSession;
import org.springframework.stereotype.Repository;

import java.util.concurrent.ConcurrentHashMap;

@Repository
public class WebRTCSessionRepository {
    private final ConcurrentHashMap<String, WebRTCSession> sessions = new ConcurrentHashMap<>();

    public void save(String sessionId, WebRTCSession session) {
        sessions.put(sessionId, session);
    }

    public WebRTCSession findById(String sessionId) {
        return sessions.get(sessionId);
    }

    public void deleteById(String sessionId) {
        sessions.remove(sessionId);
    }
}