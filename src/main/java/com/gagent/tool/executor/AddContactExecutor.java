package com.gagent.tool.executor;

import com.gagent.config.RequestContext;
import com.gagent.entity.User;
import com.gagent.entity.UserContact;
import com.gagent.repository.UserRepository;
import com.gagent.repository.UserContactRepository;
import com.gagent.service.ActivityLogger;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AddContactExecutor implements GagentTool {

    private final UserRepository userRepository;
    private final UserContactRepository userContactRepository;
    private final RequestContext requestContext;
    private final ActivityLogger activityLogger;

    @Tool(name = "add_contact", value = "Create a new Google Contacts entry. Use when the user wants to save someone (name + email at minimum). Do not use for sending mail or for searching existing contacts—use get_contact_by_name or send_email instead.")
    public String execute(
            @P("Full name or how the user referred to them (e.g. 'Jane Doe').") String contact_name,
            @P("Primary email for this contact.") String email_address,
            @P(value = "Organization (optional). Omit or empty if unknown.", required = false) String company,
            @P(value = "Free-text notes (optional). Omit or empty if unknown.", required = false) String notes
    ) {
        System.out.println("====== EXECUTING ADD CONTACT TOOL ======");
        System.out.println("Name: " + contact_name);
        System.out.println("Email: " + email_address);
        System.out.println("========================================");

        String result;
        String status = "success";
        try {
            String userIdStr = requestContext.getUserId();
            Integer userId = Integer.parseInt(userIdStr);
            User user = userRepository.findById(userId).orElse(null);

            if (user == null) {
                result = "Error: User not found in database.";
                status = "failed";
            } else if (userContactRepository.findByUserIdAndEmailAddress(userId, email_address).isPresent()) {
                result = "Error: Contact with email " + email_address + " already exists.";
                status = "failed";
            } else {
                UserContact contact = UserContact.builder()
                        .user(user)
                        .contactName(contact_name)
                        .emailAddress(email_address)
                        .company(company)
                        .notes(notes)
                        .build();

                userContactRepository.save(contact);
                result = "Contact '" + contact_name + "' (" + email_address + ") successfully added.";
            }
        } catch (Exception e) {
            e.printStackTrace();
            result = "Error adding contact: " + e.getMessage();
            status = "failed";
        }
        activityLogger.logActivity("add_contact", status, result);
        return result;
    }
}
