package com.mediwise.config;

import com.mediwise.common.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.List;

/**
 * WebSocket + STOMP Configuration for MediWise.
 *
 * ─────────────────────────────────────────────────────────────────────────
 * STOMP DESTINATION MAP
 * ─────────────────────────────────────────────────────────────────────────
 *
 * Android sends WebRTC signals to:
 * /app/call/initiate → STOMP broker → /user/{recipientId}/queue/call
 * /app/call/offer → STOMP broker → /user/{recipientId}/queue/call
 * /app/call/answer → STOMP broker → /user/{recipientId}/queue/call
 * /app/call/ice-candidate → STOMP broker → /user/{recipientId}/queue/call
 * /app/call/hangup → STOMP broker → /user/{recipientId}/queue/call
 *
 * Android sends in-call chat to:
 * /app/chat.send → STOMP broker → /topic/chat/{roomId}
 *
 * Android subscribes to receive signals/messages:
 * /user/queue/call (private — only this user's signals)
 * /topic/chat/{roomId} (shared — all chat messages in the room)
 *
 * ─────────────────────────────────────────────────────────────────────────
 * STUN SERVER (for WebRTC on the client side)
 * ─────────────────────────────────────────────────────────────────────────
 *
 * The backend doesn't configure STUN — the client does. For reference,
 * include this in your Android WebRTC PeerConnection config:
 *
 * val iceServers = listOf(
 * PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
 * PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
 * )
 *
 * ─────────────────────────────────────────────────────────────────────────
 * JWT AUTHENTICATION ON STOMP CONNECT
 * ─────────────────────────────────────────────────────────────────────────
 *
 * WebSocket connections bypass the standard Spring Security filter chain
 * (because the HTTP upgrade happens before Spring Security can intercept).
 *
 * SOLUTION: We add a ChannelInterceptor on the inbound STOMP channel.
 * When a STOMP CONNECT frame arrives, we extract the JWT from the
 * Authorization header, validate it, and set a Principal on the session.
 *
 * This Principal's name becomes the user ID used by:
 * convertAndSendToUser(userId, "/queue/call", message)
 *
 * ─────────────────────────────────────────────────────────────────────────
 */
@Slf4j
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtUtil jwtUtil;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Simple in-memory broker for these destination prefixes:
        // /topic → public channels (chat rooms)
        // /user → private user queues (call signals)
        config.enableSimpleBroker("/topic", "/user");

        // Client messages sent to /app/... are routed to @MessageMapping methods
        config.setApplicationDestinationPrefixes("/app");

        // /user/{userId}/queue/... maps to convertAndSendToUser(userId, "queue/...",
        // ...)
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                // SockJS provides fallback transport (long-polling) for
                // environments where raw WebSocket is blocked (e.g. corporate firewalls)
                .withSockJS();
    }

    /**
     * Intercept the STOMP CONNECT frame to authenticate the user via JWT.
     *
     * WHY HERE AND NOT IN SecurityConfig?
     * Spring Security's filter chain processes HTTP. The WebSocket upgrade
     * request IS an HTTP request and goes through the filter chain, but
     * after the handshake, subsequent STOMP frames are not HTTP — they're
     * WebSocket frames and bypass the HTTP security filter chain entirely.
     *
     * This ChannelInterceptor operates at the STOMP protocol level,
     * after the WebSocket handshake completes.
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

                // Only validate on the initial CONNECT frame — not on every message
                if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
                    String authHeader = accessor.getFirstNativeHeader("Authorization");

                    if (authHeader != null && authHeader.startsWith("Bearer ")) {
                        String token = authHeader.substring(7);

                        try {
                            if (jwtUtil.isTokenValid(token)) {
                                // Extract userId from the token's subject claim
                                String userId = jwtUtil.extractSubject(token);

                                // Set a Principal on this STOMP session.
                                // This userId becomes the "name" used by
                                // convertAndSendToUser() and @AuthenticationPrincipal.
                                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                                        userId,
                                        null,
                                        List.of(new SimpleGrantedAuthority("ROLE_USER")));
                                accessor.setUser(auth);

                                log.info("STOMP_CONNECTED | userId={}", userId);
                            } else {
                                log.warn("STOMP_CONNECT_FAILED | reason=INVALID_JWT");
                                // Returning null rejects the CONNECT frame
                                return null;
                            }
                        } catch (Exception e) {
                            log.warn("STOMP_CONNECT_FAILED | reason=JWT_PARSE_ERROR message={}", e.getMessage());
                            return null;
                        }
                    } else {
                        log.warn("STOMP_CONNECT_FAILED | reason=MISSING_AUTHORIZATION");
                        return null;
                    }
                }

                return message; // Allow all non-CONNECT frames through
            }
        });
    }
}
