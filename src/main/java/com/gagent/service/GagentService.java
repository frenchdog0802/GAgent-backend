package com.gagent.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gagent.dto.RunRequest;
import com.gagent.dto.RunResponse;
import com.gagent.entity.ActivityLog;
import com.gagent.entity.Message;
import com.gagent.entity.User;
import com.gagent.repository.ActivityLogRepository;
import com.gagent.repository.MessageRepository;
import com.gagent.repository.UserRepository;
import com.gagent.repository.UserContactRepository;
import com.gagent.entity.UserContact;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.gmail.Gmail;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayOutputStream;
import java.util.Properties;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class GagentService {

    @Value("${openai.api.key:}")
    private String openAiApiKey;

    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final ActivityLogRepository activityLogRepository;
    private final UserContactRepository userContactRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RunResponse processRequest(RunRequest request, String userId) {
        if (openAiApiKey == null || openAiApiKey.isEmpty()) {
            return new RunResponse(
                    "Error: OpenAI API key is not configured. Please set the OPENAI_API_KEY environment variable.",
                    "error", Instant.now());
        }

        // Save user message to database
        Message userMessage = Message.builder()
                .role("user")
                .content(request.getMessage())
                .userId(userId)
                .build();
        messageRepository.save(userMessage);

        try {
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(openAiApiKey);

            String systemPrompt = "You are an intelligent Google Workspace AI assistant. Your goal is to help users manage Gmail, Docs, Calendar, and Drive.\n"
                    + //
                    "\n" + //
                    "### Operational Rules:\n" + //
                    "1. **Identify Intent First:** Always determine the user's intent before acting. If the user is just greeting you (e.g., \"Hello,\" \"Hi\"), respond with a friendly, professional greeting and ask how you can help with their Workspace tasks.\n"
                    + //
                    "2. **Context Sensitivity:** You have access to conversation history. You may use it for context, but do NOT reuse past email addresses, subjects, or content for new actions unless explicitly told to do so.\n"
                    + //
                    "3. **Tone:** Be professional, efficient, and helpful.";

            List<Message> history = messageRepository.findByUserIdOrderByCreatedAtAsc(userId);
            List<Map<String, Object>> apiMessages = new ArrayList<>();

            // Add system prompt
            apiMessages.add(Map.of("role", "system", "content", systemPrompt));

            // Load history
            for (Message msg : history) {
                if (msg.getRole() != null && msg.getContent() != null) {
                    apiMessages.add(new HashMap<>(Map.of("role", msg.getRole(), "content", msg.getContent())));
                }
            }

            // Define the tools
            List<Map<String, Object>> tools = new ArrayList<>();
            tools.add(Map.of(
                    "type", "function",
                    "function", Map.of(
                            "name", "send_email",
                            "description", "Send an email to a specified recipient.",
                            "parameters", Map.of(
                                    "type", "object",
                                    "properties", Map.of(
                                            "to_email",
                                            Map.of("type", "string", "description", "The recipient's email address"),
                                            "subject",
                                            Map.of("type", "string", "description", "The subject of the email"),
                                            "body",
                                            Map.of("type", "string", "description", "The body/content of the email")),
                                    "required", List.of("to_email", "subject", "body")))));

            tools.add(Map.of(
                    "type", "function",
                    "function", Map.of(
                            "name", "add_contact",
                            "description", "Add a new contact to the user's address book.",
                            "parameters", Map.of(
                                    "type", "object",
                                    "properties", Map.of(
                                            "contact_name",
                                            Map.of("type", "string", "description", "The name of the contact"),
                                            "email_address",
                                            Map.of("type", "string", "description", "The email address of the contact"),
                                            "company",
                                            Map.of("type", "string", "description",
                                                    "The company of the contact (optional)"),
                                            "notes",
                                            Map.of("type", "string", "description",
                                                    "Any notes about the contact (optional)")),
                                    "required", List.of("contact_name", "email_address")))));

            tools.add(Map.of(
                    "type", "function",
                    "function", Map.of(
                            "name", "get_contact_by_name",
                            "description", "Search for a contact in the user's address book by name.",
                            "parameters", Map.of(
                                    "type", "object",
                                    "properties", Map.of(
                                            "name_query",
                                            Map.of("type", "string", "description",
                                                    "The name or partial name to search for")),
                                    "required", List.of("name_query")))));

            // ==========================================
            // STAGE 1: THE PLANNER
            // ==========================================
            List<Map<String, Object>> plannerMessages = new ArrayList<>(apiMessages);
            plannerMessages.add(Map.of(
                    "role", "user",
                    "content",
                    "You are an Architect/Planner. Analyze the conversation history and the user's latest request. Break down the actions needed into a sequential list of steps. "
                            +
                            "Respond strictly with a JSON object containing a 'steps' array of strings. Example: { \"steps\": [\"Step 1 description\", \"Step 2 description\"] }"));

            Map<String, Object> plannerRequestBody = new HashMap<>();
            plannerRequestBody.put("model", "gpt-4o-mini");
            plannerRequestBody.put("messages", plannerMessages);
            plannerRequestBody.put("response_format", Map.of("type", "json_object"));

            HttpEntity<Map<String, Object>> plannerEntity = new HttpEntity<>(plannerRequestBody, headers);
            ResponseEntity<Map> plannerResponse = restTemplate.postForEntity(
                    "https://api.openai.com/v1/chat/completions",
                    plannerEntity,
                    Map.class);

            Map<String, Object> plannerBody = plannerResponse.getBody();
            List<String> steps = new ArrayList<>();
            if (plannerBody != null && plannerBody.containsKey("choices")) {
                List<Map<String, Object>> choices = (List<Map<String, Object>>) plannerBody.get("choices");
                if (!choices.isEmpty()) {
                    Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                    String content = (String) message.get("content");
                    System.out.println("====== PLAN GENERATED ======\n" + content);

                    try {
                        Map<String, List<String>> planData = objectMapper.readValue(content,
                                new TypeReference<Map<String, List<String>>>() {
                                });
                        if (planData.containsKey("steps")) {
                            steps = planData.get("steps");
                        }
                    } catch (Exception e) {
                        System.err.println("Failed to parse plan JSON: " + e.getMessage());
                    }
                }
            }

            // Fallback if planning fails
            if (steps.isEmpty()) {
                steps.add("Analyze and fulfill the user's request directly.");
            }

            // ==========================================
            // STAGE 2: THE EXECUTOR
            // ==========================================
            for (int i = 0; i < steps.size(); i++) {
                String currentStep = steps.get(i);
                System.out.println("\n--- Executing Step " + (i + 1) + " of " + steps.size() + " ---");

                // Tell the LLM which step it is on
                apiMessages.add(Map.of(
                        "role", "user",
                        "content",
                        "[SYSTEM INSTRUCTION] You are on step " + (i + 1) + " of " + steps.size() + ": " + currentStep
                                + ". " +
                                "Please execute the tool for this step or provide your thought process. Only perform this specific step."));

                Map<String, Object> execRequestBody = new HashMap<>();
                execRequestBody.put("model", "gpt-4o-mini");
                execRequestBody.put("messages", apiMessages);
                execRequestBody.put("tools", tools);

                HttpEntity<Map<String, Object>> execEntity = new HttpEntity<>(execRequestBody, headers);
                ResponseEntity<Map> execResponse = restTemplate.postForEntity(
                        "https://api.openai.com/v1/chat/completions",
                        execEntity,
                        Map.class);

                Map<String, Object> execBody = execResponse.getBody();
                if (execBody != null && execBody.containsKey("choices")) {
                    List<Map<String, Object>> choices = (List<Map<String, Object>>) execBody.get("choices");
                    if (!choices.isEmpty()) {
                        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");

                        // Append the LLM's response to history
                        apiMessages.add(message);

                        // If the LLM decided to use a tool
                        if (message.containsKey("tool_calls") && message.get("tool_calls") != null) {
                            List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) message.get("tool_calls");

                            for (Map<String, Object> toolCall : toolCalls) {
                                String toolCallId = (String) toolCall.get("id");
                                Map<String, Object> function = (Map<String, Object>) toolCall.get("function");
                                String functionName = (String) function.get("name");
                                String argumentsJson = (String) function.get("arguments");

                                String result = "";
                                String status = "success";
                                String errorMsg = null;
                                String action = "Unknown Action";
                                String toolType = "unknown";

                                // use interface
                                if ("send_email".equals(functionName)) {
                                    action = "Send Email";
                                    toolType = "mail";
                                    Map<String, String> args = objectMapper.readValue(argumentsJson,
                                            new TypeReference<Map<String, String>>() {
                                            });
                                    result = executeSendEmail(userId, args.get("to_email"), args.get("subject"),
                                            args.get("body"));
                                    if (result.startsWith("Error")) {
                                        status = "failed";
                                        errorMsg = result;
                                    }
                                } else if ("add_contact".equals(functionName)) {
                                    action = "Add Contact";
                                    toolType = "contact";
                                    Map<String, String> args = objectMapper.readValue(argumentsJson,
                                            new TypeReference<Map<String, String>>() {
                                            });
                                    result = executeAddContact(userId, args.get("contact_name"),
                                            args.get("email_address"),
                                            args.get("company"), args.get("notes"));
                                    if (result.startsWith("Error")) {
                                        status = "failed";
                                        errorMsg = result;
                                    }
                                } else if ("get_contact_by_name".equals(functionName)) {
                                    action = "Get Contact";
                                    toolType = "contact";
                                    Map<String, String> args = objectMapper.readValue(argumentsJson,
                                            new TypeReference<Map<String, String>>() {
                                            });
                                    result = executeGetContactByName(userId, args.get("name_query"));
                                    if (result.startsWith("Error")) {
                                        status = "failed";
                                        errorMsg = result;
                                    }
                                } else {
                                    result = "Error: Unknown function.";
                                    status = "failed";
                                    errorMsg = result;
                                }

                                ActivityLog activityLog = ActivityLog.builder()
                                        .userId(userId)
                                        .timestamp(Instant.now())
                                        .command(request.getMessage())
                                        .action(action)
                                        .tool(toolType)
                                        .status(status)
                                        .error(errorMsg)
                                        .build();
                                activityLogRepository.save(activityLog);

                                // Add tool response to messages
                                Map<String, Object> toolMessage = new HashMap<>();
                                toolMessage.put("role", "tool");
                                toolMessage.put("tool_call_id", toolCallId);
                                toolMessage.put("name", functionName);
                                toolMessage.put("content", result);
                                apiMessages.add(toolMessage);
                            }
                        }
                    }
                }
            }

            // ==========================================
            // STAGE 3: FINAL SYNTHESIS
            // ==========================================
            apiMessages.add(Map.of(
                    "role", "user",
                    "content",
                    "[SYSTEM INSTRUCTION] All planned steps have been executed. Please provide the final response to the user summarizing what was accomplished."));

            Map<String, Object> finalRequestBody = new HashMap<>();
            finalRequestBody.put("model", "gpt-4o-mini");
            finalRequestBody.put("messages", apiMessages);
            // No tools provided here so it is forced to answer with text

            HttpEntity<Map<String, Object>> finalEntity = new HttpEntity<>(finalRequestBody, headers);
            ResponseEntity<Map> finalResponse = restTemplate.postForEntity(
                    "https://api.openai.com/v1/chat/completions",
                    finalEntity,
                    Map.class);

            Map<String, Object> finalBody = finalResponse.getBody();
            if (finalBody != null && finalBody.containsKey("choices")) {
                List<Map<String, Object>> choices = (List<Map<String, Object>>) finalBody.get("choices");
                if (!choices.isEmpty()) {
                    Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                    String finalContent = (String) message.get("content");

                    // Save final assistant message to database
                    Message aiMessage = Message.builder()
                            .role("assistant")
                            .content(finalContent)
                            .userId(userId)
                            .build();
                    messageRepository.save(aiMessage);

                    return new RunResponse(finalContent, "success", Instant.now());
                }
            }

            return new RunResponse("Error: Agent reached the end but could not generate a final response.", "error",
                    Instant.now());

        } catch (Exception e) {
            e.printStackTrace();
            return new RunResponse("Error calling OpenAI API: " + e.getMessage(), "error", Instant.now());
        }
    }

    private String executeSendEmail(String userIdStr, String toEmail, String subject, String body) {
        System.out.println("====== EXECUTING SEND EMAIL TOOL ======");
        System.out.println("To: " + toEmail);
        System.out.println("Subject: " + subject);
        System.out.println("Body: " + body);
        System.out.println("=======================================");

        try {
            Integer userId = Integer.parseInt(userIdStr);
            User user = userRepository.findById(userId).orElse(null);

            if (user == null) {
                return "Error: User not found in database.";
            }
            if (user.getGoogleAccessToken() == null || user.getGoogleAccessToken().isEmpty()) {
                return "Error: Google Access Token is missing. The user must sign in with Google to send emails.";
            }

            GoogleCredential credential = new GoogleCredential().setAccessToken(user.getGoogleAccessToken());
            Gmail service = new Gmail.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance(), credential)
                    .setApplicationName("Gagent")
                    .build();

            Properties props = new Properties();
            Session session = Session.getDefaultInstance(props, null);

            MimeMessage email = new MimeMessage(session);
            email.setFrom(new InternetAddress(user.getEmail()));
            email.addRecipient(jakarta.mail.Message.RecipientType.TO, new InternetAddress(toEmail));
            email.setSubject(subject);
            email.setText(body);

            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            email.writeTo(buffer);
            byte[] bytes = buffer.toByteArray();
            String encodedEmail = Base64.getUrlEncoder().encodeToString(bytes);

            com.google.api.services.gmail.model.Message message = new com.google.api.services.gmail.model.Message();
            message.setRaw(encodedEmail);

            service.users().messages().send("me", message).execute();

            return "Email successfully sent to " + toEmail + " with subject '" + subject + "'.";
        } catch (Exception e) {
            e.printStackTrace();
            return "Error sending email: " + e.getMessage();
        }
    }

    private String executeAddContact(String userIdStr, String contactName, String emailAddress, String company,
            String notes) {
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

    private String executeGetContactByName(String userIdStr, String nameQuery) {
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
