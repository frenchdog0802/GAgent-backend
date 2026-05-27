package com.gagent.tool.executor;

import com.gagent.config.RequestContext;
import com.gagent.entity.User;
import com.gagent.repository.UserRepository;
import com.gagent.service.ActivityLogger;
import com.gagent.service.GoogleWorkspaceService;
import com.gagent.service.S3Service;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SendEmailWithAttachmentExecutor implements GagentTool {

    private final UserRepository userRepository;
    private final GoogleWorkspaceService googleWorkspaceService;
    private final S3Service s3Service;
    private final RequestContext requestContext;
    private final ActivityLogger activityLogger;

    @Tool(name = "send_email_with_attachment", value = "Send an email with a file attachment via the user's Gmail. Requires an s3_file_key that the user provides after uploading a file.")
    public String execute(
            @P("RFC5322 recipient address only.") String to_email,
            @P("Email subject line.") String subject,
            @P("Plain-text body.") String body,
            @P("The S3 file key of the uploaded attachment.") String s3_file_key
    ) {
        log.info("send_email_with_attachment invoked (s3KeyPresent={})", s3_file_key != null && !s3_file_key.isEmpty());

        if (requestContext.isEmailSent()) {
            String skippedMsg = "Skipped: an email was already sent successfully in this request. Do not send again.";
            log.warn("Skipped duplicate send_email_with_attachment");
            return skippedMsg;
        }

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
                // Fetch file from S3
                byte[] fileData = s3Service.readFile(s3_file_key);

                // Extract original filename from key if possible (format: UUID_filename)
                String filename = s3_file_key.contains("_") ? s3_file_key.substring(s3_file_key.indexOf("_") + 1) : "attachment";

                result = googleWorkspaceService.sendEmailWithAttachment(user, to_email, subject, body, filename, fileData);
                if (result.startsWith("Error")) {
                    status = "failed";
                } else {
                    requestContext.setEmailSent(true);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            result = "Error sending email with attachment: " + e.getMessage();
            status = "failed";
        }
        activityLogger.logActivity("send_email_with_attachment", status, result);
        return result;
    }
}
