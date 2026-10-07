package com.openlab.mx.share.chat.controller;

import com.openlab.mx.share.chat.entity.ChatMessage;
import com.openlab.mx.share.chat.service.ChatAuthorizationService;
import com.openlab.mx.share.experience.entity.ExperienceInstance;
import com.openlab.mx.share.shared.CurrentUser;
import com.openlab.mx.share.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Chat por instancia (spec §5.6): lista de grupos, sala con historial y
 * envío que persiste + publica a /topic/instances/{id}.
 */
@Controller
@RequiredArgsConstructor
public class ChatController {

    private final ChatAuthorizationService authorization;
    private final SimpMessagingTemplate messagingTemplate;
    private final CurrentUser currentUser;

    @GetMapping("/chats")
    public String list(Model model) {
        User user = currentUser.require();
        model.addAttribute("grupos", authorization.groupsFor(user));
        return "chat/list";
    }

    @GetMapping("/chats/{instanceId}")
    public String room(@PathVariable Long instanceId, Model model) {
        User user = currentUser.require();
        ExperienceInstance instance = authorization.instance(instanceId, user);
        model.addAttribute("instanceId", instanceId);
        model.addAttribute("instance", instance);
        model.addAttribute("mensajes", authorization.history(instanceId, user));
        model.addAttribute("archivado", instance.isArchived());
        return "chat/room";
    }

    @PostMapping("/chats/{instanceId}/send")
    public String send(@PathVariable Long instanceId, @RequestParam String text) {
        User user = currentUser.require();
        ChatMessage message = authorization.sendMessage(instanceId, user, text);
        messagingTemplate.convertAndSend("/topic/instances/" + instanceId,
                payload(user.getUsername(), message.getText()));
        return "redirect:/chats/" + instanceId;
    }

    private String payload(String username, String text) {
        return "{\"user\":\"" + escape(username) + "\",\"text\":\"" + escape(text) + "\"}";
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
