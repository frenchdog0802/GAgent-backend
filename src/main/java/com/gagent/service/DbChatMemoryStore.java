package com.gagent.service;

import com.gagent.config.RequestContext;
import com.gagent.entity.Message;
import com.gagent.repository.MessageRepository;
import dev.langchain4j.data.message.*;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class DbChatMemoryStore implements ChatMemoryStore {

    private final MessageRepository messageRepository;
    private final RequestContext requestContext;

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        List<ChatMessage> ephemeral = requestContext.getEphemeralChatMessages();
        if (ephemeral != null) {
            return new ArrayList<>(ephemeral);
        }

        List<Message> history;
        if (memoryId instanceof Long) {
            history = messageRepository.findBySessionIdOrderByCreatedAtAsc((Long) memoryId);
        } else {
            history = messageRepository.findByUserIdOrderByCreatedAtAsc((String) memoryId);
        }

        List<ChatMessage> chatMessages = new ArrayList<>();
        for (Message msg : history) {
            if ("user".equals(msg.getRole())) {
                chatMessages.add(new UserMessage(msg.getContent()));
            } else if ("assistant".equals(msg.getRole())) {
                chatMessages.add(new AiMessage(msg.getContent()));
            } else if ("system".equals(msg.getRole())) {
                chatMessages.add(new SystemMessage(msg.getContent()));
            }
        }
        // #region agent log
        try (var w = new java.io.FileWriter("d:/dev/gagent/debug-d2f7ed.log", true)) {
            long userCount = chatMessages.stream().filter(m -> m instanceof UserMessage).count();
            w.write("{\"sessionId\":\"d2f7ed\",\"hypothesisId\":\"B\",\"location\":\"DbChatMemoryStore.java:getMessages\",\"message\":\"loaded messages\",\"data\":{\"memoryId\":\"" + memoryId + "\",\"total\":" + chatMessages.size() + ",\"userCount\":" + userCount + ",\"ephemeral\":" + (requestContext.getEphemeralChatMessages() != null) + "},\"timestamp\":" + System.currentTimeMillis() + "}\n");
        } catch (Exception ignored) {}
        // #endregion
        return chatMessages;
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        requestContext.setEphemeralChatMessages(new ArrayList<>(messages));

        List<ChatMessage> persistable = messages.stream()
                .filter(msg -> msg instanceof UserMessage || (msg instanceof AiMessage && !((AiMessage) msg).hasToolExecutionRequests()))
                .collect(Collectors.toList());

        List<Message> existing;
        if (memoryId instanceof Long) {
            existing = messageRepository.findBySessionIdOrderByCreatedAtAsc((Long) memoryId);
        } else {
            existing = messageRepository.findByUserIdOrderByCreatedAtAsc((String) memoryId);
        }

        int existingCount = existing.size();
        // #region agent log
        try (var w = new java.io.FileWriter("d:/dev/gagent/debug-d2f7ed.log", true)) {
            long persistUserCount = persistable.stream().filter(m -> m instanceof UserMessage).count();
            w.write("{\"sessionId\":\"d2f7ed\",\"hypothesisId\":\"A,E\",\"location\":\"DbChatMemoryStore.java:updateMessages\",\"message\":\"updateMessages called\",\"data\":{\"memoryId\":\"" + memoryId + "\",\"existingCount\":" + existingCount + ",\"persistableCount\":" + persistable.size() + ",\"persistUserCount\":" + persistUserCount + ",\"willSaveNew\":" + (persistable.size() > existingCount) + ",\"newCount\":" + Math.max(0, persistable.size() - existingCount) + "},\"timestamp\":" + System.currentTimeMillis() + "}\n");
        } catch (Exception ignored) {}
        // #endregion
        if (persistable.size() > existingCount) {
            for (int i = existingCount; i < persistable.size(); i++) {
                ChatMessage chatMsg = persistable.get(i);
                Message.MessageBuilder builder = Message.builder();
                String role;
                if (chatMsg instanceof UserMessage) {
                    role = "user";
                    builder.role("user").content(((UserMessage) chatMsg).singleText());
                } else {
                    role = "assistant";
                    builder.role("assistant").content(((AiMessage) chatMsg).text());
                }

                if (memoryId instanceof Long) {
                    builder.sessionId((Long) memoryId);
                    builder.userId(requestContext.getUserId());
                } else {
                    builder.userId((String) memoryId);
                }
                Message saved = messageRepository.save(builder.build());
                // #region agent log
                try (var w = new java.io.FileWriter("d:/dev/gagent/debug-d2f7ed.log", true)) {
                    w.write("{\"sessionId\":\"d2f7ed\",\"hypothesisId\":\"A,E\",\"location\":\"DbChatMemoryStore.java:updateMessages\",\"message\":\"saved new message\",\"data\":{\"savedId\":" + saved.getId() + ",\"role\":\"" + role + "\",\"index\":" + i + "},\"timestamp\":" + System.currentTimeMillis() + "}\n");
                } catch (Exception ignored) {}
                // #endregion
            }
        }
    }

    @Override
    public void deleteMessages(Object memoryId) {
        // No-op - session deletion is handled by ChatService.deleteSession
    }
}
