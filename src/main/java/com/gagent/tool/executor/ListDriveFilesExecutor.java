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
public class ListDriveFilesExecutor extends LoggedGagentToolExecutor {

    private final UserRepository userRepository;
    private final GoogleWorkspaceService googleWorkspaceService;
    private final RequestContext requestContext;
    private final ActivityLogger activityLogger;

    @Tool(name = "list_drive_files", value = "List files from the user's Google Drive. Use query to filter by name, mimeType, etc.")
    public String execute(
            @P(value = "Google Drive search query (e.g. 'name contains \"report\"'). Optional.", required = false) String query,
            @P(value = "Max results to return (default 10).", required = false) Integer max_results
    ) {
        return executeWithActivityLog(activityLogger, "list_drive_files", "Error listing drive files: ", () -> {
            String userIdStr = requestContext.getUserId();
            Integer userId = Integer.parseInt(userIdStr);
            User user = userRepository.findById(userId).orElse(null);

            if (user == null) {
                return "Error: User not found in database.";
            }
            return googleWorkspaceService.listDriveFiles(user, max_results, query);
        });
    }
}
