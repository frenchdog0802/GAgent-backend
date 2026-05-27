package com.gagent.tool.executor;

import com.gagent.config.RequestContext;
import com.gagent.entity.UserContact;
import com.gagent.repository.UserContactRepository;
import com.gagent.service.ActivityLogger;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class GetContactExecutor implements GagentTool {

    private final UserContactRepository userContactRepository;
    private final RequestContext requestContext;
    private final ActivityLogger activityLogger;

    @Tool(name = "get_contact_by_name", value = "Search Google Contacts by name or fragment. Use before send_email when the user gives a person name but no email. Prefer a distinctive substring (surname, first name, or 'First Last') over very short queries that match many people.")
    public String execute(
            @P("Name or substring to match (e.g. 'Chen', 'Alice', 'Alice Wang').") String name_query
    ) {
        System.out.println("====== EXECUTING GET CONTACT BY NAME TOOL ======");
        System.out.println("Query: " + name_query);
        System.out.println("=================================================");

        String result;
        String status = "success";
        try {
            String userIdStr = requestContext.getUserId();
            Integer userId = Integer.parseInt(userIdStr);
            List<UserContact> contacts = userContactRepository.findByUserIdAndContactNameContainingIgnoreCase(userId, name_query);

            if (contacts.isEmpty()) {
                result = "No contacts found matching: " + name_query + ". Stop retrying and ask the user for the email address directly.";
            } else {
                StringBuilder sb = new StringBuilder("Found contacts:\n");
                for (UserContact contact : contacts) {
                    sb.append("- ").append(contact.getContactName()).append(" (").append(contact.getEmailAddress())
                            .append(")");
                    if (contact.getCompany() != null && !contact.getCompany().isEmpty()) {
                        sb.append(" [").append(contact.getCompany()).append("]");
                    }
                    sb.append("\n");
                }
                result = sb.toString();
            }
        } catch (Exception e) {
            e.printStackTrace();
            result = "Error retrieving contact: " + e.getMessage();
            status = "failed";
        }
        activityLogger.logActivity("get_contact_by_name", status, result);
        return result;
    }
}
