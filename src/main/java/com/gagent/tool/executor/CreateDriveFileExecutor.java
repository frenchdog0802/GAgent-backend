package com.gagent.tool.executor;

import com.gagent.entity.User;
import com.gagent.repository.UserRepository;
import com.gagent.service.GoogleWorkspaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class CreateDriveFileExecutor implements GagentToolExecutor {

    private final UserRepository userRepository;
    private final GoogleWorkspaceService googleWorkspaceService;

    @Override
    public String getFunctionName() {
        return "create_drive_file";
    }

    @Override
    public String execute(String userIdStr, Map<String, Object> arguments) {
        String name = (String) arguments.get("name");
        String content = (String) arguments.get("content");

        try {
            Integer userId = Integer.parseInt(userIdStr);
            User user = userRepository.findById(userId).orElse(null);

            if (user == null) {
                return "Error: User not found in database.";
            }

            return googleWorkspaceService.createDriveFile(user, name, content);
        } catch (Exception e) {
            e.printStackTrace();
            return "Error creating drive file: " + e.getMessage();
        }
    }
}
