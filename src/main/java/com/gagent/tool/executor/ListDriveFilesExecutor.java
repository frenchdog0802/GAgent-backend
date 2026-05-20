package com.gagent.tool.executor;

import com.gagent.entity.User;
import com.gagent.repository.UserRepository;
import com.gagent.service.GoogleWorkspaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class ListDriveFilesExecutor implements GagentToolExecutor {

    private final UserRepository userRepository;
    private final GoogleWorkspaceService googleWorkspaceService;

    @Override
    public String getFunctionName() {
        return "list_drive_files";
    }

    @Override
    public String execute(String userIdStr, Map<String, Object> arguments) {
        String query = (String) arguments.get("query");
        Integer maxResults = (Integer) arguments.get("max_results");

        try {
            Integer userId = Integer.parseInt(userIdStr);
            User user = userRepository.findById(userId).orElse(null);

            if (user == null) {
                return "Error: User not found in database.";
            }

            return googleWorkspaceService.listDriveFiles(user, maxResults, query);
        } catch (Exception e) {
            e.printStackTrace();
            return "Error listing drive files: " + e.getMessage();
        }
    }
}
