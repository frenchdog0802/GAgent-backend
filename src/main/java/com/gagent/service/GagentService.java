package com.gagent.service;

import com.gagent.config.RequestContext;
import com.gagent.dto.RunRequest;
import com.gagent.dto.RunResponse;
import com.gagent.entity.Message;
import com.gagent.repository.MessageRepository;
import com.gagent.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;

@Slf4j
@Service
@RequiredArgsConstructor
public class GagentService {

    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final ChatService chatService;
    private final WorkspaceAgent workspaceAgent;
    private final RequestContext requestContext;

    public RunResponse processRequest(RunRequest request, String userId) {
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
        // #region agent log
        try (var w = new java.io.FileWriter("d:/dev/gagent/debug-d2f7ed.log", true)) {
            w.write("{\"sessionId\":\"d2f7ed\",\"hypothesisId\":\"A\",\"location\":\"GagentService.java:processRequest\",\"message\":\"before saveUserMessage\",\"data\":{\"sessionId\":" + sessionId + ",\"msgLen\":" + promptMessage.length() + "},\"timestamp\":" + System.currentTimeMillis() + "}\n");
        } catch (Exception ignored) {}
        // #endregion
        saveUserMessage(promptMessage, userId, sessionId);
        if (sessionId != null) {
            chatService.updateSessionTitleFromFirstMessage(sessionId, promptMessage);
        }

        try {
            // Populate RequestContext for request-scoped thread context tracking
            requestContext.setUserId(userId);
            requestContext.setUserQuery(promptMessage);
            requestContext.setEmailSent(false);
            requestContext.setEphemeralChatMessages(null);

            // Execute the single-stage LangChain4j agent loop (includes tools & DB memory store sync)
            String finalResponse = workspaceAgent.chat(
                    sessionId != null ? sessionId : userId,
                    user.getUserName(),
                    user.getEmail(),
                    LocalDate.now().toString(),
                    promptMessage
            );

            if (sessionId != null) {
                chatService.touchSession(sessionId);
            }
            return new RunResponse(finalResponse, "success", Instant.now());

        } catch (Exception e) {
            log.error("Error processing request", e);
            return new RunResponse("Error: " + e.getMessage(), "error", Instant.now());
        }
    }

    private void saveUserMessage(String content, String userId, Long sessionId) {
        Message saved = messageRepository.save(Message.builder()
                .role("user")
                .content(content)
                .userId(userId)
                .sessionId(sessionId)
                .build());
        // #region agent log
        try (var w = new java.io.FileWriter("d:/dev/gagent/debug-d2f7ed.log", true)) {
            w.write("{\"sessionId\":\"d2f7ed\",\"hypothesisId\":\"A\",\"location\":\"GagentService.java:saveUserMessage\",\"message\":\"user message saved\",\"data\":{\"savedId\":" + saved.getId() + ",\"sessionId\":" + sessionId + "},\"timestamp\":" + System.currentTimeMillis() + "}\n");
        } catch (Exception ignored) {}
        // #endregion
    }
}
