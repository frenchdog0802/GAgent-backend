package com.gagent.service;

import com.gagent.dto.ChatMessageDto;
import com.gagent.dto.ChatSessionDto;
import com.gagent.entity.ChatSession;
import com.gagent.entity.Message;
import com.gagent.repository.ChatSessionRepository;
import com.gagent.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    static final String NEW_CHAT_GREETING =
            "Hello! I'm Gagent, your AI-powered Google Workspace assistant. "
                    + "Ask me to search Gmail, manage Drive files, look up contacts, or summarize your activity logs.";

    private final ChatSessionRepository chatSessionRepository;
    private final MessageRepository messageRepository;

    @Transactional(readOnly = true)
    public List<ChatSessionDto> getUserSessions(String userId) {
        return chatSessionRepository.findByUserIdOrderByUpdatedAtDesc(userId)
                .stream()
                .map(this::toSessionDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public ChatSessionDto createSession(String userId) {
        ChatSession session = ChatSession.builder()
                .userId(userId)
                .title("New Chat")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        
        ChatSession saved = chatSessionRepository.save(session);
        messageRepository.save(Message.builder()
                .sessionId(saved.getId())
                .userId(userId)
                .role("assistant")
                .content(NEW_CHAT_GREETING)
                .build());
        log.info("Created new chat session {} for user {}", saved.getId(), userId);
        return toSessionDto(saved);
    }

    @Transactional(readOnly = true)
    public List<ChatMessageDto> getSessionMessages(Long sessionId) {
        return messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId)
                .stream()
                .map(this::toMessageDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteSession(Long sessionId) {
        chatSessionRepository.deleteById(sessionId);
        List<Message> messages = messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
        messageRepository.deleteAll(messages);
        log.info("Deleted chat session {} and its messages", sessionId);
    }

    @Transactional
    public void updateSessionTitleFromFirstMessage(Long sessionId, String firstMessageContent) {
        chatSessionRepository.findById(sessionId).ifPresent(session -> {
            if ("New Chat".equals(session.getTitle()) || session.getTitle().isBlank()) {
                String cleanMessage = firstMessageContent.replaceAll("\\[Attached file:[^\\]]+\\]", "").trim();
                String[] words = cleanMessage.split("\\s+");
                int limit = Math.min(words.length, 5);
                String title = String.join(" ", Arrays.copyOfRange(words, 0, limit));
                if (cleanMessage.length() > title.length()) {
                    title += "...";
                }
                if (title.isBlank()) {
                    title = "New Chat";
                }
                session.setTitle(title);
                session.setUpdatedAt(Instant.now());
                chatSessionRepository.save(session);
                log.info("Updated title for session {} to: {}", sessionId, title);
            }
        });
    }

    @Transactional
    public void touchSession(Long sessionId) {
        chatSessionRepository.findById(sessionId).ifPresent(session -> {
            session.setUpdatedAt(Instant.now());
            chatSessionRepository.save(session);
        });
    }

    private ChatSessionDto toSessionDto(ChatSession session) {
        return ChatSessionDto.builder()
                .id(session.getId())
                .title(session.getTitle())
                .createdAt(session.getCreatedAt())
                .updatedAt(session.getUpdatedAt())
                .build();
    }

    private ChatMessageDto toMessageDto(Message message) {
        return ChatMessageDto.builder()
                .id(message.getId())
                .role(message.getRole())
                .content(message.getContent())
                .createdAt(message.getCreatedAt())
                .build();
    }
}
