package com.gagent.tool.executor;

import com.gagent.entity.User;
import com.gagent.repository.UserRepository;
import com.gagent.service.GoogleWorkspaceService;
import com.gagent.service.S3Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class SendEmailWithAttachmentExecutor implements GagentToolExecutor {

    private final UserRepository userRepository;
    private final GoogleWorkspaceService googleWorkspaceService;
    private final S3Service s3Service;

    @Override
    public String getFunctionName() {
        return "send_email_with_attachment";
    }

    @Override
    public String execute(String userIdStr, Map<String, Object> arguments) {
        String toEmail = (String) arguments.get("to_email");
        String subject = (String) arguments.get("subject");
        String body = (String) arguments.get("body");
        String s3FileKey = (String) arguments.get("s3_file_key");

        log.info("send_email_with_attachment invoked (s3KeyPresent={})", s3FileKey != null && !s3FileKey.isEmpty());

        try {
            Integer userId = Integer.parseInt(userIdStr);
            User user = userRepository.findById(userId).orElse(null);

            if (user == null) {
                return "Error: User not found in database.";
            }

            // Fetch file from S3
            byte[] fileData = s3Service.readFile(s3FileKey);
            
            // Extract original filename from key if possible (format: UUID_filename)
            String filename = s3FileKey.contains("_") ? s3FileKey.substring(s3FileKey.indexOf("_") + 1) : "attachment";

            return googleWorkspaceService.sendEmailWithAttachment(user, toEmail, subject, body, filename, fileData);
        } catch (Exception e) {
            e.printStackTrace();
            return "Error sending email with attachment: " + e.getMessage();
        }
    }
}
