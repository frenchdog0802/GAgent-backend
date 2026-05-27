package com.gagent.service;

import com.gagent.config.RequestContext;
import com.gagent.entity.ActivityLog;
import com.gagent.repository.ActivityLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class ActivityLogger {

    private final ActivityLogRepository activityLogRepository;
    private final RequestContext requestContext;

    public void logActivity(String action, String status, String result) {
        String userId = requestContext.getUserId();
        String query = requestContext.getUserQuery();

        String toolStr = "docs";
        if (action != null) {
            if (action.contains("email")) {
                toolStr = "mail";
            } else if (action.contains("contact")) {
                toolStr = "contacts";
            } else if (action.contains("calendar")) {
                toolStr = "calendar";
            }
        }

        activityLogRepository.save(ActivityLog.builder()
                .userId(userId)
                .timestamp(Instant.now())
                .command(query != null ? query : "")
                .action(action)
                .tool(toolStr)
                .status(status)
                .error("failed".equals(status) ? result : null)
                .build());
    }
}
