package com.openlab.mx.movo.chat.repository;

import com.openlab.mx.movo.chat.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findByInstanceIdOrderByCreatedAtAsc(Long instanceId);
}
