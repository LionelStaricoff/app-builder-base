package com.openlab.mx.share.chat.config;

import com.openlab.mx.share.chat.service.ChatAuthorizationService;
import com.openlab.mx.share.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.Map;

/**
 * Autenticación de la conexión WS (spec §5.6/§7): la sesión HTTP del
 * handshake aporta el usuario; CONNECT sin sesión se rechaza y SUBSCRIBE
 * solo entra si el usuario es dueño o tiene reserva CONFIRMADA.
 */
@Component
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private static final String SECURITY_CONTEXT_KEY = "SPRING_SECURITY_CONTEXT";
    private static final String INSTANCE_TOPIC = "/topic/instances/";

    private final ChatAuthorizationService chatAuthorizationService;
    private final UserRepository userRepository;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        StompCommand command = accessor.getCommand();

        if (command == StompCommand.CONNECT) {
            if (resolveUsername(accessor) == null) {
                throw new AccessDeniedException("Conexión WS sin sesión iniciada");
            }
            return message;
        }

        if (command == StompCommand.SUBSCRIBE) {
            String destination = accessor.getDestination();
            if (destination == null || !destination.startsWith(INSTANCE_TOPIC)) {
                return message;
            }
            String username = resolveUsername(accessor);
            var user = username == null ? null : userRepository.findByUsername(username).orElse(null);
            String idText = destination.substring(INSTANCE_TOPIC.length());
            Long instanceId;
            try {
                instanceId = Long.valueOf(idText);
            } catch (NumberFormatException e) {
                throw new AccessDeniedException("Destino inválido");
            }
            if (user == null || !chatAuthorizationService.canAccess(instanceId, user)) {
                throw new AccessDeniedException("No podés ver ese chat");
            }
            return message;
        }

        if (command == StompCommand.SEND && resolveUsername(accessor) == null) {
            throw new AccessDeniedException("Conexión WS sin sesión iniciada");
        }
        return message;
    }

    private String resolveUsername(StompHeaderAccessor accessor) {
        Principal principal = accessor.getUser();
        if (principal instanceof Authentication authentication && authentication.isAuthenticated()) {
            return authentication.getName();
        }
        if (principal != null) {
            return principal.getName();
        }
        Map<String, Object> attributes = accessor.getSessionAttributes();
        if (attributes != null && attributes.get(SECURITY_CONTEXT_KEY) instanceof SecurityContext context
                && context.getAuthentication() != null) {
            return context.getAuthentication().getName();
        }
        return null;
    }
}
