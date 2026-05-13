package com.gagent.tool.executor;

import com.gagent.entity.UserContact;
import com.gagent.repository.UserContactRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class GetContactExecutor implements GagentToolExecutor {

    private final UserContactRepository userContactRepository;

    @Override
    public String getFunctionName() {
        return "get_contact_by_name";
    }

    @Override
    public String execute(String userIdStr, Map<String, Object> arguments) {
        String nameQuery = (String) arguments.get("name_query");

        System.out.println("====== EXECUTING GET CONTACT BY NAME TOOL ======");
        System.out.println("Query: " + nameQuery);
        System.out.println("=================================================");

        try {
            Integer userId = Integer.parseInt(userIdStr);
            List<UserContact> contacts = userContactRepository.findByUserIdAndContactNameContainingIgnoreCase(userId,
                    nameQuery);

            if (contacts.isEmpty()) {
                return "No contacts found matching: " + nameQuery;
            }

            StringBuilder sb = new StringBuilder("Found contacts:\n");
            for (UserContact contact : contacts) {
                sb.append("- ").append(contact.getContactName()).append(" (").append(contact.getEmailAddress())
                        .append(")");
                if (contact.getCompany() != null && !contact.getCompany().isEmpty()) {
                    sb.append(" [").append(contact.getCompany()).append("]");
                }
                sb.append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return "Error retrieving contact: " + e.getMessage();
        }
    }
}
