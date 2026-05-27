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
public class ReadDriveFileExecutor implements GagentTool {

    private final UserRepository userRepository;
    private final GoogleWorkspaceService googleWorkspaceService;
    private final RequestContext requestContext;
    private final ActivityLogger activityLogger;

    @Tool(name = "read_drive_file", value = "Read the contents of a text-based file from Google Drive. Needs the exact file ID.")
    public String execute(
            @P("Exact Google Drive file ID.") String file_id
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
                result = googleWorkspaceService.readDriveFile(user, file_id);
                if (result.startsWith("Error")) {
                    status = "failed";
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            result = "Error reading drive file: " + e.getMessage();
            status = "failed";
        }
        activityLogger.logActivity("read_drive_file", status, result);
        return result;
    }
}
