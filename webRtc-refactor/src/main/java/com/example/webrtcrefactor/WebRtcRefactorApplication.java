package com.example.webrtcrefactor;

import org.kurento.client.KurentoClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;


@EnableWebSocket   // 이 어노테이션 추가
@SpringBootApplication
public class WebRtcRefactorApplication  implements WebSocketConfigurer {

	@Bean
	public HelloWorldHandler handler() {
		return new HelloWorldHandler();
	}

	@Bean
	public KurentoClient kurentoClient() {
		return KurentoClient.create();
	}

	@Bean
	public ServletServerContainerFactoryBean createServletServerContainerFactoryBean() {
		ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
		container.setMaxTextMessageBufferSize(32768);
		return container;
	}

	@Override
	public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
		registry.addHandler(handler(), "/helloworld")
				.setAllowedOrigins("*");  // 개발 환경에서만 사용

	}




	public static void main(String[] args) {
		SpringApplication.run(WebRtcRefactorApplication.class, args);
	}

}
