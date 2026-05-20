package com.gagent.service;

import com.gagent.entity.User;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.api.client.http.InputStreamContent;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.activation.DataHandler;
import jakarta.activation.DataSource;
import jakarta.mail.util.ByteArrayDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.util.Base64;
import java.util.List;
import java.util.Collections;
import java.util.Properties;
import java.util.stream.Collectors;

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

    private Drive getDriveService(User user) throws Exception {
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

        return new Drive.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance(), credential)
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

    public String sendEmailWithAttachment(User user, String toEmail, String subject, String body, String filename, byte[] fileData) throws Exception {
        Gmail service = getGmailService(user);

        Properties props = new Properties();
        Session session = Session.getDefaultInstance(props, null);

        MimeMessage email = new MimeMessage(session);
        email.setFrom(new InternetAddress(user.getEmail()));
        email.addRecipient(jakarta.mail.Message.RecipientType.TO, new InternetAddress(toEmail));
        email.setSubject(subject);

        MimeBodyPart mimeBodyPart = new MimeBodyPart();
        mimeBodyPart.setText(body);

        MimeMultipart multipart = new MimeMultipart();
        multipart.addBodyPart(mimeBodyPart);

        MimeBodyPart attachmentPart = new MimeBodyPart();
        DataSource source = new ByteArrayDataSource(fileData, "application/octet-stream");
        attachmentPart.setDataHandler(new DataHandler(source));
        attachmentPart.setFileName(filename);
        multipart.addBodyPart(attachmentPart);

        email.setContent(multipart);

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        email.writeTo(buffer);
        byte[] bytes = buffer.toByteArray();
        String encodedEmail = Base64.getUrlEncoder().encodeToString(bytes);

        Message message = new Message();
        message.setRaw(encodedEmail);

        service.users().messages().send("me", message).execute();

        return "Email successfully sent with attachment to " + toEmail + " with subject '" + subject + "'.";
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

    public String listDriveFiles(User user, Integer maxResults, String query) throws Exception {
        Drive service = getDriveService(user);
        
        FileList result = service.files().list()
                .setPageSize(maxResults != null ? maxResults : 10)
                .setQ(query)
                .setFields("nextPageToken, files(id, name, mimeType)")
                .execute();
                
        List<File> files = result.getFiles();
        if (files == null || files.isEmpty()) {
            return "No files found.";
        }
        
        StringBuilder sb = new StringBuilder("Drive files:\n");
        for (File file : files) {
            sb.append("- [ID: ").append(file.getId()).append("] Name: ").append(file.getName())
              .append(" (").append(file.getMimeType()).append(")\n");
        }
        return sb.toString();
    }

    public String readDriveFile(User user, String fileId) throws Exception {
        Drive service = getDriveService(user);
        try (java.io.InputStream is = service.files().get(fileId).executeMediaAsInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }

    public String createDriveFile(User user, String name, String content) throws Exception {
        Drive service = getDriveService(user);
        
        File fileMetadata = new File();
        fileMetadata.setName(name);
        fileMetadata.setMimeType("text/plain");
        
        InputStreamContent mediaContent = new InputStreamContent("text/plain",
                new ByteArrayInputStream(content.getBytes()));
                
        File file = service.files().create(fileMetadata, mediaContent)
                .setFields("id")
                .execute();
                
        return "File created successfully with ID: " + file.getId();
    }

    public String writeDriveFile(User user, String fileId, String content) throws Exception {
        Drive service = getDriveService(user);
        
        File fileMetadata = new File();
        
        InputStreamContent mediaContent = new InputStreamContent("text/plain",
                new ByteArrayInputStream(content.getBytes()));
                
        File file = service.files().update(fileId, fileMetadata, mediaContent)
                .execute();
                
        return "File updated successfully: " + file.getId();
    }

    public String uploadFileToDrive(User user, String fileName, byte[] fileData) throws Exception {
        Drive service = getDriveService(user);

        File fileMetadata = new File();
        fileMetadata.setName(fileName);

        // We use a generic octet-stream. Google Drive often auto-detects or keeps it as binary.
        InputStreamContent mediaContent = new InputStreamContent("application/octet-stream",
                new ByteArrayInputStream(fileData));
                
        File file = service.files().create(fileMetadata, mediaContent)
                .setFields("id")
                .execute();
                
        return "File successfully uploaded to Google Drive with ID: " + file.getId();
    }
}
