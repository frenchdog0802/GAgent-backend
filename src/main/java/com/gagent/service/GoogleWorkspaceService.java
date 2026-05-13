package com.gagent.service;

import com.gagent.entity.User;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.api.services.gmail.model.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.List;
import java.util.Properties;

@Service
public class GoogleWorkspaceService {

    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String googleClientId;

    @Value("${spring.security.oauth2.client.registration.google.client-secret}")
    private String googleClientSecret;

    private Gmail getGmailService(User user) throws Exception {
        if (user.getGoogleAccessToken() == null || user.getGoogleAccessToken().isEmpty()) {
            throw new RuntimeException("Google Access Token is missing. The user must sign in with Google.");
        }

        GoogleCredential credential = new GoogleCredential.Builder()
                .setTransport(new NetHttpTransport())
                .setJsonFactory(GsonFactory.getDefaultInstance())
                .setClientSecrets(googleClientId, googleClientSecret)
                .build()
                .setAccessToken(user.getGoogleAccessToken());

        if (user.getGoogleRefreshToken() != null && !user.getGoogleRefreshToken().isEmpty()) {
            credential.setRefreshToken(user.getGoogleRefreshToken());
        }

        return new Gmail.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance(), credential)
                .setApplicationName("Gagent")
                .build();
    }

    public String sendEmail(User user, String toEmail, String subject, String body) throws Exception {
        Gmail service = getGmailService(user);

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

        Message message = new Message();
        message.setRaw(encodedEmail);

        service.users().messages().send("me", message).execute();

        return "Email successfully sent to " + toEmail + " with subject '" + subject + "'.";
    }

    public String listEmails(User user, Integer maxResults, String query) throws Exception {
        Gmail service = getGmailService(user);
        
        ListMessagesResponse response = service.users().messages().list("me")
                .setMaxResults(maxResults != null ? maxResults.longValue() : 10L)
                .setQ(query)
                .execute();
        
        List<Message> messages = response.getMessages();
        if (messages == null || messages.isEmpty()) {
            return "No emails found.";
        }

        StringBuilder sb = new StringBuilder("Recent emails:\n");
        for (Message msg : messages) {
            Message fullMsg = service.users().messages().get("me", msg.getId()).execute();
            String subject = "No Subject";
            if (fullMsg.getPayload() != null && fullMsg.getPayload().getHeaders() != null) {
                for (var header : fullMsg.getPayload().getHeaders()) {
                    if ("Subject".equalsIgnoreCase(header.getName())) {
                        subject = header.getValue();
                        break;
                    }
                }
            }
            sb.append("- [ID: ").append(msg.getId()).append("] Subject: ").append(subject)
              .append("\n  Snippet: ").append(fullMsg.getSnippet()).append("\n");
        }
        return sb.toString();
    }

    public String readEmail(User user, String messageId) throws Exception {
        Gmail service = getGmailService(user);
        Message message = service.users().messages().get("me", messageId).execute();

        StringBuilder sb = new StringBuilder();
        sb.append("From: ").append(getHeader(message, "From")).append("\n");
        sb.append("Subject: ").append(getHeader(message, "Subject")).append("\n");
        sb.append("Date: ").append(getHeader(message, "Date")).append("\n\n");
        sb.append("Content (Snippet): ").append(message.getSnippet()).append("\n");

        return sb.toString();
    }

    private String getHeader(Message message, String name) {
        if (message.getPayload() == null || message.getPayload().getHeaders() == null)
            return "N/A";
        for (var header : message.getPayload().getHeaders()) {
            if (name.equalsIgnoreCase(header.getName()))
                return header.getValue();
        }
        return "N/A";
    }
}
