package com.gagent.tool.executor;

import com.gagent.entity.User;
import com.gagent.repository.UserRepository;
import com.gagent.service.GoogleWorkspaceService;
import com.gagent.service.S3Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class UploadDriveFileExecutor implements GagentToolExecutor {

    private final UserRepository userRepository;
    private final GoogleWorkspaceService googleWorkspaceService;
    private final S3Service s3Service;

    @Override
    public String getFunctionName() {
        return "upload_drive_file_from_attachment";
    }

    @Override
    public String execute(String userIdStr, Map<String, Object> arguments) {
        String s3FileKey = (String) arguments.get("s3_file_key");
        String fileName = (String) arguments.get("file_name");

        log.info("====== EXECUTING UPLOAD DRIVE FILE FROM ATTACHMENT TOOL ======");
        log.info("S3 Key: {}", s3FileKey);
        log.info("File Name: {}", fileName);
        log.info("==============================================================");

        try {
            Integer userId = Integer.parseInt(userIdStr);
            User user = userRepository.findById(userId).orElse(null);

            if (user == null) {
                return "Error: User not found in database.";
            }

            if (s3FileKey == null || s3FileKey.isEmpty()) {
                return "Error: s3_file_key is missing.";
            }

            if (fileName == null || fileName.isEmpty()) {
                return "Error: file_name is missing.";
            }

            byte[] fileData = s3Service.readFile(s3FileKey);
            return googleWorkspaceService.uploadFileToDrive(user, fileName, fileData);
            
        } catch (Exception e) {
            log.error("Error uploading file to Drive", e);
            return "Error uploading file to Drive: " + e.getMessage();
        }
    }
}
