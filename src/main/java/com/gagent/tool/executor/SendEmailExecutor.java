package com.gagent.tool.executor;

import com.gagent.entity.User;
import com.gagent.repository.UserRepository;
import com.gagent.service.GoogleWorkspaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class SendEmailExecutor implements GagentToolExecutor {

    private final UserRepository userRepository;
    private final GoogleWorkspaceService googleWorkspaceService;

    @Override
    public String getFunctionName() {
        return "send_email";
    }

    @Override
    public String execute(String userIdStr, Map<String, Object> arguments) {
        String toEmail = (String) arguments.get("to_email");
        String subject = (String) arguments.get("subject");
        String body = (String) arguments.get("body");

        System.out.println("====== EXECUTING SEND EMAIL TOOL ======");
        System.out.println("To: " + toEmail);
        System.out.println("Subject: " + subject);
        System.out.println("=======================================");

        try {
            Integer userId = Integer.parseInt(userIdStr);
            User user = userRepository.findById(userId).orElse(null);

            if (user == null) {
                return "Error: User not found in database.";
            }

            return googleWorkspaceService.sendEmail(user, toEmail, subject, body);
        } catch (Exception e) {
            e.printStackTrace();
            return "Error sending email: " + e.getMessage();
        }
    }
}
