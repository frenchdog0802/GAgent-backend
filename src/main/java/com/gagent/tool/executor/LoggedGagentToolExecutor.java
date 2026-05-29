package com.gagent.tool.executor;

import com.gagent.service.ActivityLogger;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Function;

@Slf4j
public abstract class LoggedGagentToolExecutor implements GagentTool {

    protected String executeWithActivityLog(
            ActivityLogger activityLogger,
            String action,
            String errorPrefix,
            ThrowingSupplier<String> operation
    ) {
        return executeWithActivityLog(activityLogger, action, errorPrefix, operation, this::inferStatusFromResult);
    }

    protected String executeWithActivityLog(
            ActivityLogger activityLogger,
            String action,
            String errorPrefix,
            ThrowingSupplier<String> operation,
            Function<String, String> statusResolver
    ) {
        String result;
        String status;
        try {
            result = operation.get();
            status = statusResolver.apply(result);
        } catch (Exception e) {
            log.error("Error executing {}", action, e);
            result = errorPrefix + e.getMessage();
            status = "failed";
        }
        activityLogger.logActivity(action, status, result);
        return result;
    }

    protected String inferStatusFromResult(String result) {
        return result != null && result.startsWith("Error") ? "failed" : "success";
    }

    @FunctionalInterface
    protected interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
