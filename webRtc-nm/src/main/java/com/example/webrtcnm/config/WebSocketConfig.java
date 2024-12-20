package com.example.webrtcnm.config;

import com.example.webrtcnm.RoomManager;
import com.example.webrtcnm.UserRegistry;
import com.example.webrtcnm.WebRtcHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;
@EnableWebSocket
@Configuration
public class WebSocketConfig implements WebSocketConfigurer {

    @Bean
    public WebRtcHandler webRtcHandler() {
        return new WebRtcHandler();
    }
    @Bean
    public UserRegistry registry() {
        return new UserRegistry();
    }

    @Bean
    public RoomManager roomManager() {
        return new RoomManager();
    }
    @Bean
    public ServletServerContainerFactoryBean createServletServerContainerFactoryBean() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(32768);
        return container;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(webRtcHandler(), "/webrtc");
    }
}