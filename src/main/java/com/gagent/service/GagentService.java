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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class GagentService {

    private final String openAiApiKey;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final ActivityLogRepository activityLogRepository;
    private final UserContactRepository userContactRepository;
    private final ChatService chatService;
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
            ChatService chatService,
            List<GagentToolExecutor> executors,
            RestClient.Builder restClientBuilder) {
        this.openAiApiKey = openAiApiKey;
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.activityLogRepository = activityLogRepository;
        this.userContactRepository = userContactRepository;
        this.chatService = chatService;
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

        String promptMessage = request.getMessage();
        boolean hasAttachment = request.getAttachmentKey() != null && !request.getAttachmentKey().isEmpty();
        if (hasAttachment) {
            promptMessage += "\n\n[Attached file: " + request.getAttachmentName() + ", S3 Key: " + request.getAttachmentKey() + "]";
        }

        Long sessionId = request.getSessionId();
        saveUserMessage(promptMessage, userId, sessionId);
        if (sessionId != null) {
            chatService.updateSessionTitleFromFirstMessage(sessionId, promptMessage);
        }

        try {
            List<ChatMessage> apiMessages = buildConversationContext(userId, user, sessionId);
            String latestUserMessage = extractLatestUserMessage(apiMessages);

            // STAGE 1: PLANNING
            List<String> steps = normalizePlanSteps(planActions(apiMessages, latestUserMessage));
            log.info("Plan for latest message (len={}): {}", latestUserMessage.length(), steps);

            // STAGE 2: EXECUTION (skip when no tool-backed steps)
            if (!steps.isEmpty()) {
                executeSteps(apiMessages, steps, userId, promptMessage);
            } else {
                log.info("Skipping tool execution — latest message needs no workspace tools");
            }

            // STAGE 3: SYNTHESIS
            String finalContent = synthesizeResponse(apiMessages, latestUserMessage, !steps.isEmpty());

            saveAssistantMessage(finalContent, userId, sessionId);
            if (sessionId != null) {
                chatService.touchSession(sessionId);
            }
            return new RunResponse(finalContent, "success", Instant.now());

        } catch (Exception e) {
            e.printStackTrace();
            return new RunResponse("Error: " + e.getMessage(), "error", Instant.now());
        }
    }

    private void saveUserMessage(String content, String userId, Long sessionId) {
        messageRepository.save(Message.builder()
                .role("user")
                .content(content)
                .userId(userId)
                .sessionId(sessionId)
                .build());
    }

    private void saveAssistantMessage(String content, String userId, Long sessionId) {
        messageRepository.save(Message.builder()
                .role("assistant")
                .content(content)
                .userId(userId)
                .sessionId(sessionId)
                .build());
    }

    private String extractLatestUserMessage(List<ChatMessage> apiMessages) {
        for (int i = apiMessages.size() - 1; i >= 0; i--) {
            ChatMessage msg = apiMessages.get(i);
            if ("user".equals(msg.role()) && msg.content() != null && !msg.content().isBlank()) {
                return msg.content().trim();
            }
        }
        return "";
    }

    private List<ChatMessage> buildConversationContext(String userId, com.gagent.entity.User user, Long sessionId) {
        String accountContext = user != null
                ? "\n\nAuthenticated account (use for identity questions like \"who am I?\"):\n- Name: "
                        + user.getUserName() + "\n- Email: " + user.getEmail()
                : "";

        String systemPrompt = """
                You are a capable Google Workspace assistant for THIS user only. Your scope is Gmail, Google Drive, and Google Contacts (no Calendar tools are available--do not claim you can use them).

                Operating principles:
                - Only call tools when the LATEST user message clearly asks for a Gmail, Drive, or Contacts action right now. Greetings (hi, hello), thanks, small talk, and identity questions do NOT require tools.
                - Do not re-run or repeat actions from earlier turns unless the latest message explicitly asks (e.g. "send it again", "do the same thing").
                - Prefer facts from tool results over assumptions. If a tool fails or returns empty, say so briefly and suggest the next concrete step (e.g. refine search, add contact, check spelling).
                - Call tools as soon as you have enough parameters; do not narrate long plans instead of acting. One step may require several tool calls (e.g. list then read).
                - Match the user's language in final replies.
                - When the latest user message includes an attached file marker like "[Attached file: <name>, S3 Key: <key>]", use that S3 key exactly as s3_file_key and use the provided filename unless the user asks for a different Drive filename.
                """
                + accountContext
                + """

                Tool discipline:
                - send_email: requires a real email address in to_email. If the user gives only a person's name, call get_contact_by_name first. If multiple matches appear, pick the best match from tool output or list options for the user—do not invent addresses.
                - send_email_with_attachment: use when the user asks to send an email and attach the uploaded file. Requires to_email, subject, body, and the attached file's s3_file_key. Resolve names with get_contact_by_name first when needed.
                - get_contact_by_name: use name_query with the name or distinctive fragment the user provided.
                - add_contact: use when the user wants to save someone; contact_name and email_address are required unless the tool schema says otherwise.
                - list_emails: use q with Gmail search syntax when the user filters by sender, subject, unread, date, etc.; set max_results when they ask for "last N" or "a few".
                - read_email: only after you have message_id from list_emails (or the user pasted an id). Never guess message_id.
                - upload_drive_file_from_attachment: use when the user asks to upload, save, or put the uploaded file in Google Drive. Requires the attached file's s3_file_key and a file_name.
                - list_drive_files: use when the user asks to find, list, or search Drive files. Use Google Drive query syntax when filtering by name or type.
                - read_drive_file: only after you have an exact Drive file_id from list_drive_files or the user provided one. Never guess file_id.
                - create_drive_file: use when the user asks to create a new text file in Drive from provided content.
                - write_drive_file: use when the user asks to replace/update an existing Drive file's text content and you have the exact file_id.

                Safety and clarity:
                - Summarize what you did after tools succeed; if something could not be done, state what blocked it.
                - Do not fabricate email content, subjects, or addresses not supported by tools or the conversation.
                - If the user requests an attachment action but no attached file marker/S3 key exists, ask them to upload a file first instead of calling attachment tools.
                """;

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(systemPrompt));

        List<Message> history;
        if (sessionId != null) {
            history = messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
        } else {
            history = messageRepository.findByUserIdOrderByCreatedAtAsc(userId);
        }
        for (Message msg : history) {
            messages.add(new ChatMessage(msg.getRole(), msg.getContent(), null, null, null));
        }
        return messages;
    }

    private List<String> planActions(List<ChatMessage> apiMessages, String latestUserMessage) {
        List<ChatMessage> plannerMessages = new ArrayList<>(apiMessages);
        plannerMessages.add(ChatMessage.user("""
                Produce a minimal ordered plan to satisfy ONLY this latest user message (ignore older messages unless the latest message explicitly refers to them):
                ---
                %s
                ---

                Available tools: send_email, send_email_with_attachment, add_contact, get_contact_by_name, list_emails, read_email, list_drive_files, read_drive_file, create_drive_file, write_drive_file, upload_drive_file_from_attachment. No Calendar.

                Rules:
                - If the latest message is a greeting, thanks, small talk, or identity/chat question (e.g. hi, hello, who am I, what can you do) with NO explicit request to send mail, upload, list, or modify workspace data, output {"steps":[]} with an empty array.
                - Never plan email/Drive/contact actions unless the latest message clearly requests them NOW.
                - Steps must be short user-visible goals (e.g. "Send the attached file by email to my friend"), NOT raw tool names like send_email or get_contact_by_name.
                - One step = one user-visible goal; a step may imply multiple tool calls (e.g. get_contact_by_name then send_email_with_attachment inside ONE step).
                - For "email someone with an attachment", use exactly ONE step that covers contact lookup (if needed) and sending—never split lookup and send into separate steps.
                - If send_email or send_email_with_attachment needs a recipient address and only a name is known, resolve the address via get_contact_by_name within the same send step (not a separate plan step).
                - For "send this/the attached file by email" tasks, plan one send step using send_email_with_attachment, not send_email.
                - For "upload/save this/the attached file to Drive" tasks, plan upload_drive_file_from_attachment.
                - For "show/read that email" tasks, plan list_emails (with q if user gave filters) before read_email.
                - For "show/read that Drive file" tasks, plan list_drive_files (with query if user gave filters) before read_drive_file unless the user supplied an exact file id.
                - Do not add steps for unavailable capabilities.

                Output JSON only, no markdown or prose: {"steps":["...","..."]} or {"steps":[]}
                """.formatted(latestUserMessage)));

        ChatCompletionRequest request = ChatCompletionRequest.of("gpt-4o-mini", plannerMessages)
                .withResponseFormat(ChatCompletionRequest.ResponseFormat.JSON);

        ChatMessage response = callChatCompletion(request);
        try {
            Map<String, List<String>> planData = objectMapper.readValue(response.content(),
                    new TypeReference<Map<String, List<String>>>() {
                    });
            return planData.getOrDefault("steps", List.of());
        } catch (Exception e) {
            log.warn("Failed to parse planner response, using no tool steps", e);
            return List.of();
        }
    }

    private List<String> normalizePlanSteps(List<String> steps) {
        if (steps.size() < 2) {
            return steps;
        }
        List<String> normalized = new ArrayList<>();
        for (int i = 0; i < steps.size(); i++) {
            String current = normalizeStepToken(steps.get(i));
            if (i + 1 < steps.size()) {
                String next = normalizeStepToken(steps.get(i + 1));
                if (isContactLookupStep(current) && isSendEmailStep(next)) {
                    String merged = "Send email to the recipient"
                            + (next.contains("attachment") ? " with the attached file" : "");
                    normalized.add(merged);
                    log.info("Merged plan steps [{} + {}] into one send step", steps.get(i), steps.get(i + 1));
                    i++;
                    continue;
                }
            }
            normalized.add(steps.get(i));
        }
        return normalized;
    }

    private String normalizeStepToken(String step) {
        return step == null ? "" : step.toLowerCase().replace(' ', '_');
    }

    private boolean isContactLookupStep(String stepToken) {
        return stepToken.equals("get_contact_by_name")
                || (stepToken.contains("contact") && stepToken.contains("look"));
    }

    private boolean isSendEmailStep(String stepToken) {
        return stepToken.equals("send_email")
                || stepToken.equals("send_email_with_attachment")
                || stepToken.startsWith("send_email");
    }

    private void executeSteps(List<ChatMessage> apiMessages, List<String> steps, String userId, String userQuery) {
        List<Tool> tools = WorkspaceTools.getTools();
        boolean[] emailSentThisRun = {false};

        for (int i = 0; i < steps.size(); i++) {
            apiMessages.add(ChatMessage.user("[SYSTEM] Step " + (i + 1) + " of " + steps.size() + ": " + steps.get(i)));

            boolean stepComplete = false;
            while (!stepComplete) {
                ChatCompletionRequest request = ChatCompletionRequest.of("gpt-4o-mini", apiMessages).withTools(tools);
                ChatMessage assistantResponse = callChatCompletion(request);
                apiMessages.add(assistantResponse);

                if (assistantResponse.toolCalls() != null && !assistantResponse.toolCalls().isEmpty()) {
                    processToolCalls(assistantResponse.toolCalls(), apiMessages, userId, userQuery, emailSentThisRun);
                } else {
                    stepComplete = true;
                }
            }
        }
    }

    private void processToolCalls(List<ToolCall> toolCalls, List<ChatMessage> apiMessages, String userId, String userQuery,
            boolean[] emailSentThisRun) {
        for (ToolCall toolCall : toolCalls) {
            String functionName = toolCall.function().name();
            String result;
            String status = "success";
            boolean isSendTool = functionName.startsWith("send_email");

            if (isSendTool && emailSentThisRun[0]) {
                result = "Skipped: an email was already sent successfully in this request. Do not send again.";
                status = "skipped";
                log.warn("Skipped duplicate {}", functionName);
                apiMessages.add(ChatMessage.tool(toolCall.id(), functionName, result));
                continue;
            }

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

            if (isSendTool && "success".equals(status)) {
                emailSentThisRun[0] = true;
                log.info("Email sent via {}", functionName);
            }

            if (!"skipped".equals(status)) {
                logActivity(userId, userQuery, functionName, status, result);
            }
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

    private String synthesizeResponse(List<ChatMessage> apiMessages, String latestUserMessage, boolean toolsRanThisRequest) {
        apiMessages.add(ChatMessage.user("""
                [SYSTEM] Write the final user-facing reply to ONLY this latest user message:
                ---
                %s
                ---

                Rules:
                - If toolsRanThisRequest is false, answer conversationally. Do NOT claim you sent email, uploaded files, or ran tools in this turn.
                - Do NOT repeat or summarize actions from previous turns unless the latest message asks about them.
                - If toolsRanThisRequest is true, summarize only what tools accomplished in THIS request.
                - For "who am I" / identity questions, use the authenticated account info from the system message.
                - Be concise. Match the user's language. Do not expose raw internal step labels unless helpful.

                toolsRanThisRequest=%s
                """.formatted(latestUserMessage, toolsRanThisRequest)));
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
