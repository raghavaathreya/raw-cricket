package com.handcricket.config;

import com.handcricket.handler.GameHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {

        // Pure WebSocket — works on localhost
        registry.addHandler(new GameHandler(), "/game")
                .setAllowedOrigins("*");

        // SockJS fallback — works through Railway's reverse proxy
        registry.addHandler(new GameHandler(), "/game-sockjs")
                .setAllowedOrigins("*")
                .withSockJS();
    }
}