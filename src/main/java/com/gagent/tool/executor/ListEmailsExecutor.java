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
public class ListEmailsExecutor extends LoggedGagentToolExecutor {

    private final UserRepository userRepository;
    private final GoogleWorkspaceService googleWorkspaceService;
    private final RequestContext requestContext;
    private final ActivityLogger activityLogger;

    @Tool(name = "list_emails", value = "List Gmail messages (metadata/summary). First step when the user asks about inbox, unread, recent mail, or finding a message before read_email. Build q using Gmail search operators when helpful: from:, to:, subject:, is:unread, is:read, newer_than:, older_than:, has:attachment, label:. Leave q empty only for generic 'recent inbox' requests.")
    public String execute(
            @P(value = "Max messages to return; if omitted the server defaults to 10. Use 5–20 for 'a few', more for broader searches (Gmail API has its own upper bound).", required = false) Integer max_results,
            @P(value = "Gmail search string (optional). Examples: 'from:boss is:unread', 'subject:invoice newer_than:7d', 'is:important'.", required = false) String q
    ) {
        System.out.println("====== EXECUTING LIST EMAILS TOOL ======");
        System.out.println("Max Results: " + max_results);
        System.out.println("Query: " + q);
        System.out.println("========================================");

        return executeWithActivityLog(activityLogger, "list_emails", "Error listing emails: ", () -> {
            String userIdStr = requestContext.getUserId();
            Integer userId = Integer.parseInt(userIdStr);
            User user = userRepository.findById(userId).orElse(null);

            if (user == null) {
                return "Error: User not found in database.";
            }
            return googleWorkspaceService.listEmails(user, max_results, q);
        });
    }
}
