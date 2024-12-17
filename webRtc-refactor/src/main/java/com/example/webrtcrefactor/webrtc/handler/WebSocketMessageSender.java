package com.example.webrtcrefactor.webrtc.handler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;

@Component
@Slf4j
public class WebSocketMessageSender {
    private final Object lock = new Object();

    public void sendMessage(WebSocketSession session, String message) {
        try {
            synchronized (lock) {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(message));
                    // 약간의 지연을 주어 메시지 전송이 겹치지 않도록 함
                    Thread.sleep(10);
                }
            }
        } catch (IOException | InterruptedException e) {
            log.error("메시지 전송 중 오류 발생: {}", e.getMessage());
        }
    }
}