package com.gagent.config;

import com.gagent.service.DbChatMemoryStore;
import com.gagent.service.WorkspaceAgent;
import com.gagent.tool.executor.GagentTool;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class LangChain4jConfig {

    @Bean
    public ChatLanguageModel chatLanguageModel(
            @Value("${langchain4j.open-ai.chat-model.api-key:}") String apiKey,
            @Value("${langchain4j.open-ai.chat-model.model-name:gpt-4o-mini}") String modelName) {
        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    @Bean
    public ChatMemoryProvider chatMemoryProvider(DbChatMemoryStore chatMemoryStore) {
        return memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(20)
                .chatMemoryStore(chatMemoryStore)
                .build();
    }

    @Bean
    public WorkspaceAgent workspaceAgent(
            ChatLanguageModel chatLanguageModel,
            ChatMemoryProvider chatMemoryProvider,
            List<GagentTool> tools) {
        log.info("Registering {} tool objects with AiServices:", tools.size());
        for (GagentTool tool : tools) {
            log.info("  -> {}", tool.getClass().getSimpleName());
        }
        List<Object> toolObjects = new ArrayList<>(tools);
        return AiServices.builder(WorkspaceAgent.class)
                .chatLanguageModel(chatLanguageModel)
                .chatMemoryProvider(chatMemoryProvider)
                .tools(toolObjects)
                .build();
    }
}
