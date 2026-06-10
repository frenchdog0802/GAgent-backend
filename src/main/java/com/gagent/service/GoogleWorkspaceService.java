package com.gagent.service;

import com.gagent.entity.User;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.ListMessagesResponse;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.util.Base64;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GoogleWorkspaceService {

    private final OAuthTokenService oauthTokenService;

    private record GoogleContext(Gmail gmail, Drive drive, GoogleCredential credential, User user) {}

    @FunctionalInterface
    private interface GoogleAction<T> {
        T apply(GoogleContext ctx) throws Exception;
    }

    private GoogleContext buildContext(User user) {
        GoogleCredential credential = oauthTokenService.createGoogleCredential(user);
        NetHttpTransport transport = new NetHttpTransport();
        GsonFactory jsonFactory = GsonFactory.getDefaultInstance();

        Gmail gmail = new Gmail.Builder(transport, jsonFactory, credential)
                .setApplicationName("Gagent")
                .build();
        Drive drive = new Drive.Builder(transport, jsonFactory, credential)
                .setApplicationName("Gagent")
                .build();

        return new GoogleContext(gmail, drive, credential, user);
    }

    private <T> T withGoogle(User user, GoogleAction<T> action) throws Exception {
        GoogleContext ctx = buildContext(user);
        try {
            T result = action.apply(ctx);
            oauthTokenService.persistGoogleTokenIfRefreshed(ctx.user(), ctx.credential());
            return result;
        } catch (Exception e) {
            oauthTokenService.handleGoogleApiError(user, e);
            throw e;
        }
    }

    public String sendEmail(User user, String toEmail, String subject, String body) throws Exception {
        return withGoogle(user, ctx -> {
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

            ctx.gmail().users().messages().send("me", message).execute();

            return "Email successfully sent to " + toEmail + " with subject '" + subject + "'.";
        });
    }

    public String sendEmailWithAttachment(User user, String toEmail, String subject, String body, String filename, byte[] fileData) throws Exception {
        return withGoogle(user, ctx -> {
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

            ctx.gmail().users().messages().send("me", message).execute();

            return "Email successfully sent with attachment to " + toEmail + " with subject '" + subject + "'.";
        });
    }

    public String listEmails(User user, Integer maxResults, String query) throws Exception {
        return withGoogle(user, ctx -> {
            ListMessagesResponse response = ctx.gmail().users().messages().list("me")
                    .setMaxResults(maxResults != null ? maxResults.longValue() : 10L)
                    .setQ(query)
                    .execute();

            List<Message> messages = response.getMessages();
            if (messages == null || messages.isEmpty()) {
                return "No emails found.";
            }

            StringBuilder sb = new StringBuilder("Recent emails:\n");
            for (Message msg : messages) {
                Message fullMsg = ctx.gmail().users().messages().get("me", msg.getId()).execute();
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
        });
    }

    public String readEmail(User user, String messageId) throws Exception {
        return withGoogle(user, ctx -> {
            Message message = ctx.gmail().users().messages().get("me", messageId).execute();

            StringBuilder sb = new StringBuilder();
            sb.append("From: ").append(getHeader(message, "From")).append("\n");
            sb.append("Subject: ").append(getHeader(message, "Subject")).append("\n");
            sb.append("Date: ").append(getHeader(message, "Date")).append("\n\n");
            sb.append("Content (Snippet): ").append(message.getSnippet()).append("\n");

            return sb.toString();
        });
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
        return withGoogle(user, ctx -> {
            FileList result = ctx.drive().files().list()
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
        });
    }

    public String readDriveFile(User user, String fileId) throws Exception {
        return withGoogle(user, ctx -> {
            try (java.io.InputStream is = ctx.drive().files().get(fileId).executeMediaAsInputStream();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        });
    }

    public String createDriveFile(User user, String name, String content) throws Exception {
        return withGoogle(user, ctx -> {
            File fileMetadata = new File();
            fileMetadata.setName(name);
            fileMetadata.setMimeType("text/plain");

            InputStreamContent mediaContent = new InputStreamContent("text/plain",
                    new ByteArrayInputStream(content.getBytes()));

            File file = ctx.drive().files().create(fileMetadata, mediaContent)
                    .setFields("id")
                    .execute();

            return "File created successfully with ID: " + file.getId();
        });
    }

    public String writeDriveFile(User user, String fileId, String content) throws Exception {
        return withGoogle(user, ctx -> {
            File fileMetadata = new File();

            InputStreamContent mediaContent = new InputStreamContent("text/plain",
                    new ByteArrayInputStream(content.getBytes()));

            File file = ctx.drive().files().update(fileId, fileMetadata, mediaContent)
                    .execute();

            return "File updated successfully: " + file.getId();
        });
    }

    public String uploadFileToDrive(User user, String fileName, byte[] fileData) throws Exception {
        return withGoogle(user, ctx -> {
            File fileMetadata = new File();
            fileMetadata.setName(fileName);

            InputStreamContent mediaContent = new InputStreamContent("application/octet-stream",
                    new ByteArrayInputStream(fileData));

            File file = ctx.drive().files().create(fileMetadata, mediaContent)
                    .setFields("id")
                    .execute();

            return "File successfully uploaded to Google Drive with ID: " + file.getId();
        });
    }
}
