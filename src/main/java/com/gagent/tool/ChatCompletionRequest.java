package com.gagent.tool;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatCompletionRequest(
    String model,
    List<ChatMessage> messages,
    List<Tool> tools,
    @JsonProperty("response_format") ResponseFormat responseFormat
) {
    public record ResponseFormat(String type) {
        public static final ResponseFormat JSON = new ResponseFormat("json_object");
    }

    public static ChatCompletionRequest of(String model, List<ChatMessage> messages) {
        return new ChatCompletionRequest(model, messages, null, null);
    }

    public ChatCompletionRequest withTools(List<Tool> tools) {
        return new ChatCompletionRequest(this.model, this.messages, tools, this.responseFormat);
    }

    public ChatCompletionRequest withResponseFormat(ResponseFormat responseFormat) {
        return new ChatCompletionRequest(this.model, this.messages, this.tools, responseFormat);
    }
}
