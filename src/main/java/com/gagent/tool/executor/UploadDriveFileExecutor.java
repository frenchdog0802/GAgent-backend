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

@Component
@RequiredArgsConstructor
@Slf4j
public class UploadDriveFileExecutor extends LoggedGagentToolExecutor {

    private final UserRepository userRepository;
    private final GoogleWorkspaceService googleWorkspaceService;
    private final S3Service s3Service;
    private final RequestContext requestContext;
    private final ActivityLogger activityLogger;

    @Tool(name = "upload_drive_file_from_attachment", value = "Upload an attached file to Google Drive. Requires an s3_file_key that the user provides after uploading a file.")
    public String execute(
            @P("The S3 file key of the uploaded attachment.") String s3_file_key,
            @P("The desired name of the file in Google Drive.") String file_name
    ) {
        log.info("====== EXECUTING UPLOAD DRIVE FILE FROM ATTACHMENT TOOL ======");
        log.info("S3 Key: {}", s3_file_key);
        log.info("File Name: {}", file_name);
        log.info("==============================================================");

        return executeWithActivityLog(activityLogger, "upload_drive_file_from_attachment",
                "Error uploading file to Drive: ", () -> {
            String userIdStr = requestContext.getUserId();
            Integer userId = Integer.parseInt(userIdStr);
            User user = userRepository.findById(userId).orElse(null);

            if (user == null) {
                return "Error: User not found in database.";
            }
            if (s3_file_key == null || s3_file_key.isEmpty()) {
                return "Error: s3_file_key is missing.";
            }
            if (file_name == null || file_name.isEmpty()) {
                return "Error: file_name is missing.";
            }
            byte[] fileData = s3Service.readFile(s3_file_key);
            return googleWorkspaceService.uploadFileToDrive(user, file_name, fileData);
        });
    }
}
