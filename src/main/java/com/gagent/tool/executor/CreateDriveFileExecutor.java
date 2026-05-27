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
public class CreateDriveFileExecutor implements GagentTool {

    private final UserRepository userRepository;
    private final GoogleWorkspaceService googleWorkspaceService;
    private final RequestContext requestContext;
    private final ActivityLogger activityLogger;

    @Tool(name = "create_drive_file", value = "Create a new file in Google Drive with the specified name and content.")
    public String execute(
            @P("Name of the new file.") String name,
            @P("Text content to write to the file.") String content
    ) {
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
                result = googleWorkspaceService.createDriveFile(user, name, content);
                if (result.startsWith("Error")) {
                    status = "failed";
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            result = "Error creating drive file: " + e.getMessage();
            status = "failed";
        }
        activityLogger.logActivity("create_drive_file", status, result);
        return result;
    }
}
