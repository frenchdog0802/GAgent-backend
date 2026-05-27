package com.gagent.tool.executor;

import com.gagent.config.RequestContext;
import com.gagent.entity.User;
import com.gagent.repository.UserRepository;
import com.gagent.service.ActivityLogger;
import com.gagent.service.GoogleWorkspaceService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SendEmailExecutor implements GagentTool {

    private final UserRepository userRepository;
    private final GoogleWorkspaceService googleWorkspaceService;
    private final RequestContext requestContext;
    private final ActivityLogger activityLogger;

    @Tool(name = "send_email", value = "Send one email via the user's Gmail. Use only when you already have a valid recipient address. If the user names a person but gives no address, call get_contact_by_name first and use the address from its result—never guess an address.")
    public String execute(
            @P("RFC5322 recipient address only (e.g. user@domain.com). Not a display name.") String to_email,
            @P("Email subject line; use empty string only if the user explicitly wants no subject.") String subject,
            @P("Plain-text body. Preserve the user's wording for quotes or signatures they asked for.") String body
    ) {
        log.info("send_email invoked (subjectLength={})", subject != null ? subject.length() : 0);

        if (requestContext.isEmailSent()) {
            String skippedMsg = "Skipped: an email was already sent successfully in this request. Do not send again.";
            log.warn("Skipped duplicate send_email");
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
                result = googleWorkspaceService.sendEmail(user, to_email, subject, body);
                if (result.startsWith("Error")) {
                    status = "failed";
                } else {
                    requestContext.setEmailSent(true);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            result = "Error sending email: " + e.getMessage();
            status = "failed";
        }
        activityLogger.logActivity("send_email", status, result);
        return result;
    }
}
