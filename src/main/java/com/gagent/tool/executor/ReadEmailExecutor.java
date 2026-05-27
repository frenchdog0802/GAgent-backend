package com.gagent.tool.executor;

import com.gagent.config.RequestContext;
import com.gagent.entity.User;
import com.gagent.repository.UserRepository;
import com.gagent.service.ActivityLogger;
import com.gagent.service.GoogleWorkspaceService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReadEmailExecutor implements GagentTool {

    private final UserRepository userRepository;
    private final GoogleWorkspaceService googleWorkspaceService;
    private final RequestContext requestContext;
    private final ActivityLogger activityLogger;

    @Tool(name = "read_email", value = "Fetch full content for one Gmail message. Always use message_id returned by list_emails (or supplied by the user). Never invent or truncate an id to guess—if missing, call list_emails first.")
    public String execute(
            @P("Exact Gmail API message id from a prior list_emails result.") String message_id
    ) {
        System.out.println("====== EXECUTING READ EMAIL TOOL ======");
        System.out.println("Message ID: " + message_id);
        System.out.println("=======================================");

        String result;
        String status = "success";
        try {
            String userIdStr = requestContext.getUserId();
            Integer userId = Integer.parseInt(userIdStr);
            User user = userRepository.findById(userId).orElse(null);

            if (user == null) {
                result = "Error: User not found in database.";
                status = "failed";
            } else {
                result = googleWorkspaceService.readEmail(user, message_id);
                if (result.startsWith("Error")) {
                    status = "failed";
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            result = "Error reading email: " + e.getMessage();
            status = "failed";
        }
        activityLogger.logActivity("read_email", status, result);
        return result;
    }
}
