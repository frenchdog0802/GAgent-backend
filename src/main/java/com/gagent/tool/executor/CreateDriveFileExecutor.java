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
public class CreateDriveFileExecutor extends LoggedGagentToolExecutor {

    private final UserRepository userRepository;
    private final GoogleWorkspaceService googleWorkspaceService;
    private final RequestContext requestContext;
    private final ActivityLogger activityLogger;

    @Tool(name = "create_drive_file", value = "Create a new file in Google Drive with the specified name and content.")
    public String execute(
            @P("Name of the new file.") String name,
            @P("Text content to write to the file.") String content
    ) {
        return executeWithActivityLog(activityLogger, "create_drive_file", "Error creating drive file: ", () -> {
            String userIdStr = requestContext.getUserId();
            Integer userId = Integer.parseInt(userIdStr);
            User user = userRepository.findById(userId).orElse(null);

            if (user == null) {
                return "Error: User not found in database.";
            }
            return googleWorkspaceService.createDriveFile(user, name, content);
        });
    }
}
