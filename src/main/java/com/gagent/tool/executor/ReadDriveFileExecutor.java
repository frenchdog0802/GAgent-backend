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
public class ReadDriveFileExecutor extends LoggedGagentToolExecutor {

    private final UserRepository userRepository;
    private final GoogleWorkspaceService googleWorkspaceService;
    private final RequestContext requestContext;
    private final ActivityLogger activityLogger;

    @Tool(name = "read_drive_file", value = "Read the contents of a text-based file from Google Drive. Needs the exact file ID.")
    public String execute(
            @P("Exact Google Drive file ID.") String file_id
    ) {
        return executeWithActivityLog(activityLogger, "read_drive_file", "Error reading drive file: ", () -> {
            String userIdStr = requestContext.getUserId();
            Integer userId = Integer.parseInt(userIdStr);
            User user = userRepository.findById(userId).orElse(null);

            if (user == null) {
                return "Error: User not found in database.";
            }
            return googleWorkspaceService.readDriveFile(user, file_id);
        });
    }
}
