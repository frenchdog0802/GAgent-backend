package com.gagent.tool.executor;

import com.gagent.entity.User;
import com.gagent.repository.UserRepository;
import com.gagent.service.GoogleWorkspaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class ReadEmailExecutor implements GagentToolExecutor {

    private final UserRepository userRepository;
    private final GoogleWorkspaceService googleWorkspaceService;

    @Override
    public String getFunctionName() {
        return "read_email";
    }

    @Override
    public String execute(String userIdStr, Map<String, Object> arguments) {
        String messageId = (String) arguments.get("message_id");

        System.out.println("====== EXECUTING READ EMAIL TOOL ======");
        System.out.println("Message ID: " + messageId);
        System.out.println("=======================================");

        try {
            Integer userId = Integer.parseInt(userIdStr);
            User user = userRepository.findById(userId).orElse(null);

            if (user == null) {
                return "Error: User not found in database.";
            }

            return googleWorkspaceService.readEmail(user, messageId);
        } catch (Exception e) {
            e.printStackTrace();
            return "Error reading email: " + e.getMessage();
        }
    }
}
