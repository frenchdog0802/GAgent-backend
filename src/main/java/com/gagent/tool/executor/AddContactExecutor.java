package com.gagent.tool.executor;

import com.gagent.entity.User;
import com.gagent.entity.UserContact;
import com.gagent.repository.UserRepository;
import com.gagent.repository.UserContactRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class AddContactExecutor implements GagentToolExecutor {

    private final UserRepository userRepository;
    private final UserContactRepository userContactRepository;

    @Override
    public String getFunctionName() {
        return "add_contact";
    }

    @Override
    public String execute(String userIdStr, Map<String, Object> arguments) {
        String contactName = (String) arguments.get("contact_name");
        String emailAddress = (String) arguments.get("email_address");
        String company = (String) arguments.get("company");
        String notes = (String) arguments.get("notes");

        System.out.println("====== EXECUTING ADD CONTACT TOOL ======");
        System.out.println("Name: " + contactName);
        System.out.println("Email: " + emailAddress);
        System.out.println("========================================");

        try {
            Integer userId = Integer.parseInt(userIdStr);
            User user = userRepository.findById(userId).orElse(null);

            if (user == null) {
                return "Error: User not found in database.";
            }

            if (userContactRepository.findByUserIdAndEmailAddress(userId, emailAddress).isPresent()) {
                return "Error: Contact with email " + emailAddress + " already exists.";
            }

            UserContact contact = UserContact.builder()
                    .user(user)
                    .contactName(contactName)
                    .emailAddress(emailAddress)
                    .company(company)
                    .notes(notes)
                    .build();

            userContactRepository.save(contact);

            return "Contact '" + contactName + "' (" + emailAddress + ") successfully added.";
        } catch (Exception e) {
            e.printStackTrace();
            return "Error adding contact: " + e.getMessage();
        }
    }
}
