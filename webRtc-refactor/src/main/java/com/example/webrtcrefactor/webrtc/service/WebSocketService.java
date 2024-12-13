package com.example.webrtcrefactor.webrtc.service;

// WebSocketService.java

import com.example.webrtcrefactor.webrtc.dto.response.IceCandidateResponse;
import com.google.gson.Gson;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

@Service
@RequiredArgsConstructor
public class WebSocketService {
    private final Gson gson;

    public void sendIceCandidate(String sessionId, IceCandidateResponse candidate) {
        // WebSocket 세션 관리 및 메시지 전송 로직
    }
}
