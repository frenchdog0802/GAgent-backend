package com.gagent.tool.executor;

import com.gagent.entity.User;
import com.gagent.repository.UserRepository;
import com.gagent.service.GoogleWorkspaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class ListEmailsExecutor implements GagentToolExecutor {

    private final UserRepository userRepository;
    private final GoogleWorkspaceService googleWorkspaceService;

    @Override
    public String getFunctionName() {
        return "list_emails";
    }

    @Override
    public String execute(String userIdStr, Map<String, Object> arguments) {
        Integer maxResults = (Integer) arguments.get("max_results");
        String query = (String) arguments.get("q");

        System.out.println("====== EXECUTING LIST EMAILS TOOL ======");
        System.out.println("Max Results: " + maxResults);
        System.out.println("Query: " + query);
        System.out.println("========================================");

        try {
            Integer userId = Integer.parseInt(userIdStr);
            User user = userRepository.findById(userId).orElse(null);

            if (user == null) {
                return "Error: User not found in database.";
            }

            return googleWorkspaceService.listEmails(user, maxResults, query);
        } catch (Exception e) {
            e.printStackTrace();
            return "Error listing emails: " + e.getMessage();
        }
    }
}
