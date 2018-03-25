package com.chat.api;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;


@SpringBootApplication
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @NoArgsConstructor
    @AllArgsConstructor
    @Data
    public class ChatMessage {
        private String from;
        private String body;
        private MessageType messageType;

    }

    public enum MessageType {
        CHAT,
        JOIN,
        LEAVE
    }

    @Controller
    public class ChatController {

        @MessageMapping("/chat.sendMessage")
        @SendTo("/topic/public")
        public ChatMessage sendMessage(@Payload ChatMessage chatMessage) {
            return chatMessage;
        }

        @MessageMapping("/chat.addUser")
        @SendTo("/topic/public")
        public ChatMessage addUser(@Payload ChatMessage chatMessage, SimpMessageHeaderAccessor headerAccessor) {
            // Add username in web socket session
            headerAccessor.getSessionAttributes().put("username", chatMessage.getFrom());
            return chatMessage;
        }

    }

    @Configuration
    @EnableWebSocketMessageBroker
    public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

        @Override
        public void registerStompEndpoints(StompEndpointRegistry registry) {
            registry.addEndpoint("/ws").withSockJS();
        }

        @Override
        public void configureMessageBroker(MessageBrokerRegistry registry) {
            registry.setApplicationDestinationPrefixes("/app");
            registry.enableSimpleBroker("/topic");
        }
    }


    @Component
    public class WebSocketEventListener {

        private final Logger logger = LoggerFactory.getLogger(WebSocketEventListener.class);

        @Autowired
        private SimpMessageSendingOperations messagingTemplate;

        @EventListener
        public void handleWebSocketConnectListener(SessionConnectedEvent event) {
            logger.info("Received a new web socket connection");
        }

        @EventListener
        public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
            StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());

            String username = (String) headerAccessor.getSessionAttributes().get("username");
            if(username != null) {
                logger.info("User Disconnected : " + username);

                ChatMessage chatMessage = new ChatMessage();
                chatMessage.setMessageType(MessageType.LEAVE);
                chatMessage.setFrom(username);

                messagingTemplate.convertAndSend("/topic/public", chatMessage);
            }
        }
    }


}

