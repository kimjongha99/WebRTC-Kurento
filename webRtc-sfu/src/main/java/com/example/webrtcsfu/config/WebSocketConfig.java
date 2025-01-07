package com.example.webrtcsfu.config;

import com.example.webrtcsfu.CallHandler;
import com.example.webrtcsfu.MessageSender;
import com.example.webrtcsfu.RoomRegister;
import com.example.webrtcsfu.UserRegister;
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
    public CallHandler callHandler() {
        return new CallHandler();
    }
    @Bean
    public UserRegister userRegister() {
        return new UserRegister();
    }
    @Bean
    public RoomRegister roomRegister() {
        return new RoomRegister();
    }

    @Bean
    public MessageSender messageSender() {
        return new MessageSender();
    }

    @Bean
    public ServletServerContainerFactoryBean createServletServerContainerFactoryBean() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(32768);
        return container;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(callHandler(), "/webrtc")
                .setAllowedOriginPatterns("*");    // 모든 origin 패턴 허용

    }
}