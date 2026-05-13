package com.gagent.tool;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatMessage(
    String role,
    String content,
    String name,
    @JsonProperty("tool_call_id") String toolCallId,
    @JsonProperty("tool_calls") List<ToolCall> toolCalls
) {
    public static ChatMessage system(String content) {
        return new ChatMessage("system", content, null, null, null);
    }
    public static ChatMessage user(String content) {
        return new ChatMessage("user", content, null, null, null);
    }
    public static ChatMessage assistant(String content) {
        return new ChatMessage("assistant", content, null, null, null);
    }
    public static ChatMessage assistant(List<ToolCall> toolCalls) {
        return new ChatMessage("assistant", null, null, null, toolCalls);
    }
    public static ChatMessage tool(String toolCallId, String name, String content) {
        return new ChatMessage("tool", content, name, toolCallId, null);
    }
}
