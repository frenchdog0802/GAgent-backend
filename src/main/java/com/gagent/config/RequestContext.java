package com.gagent.config;

import dev.langchain4j.data.message.ChatMessage;
import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import java.util.List;

@Component
@RequestScope
@Getter
@Setter
public class RequestContext {
    private String userId;
    private String userQuery;
    private boolean emailSent;
    /** Full in-request chat history including tool call/result pairs (not persisted to DB). */
    private List<ChatMessage> ephemeralChatMessages;
}
