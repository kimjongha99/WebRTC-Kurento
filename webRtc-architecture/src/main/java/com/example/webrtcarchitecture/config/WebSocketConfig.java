package com.example.webrtcarchitecture.config;

import com.example.webrtcarchitecture.controller.WebRTCWebSocketHandler;
import com.example.webrtcarchitecture.service.WebRTCService;
import org.kurento.client.KurentoClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Autowired
    private WebRTCWebSocketHandler webRTCHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(webRTCHandler, "/webrtc")
                .setAllowedOrigins("*");
    }
}