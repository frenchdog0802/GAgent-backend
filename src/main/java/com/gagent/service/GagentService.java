package com.gagent.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gagent.dto.RunRequest;
import com.gagent.dto.RunResponse;
import com.gagent.entity.ActivityLog;
import com.gagent.entity.Message;
import com.gagent.repository.ActivityLogRepository;
import com.gagent.repository.MessageRepository;
import com.gagent.repository.UserRepository;
import com.gagent.tool.*;
import com.gagent.tool.executor.GagentToolExecutor;
import com.gagent.repository.UserContactRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class GagentService {

    private final String openAiApiKey;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final ActivityLogRepository activityLogRepository;
    private final UserContactRepository userContactRepository;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private final RestClient restClient;
    private final Map<String, GagentToolExecutor> toolExecutors = new HashMap<>();

    public GagentService(
            @Value("${openai.api.key:}") String openAiApiKey,
            MessageRepository messageRepository,
            UserRepository userRepository,
            ActivityLogRepository activityLogRepository,
            UserContactRepository userContactRepository,
            List<GagentToolExecutor> executors,
            RestClient.Builder restClientBuilder) {
        this.openAiApiKey = openAiApiKey;
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.activityLogRepository = activityLogRepository;
        this.userContactRepository = userContactRepository;
        this.restClient = restClientBuilder
                .baseUrl("https://api.openai.com/v1")
                .defaultHeader("Authorization", "Bearer " + openAiApiKey)
                .build();
        for (GagentToolExecutor executor : executors) {
            this.toolExecutors.put(executor.getFunctionName(), executor);
        }
    }

    public RunResponse processRequest(RunRequest request, String userId) {
        if (openAiApiKey == null || openAiApiKey.isEmpty()) {
            return new RunResponse(
                    "Error: OpenAI API key is not configured.", "error", Instant.now());
        }

        // Permission Gatekeeper: Ensure user has authenticated with Google
        com.gagent.entity.User user = userRepository.findById(Integer.parseInt(userId)).orElse(null);
        if (user == null || user.getGoogleAccessToken() == null || user.getGoogleAccessToken().isEmpty()) {
            return new RunResponse("Google Workspace permission required.", "AUTH_REQUIRED", Instant.now());
        }

        saveUserMessage(request.getMessage(), userId);

        try {
            List<ChatMessage> apiMessages = buildConversationContext(userId);

            // STAGE 1: PLANNING
            List<String> steps = planActions(apiMessages);

            // STAGE 2: EXECUTION
            executeSteps(apiMessages, steps, userId, request.getMessage());

            // STAGE 3: SYNTHESIS
            String finalContent = synthesizeResponse(apiMessages);

            saveAssistantMessage(finalContent, userId);
            return new RunResponse(finalContent, "success", Instant.now());

        } catch (Exception e) {
            e.printStackTrace();
            return new RunResponse("Error: " + e.getMessage(), "error", Instant.now());
        }
    }

    private void saveUserMessage(String content, String userId) {
        messageRepository.save(Message.builder()
                .role("user")
                .content(content)
                .userId(userId)
                .build());
    }

    private void saveAssistantMessage(String content, String userId) {
        messageRepository.save(Message.builder()
                .role("assistant")
                .content(content)
                .userId(userId)
                .build());
    }

    private List<ChatMessage> buildConversationContext(String userId) {
        String systemPrompt = """
                You are a capable Google Workspace assistant for THIS user only. Your scope is Gmail and Google Contacts (no Calendar or Drive tools are available—do not claim you can use them).

                Operating principles:
                - Prefer facts from tool results over assumptions. If a tool fails or returns empty, say so briefly and suggest the next concrete step (e.g. refine search, add contact, check spelling).
                - Call tools as soon as you have enough parameters; do not narrate long plans instead of acting. One step may require several tool calls (e.g. list then read).
                - Match the user's language in final replies (e.g. Chinese if they wrote in Chinese).

                Tool discipline:
                - send_email: requires a real email address in to_email. If the user gives only a person's name, call get_contact_by_name first. If multiple matches appear, pick the best match from tool output or list options for the user—do not invent addresses.
                - get_contact_by_name: use name_query with the name or distinctive fragment the user provided.
                - add_contact: use when the user wants to save someone; contact_name and email_address are required unless the tool schema says otherwise.
                - list_emails: use q with Gmail search syntax when the user filters by sender, subject, unread, date, etc.; set max_results when they ask for "last N" or "a few".
                - read_email: only after you have message_id from list_emails (or the user pasted an id). Never guess message_id.

                Safety and clarity:
                - Summarize what you did after tools succeed; if something could not be done, state what blocked it.
                - Do not fabricate email content, subjects, or addresses not supported by tools or the conversation.
                """;

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(systemPrompt));

        List<Message> history = messageRepository.findByUserIdOrderByCreatedAtAsc(userId);
        for (Message msg : history) {
            messages.add(new ChatMessage(msg.getRole(), msg.getContent(), null, null, null));
        }
        return messages;
    }

    private List<String> planActions(List<ChatMessage> apiMessages) {
        List<ChatMessage> plannerMessages = new ArrayList<>(apiMessages);
        plannerMessages.add(ChatMessage.user("""
                Produce a minimal ordered plan to satisfy the latest user message using ONLY these tools: send_email, add_contact, get_contact_by_name, list_emails, read_email. No Calendar/Drive.

                Rules:
                - One step = one user-visible goal; a step may imply multiple tool calls (e.g. list_emails then read_email).
                - If send_email needs a recipient address and only a name is known, the plan MUST include resolving the address via get_contact_by_name before send_email.
                - For "show/read that email" tasks, plan list_emails (with q if user gave filters) before read_email.
                - Do not add steps for unavailable capabilities.

                Output JSON only, no markdown or prose: {"steps":["...","..."]}
                """));

        ChatCompletionRequest request = ChatCompletionRequest.of("gpt-4o-mini", plannerMessages)
                .withResponseFormat(ChatCompletionRequest.ResponseFormat.JSON);

        ChatMessage response = callChatCompletion(request);
        try {
            Map<String, List<String>> planData = objectMapper.readValue(response.content(),
                    new TypeReference<Map<String, List<String>>>() {
                    });
            return planData.getOrDefault("steps", List.of("Fulfill the user's request directly."));
        } catch (Exception e) {
            return List.of("Fulfill the user's request directly.");
        }
    }

    private void executeSteps(List<ChatMessage> apiMessages, List<String> steps, String userId, String userQuery) {
        List<Tool> tools = WorkspaceTools.getTools();

        for (int i = 0; i < steps.size(); i++) {
            apiMessages.add(ChatMessage.user("[SYSTEM] Step " + (i + 1) + " of " + steps.size() + ": " + steps.get(i)));

            boolean stepComplete = false;
            while (!stepComplete) {
                ChatCompletionRequest request = ChatCompletionRequest.of("gpt-4o-mini", apiMessages).withTools(tools);
                ChatMessage assistantResponse = callChatCompletion(request);
                apiMessages.add(assistantResponse);

                if (assistantResponse.toolCalls() != null && !assistantResponse.toolCalls().isEmpty()) {
                    processToolCalls(assistantResponse.toolCalls(), apiMessages, userId, userQuery);
                } else {
                    stepComplete = true;
                }
            }
        }
    }

    private void processToolCalls(List<ToolCall> toolCalls, List<ChatMessage> apiMessages, String userId, String userQuery) {
        for (ToolCall toolCall : toolCalls) {
            String functionName = toolCall.function().name();
            String result;
            String status = "success";

            GagentToolExecutor executor = toolExecutors.get(functionName);
            if (executor != null) {
                try {
                    Map<String, Object> args = objectMapper.readValue(toolCall.function().arguments(),
                            new TypeReference<Map<String, Object>>() {
                            });
                    result = executor.execute(userId, args);
                    if (result.startsWith("Error")) status = "failed";
                } catch (Exception e) {
                    result = "Error parsing arguments: " + e.getMessage();
                    status = "failed";
                }
            } else {
                result = "Error: Unknown function.";
                status = "failed";
            }

            logActivity(userId, userQuery, functionName, status, result);
            apiMessages.add(ChatMessage.tool(toolCall.id(), functionName, result));
        }
    }

    private void logActivity(String userId, String query, String action, String status, String result) {
        String toolStr = "docs";
        if (action != null) {
            if (action.contains("email")) {
                toolStr = "mail";
            } else if (action.contains("contact")) {
                toolStr = "contacts";
            } else if (action.contains("calendar")) {
                toolStr = "calendar";
            }
        }
        activityLogRepository.save(ActivityLog.builder()
                .userId(userId)
                .timestamp(Instant.now())
                .command(query)
                .action(action)
                .tool(toolStr)
                .status(status)
                .error(status.equals("failed") ? result : null)
                .build());
    }

    private String synthesizeResponse(List<ChatMessage> apiMessages) {
        apiMessages.add(ChatMessage.user("""
                [SYSTEM] Write the final user-facing reply.

                Use the conversation and tool results above: state what was done (or attempted), key facts (subjects, times, addresses) only when they matter, and clearly mention any failure or missing data.
                Be concise. Match the user's language. Do not expose raw internal step labels unless helpful.
                """));
        ChatCompletionRequest request = ChatCompletionRequest.of("gpt-4o-mini", apiMessages);
        return callChatCompletion(request).content();
    }

    private ChatMessage callChatCompletion(ChatCompletionRequest request) {
        ResponseEntity<Map> response = restClient.post()
                .uri("/chat/completions")
                .body(request)
                .retrieve()
                .toEntity(Map.class);

        Map<String, Object> body = response.getBody();
        if (body != null && body.containsKey("choices")) {
            List<Map<String, Object>> choices = (List<Map<String, Object>>) body.get("choices");
            if (!choices.isEmpty()) {
                Map<String, Object> messageMap = (Map<String, Object>) choices.get(0).get("message");
                return objectMapper.convertValue(messageMap, ChatMessage.class);
            }
        }
        throw new RuntimeException("Empty response from OpenAI");
    }
}
