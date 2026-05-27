package com.gagent.tool.executor;

import com.gagent.config.RequestContext;
import com.gagent.entity.ActivityLog;
import com.gagent.repository.ActivityLogRepository;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class SummarizeActivityLogsExecutor implements GagentTool {

    private static final ZoneId ZONE = ZoneId.systemDefault();
    private static final DateTimeFormatter DETAIL_TIME = DateTimeFormatter.ofPattern("HH:mm")
            .withZone(ZONE);
    private static final int MAX_DETAIL_LINES = 15;

    private final ActivityLogRepository activityLogRepository;
    private final RequestContext requestContext;

    @Tool(name = "summarize_activity_logs", value = "Summarize the authenticated user's past GAgent activity from stored activity logs. Use when the user asks what they did yesterday, today, last week, this month, recently, or over a date range. Returns counts by action type plus concise detail lines—does not scan Gmail or Drive live.")
    public String execute(
            @P("Date range keyword: yesterday, today, last_week, last_7_days, this_month, or custom. Map user phrases exactly: 'yesterday'->yesterday, 'last week'/'lastweek'->last_week, 'this month'->this_month, 'recently'->last_7_days. Do NOT use last_7_days when the user asked for yesterday.")
            String date_range,
            @P(value = "Start date in YYYY-MM-DD; required when date_range is custom.", required = false) String start_date,
            @P(value = "End date in YYYY-MM-DD; required when date_range is custom.", required = false) String end_date,
            @P(value = "Include failed actions; defaults to true.", required = false) Boolean include_failed
    ) {
        // #region agent log
        debugLog("H-A,H-B,H-D", "SummarizeActivityLogsExecutor.execute:entry", Map.of(
                "date_range", String.valueOf(date_range),
                "start_date", String.valueOf(start_date),
                "end_date", String.valueOf(end_date),
                "include_failed", String.valueOf(include_failed)));
        // #endregion
        String userId = requestContext.getUserId();
        if (userId == null || userId.isBlank()) {
            return "Error: User context not available.";
        }

        boolean includeFailed = include_failed == null || include_failed;

        try {
            DateRange range = resolveDateRange(date_range, start_date, end_date);
            // #region agent log
            debugLog("H-B,H-E", "SummarizeActivityLogsExecutor.execute:resolvedRange", Map.of(
                    "label", range.label(),
                    "start", range.start().toString(),
                    "end", range.end().toString(),
                    "today", LocalDate.now(ZONE).toString(),
                    "zone", ZONE.toString()));
            // #endregion
            List<ActivityLog> logs = activityLogRepository.findByUserIdAndTimestampRange(
                    userId, range.start(), range.end());
            // #region agent log
            debugLog("H-B,H-E", "SummarizeActivityLogsExecutor.execute:queryResult", Map.of(
                    "userId", userId,
                    "logCount", String.valueOf(logs.size()),
                    "firstTimestamp", logs.isEmpty() ? "none" : logs.get(0).getTimestamp().toString(),
                    "lastTimestamp", logs.isEmpty() ? "none" : logs.get(logs.size() - 1).getTimestamp().toString()));
            // #endregion

            if (!includeFailed) {
                logs = logs.stream()
                        .filter(log -> "success".equalsIgnoreCase(log.getStatus()))
                        .toList();
            }

            if (logs.isEmpty()) {
                String empty = "No recorded activity logs found for " + range.label() + ".";
                // #region agent log
                debugLog("H-C,H-D", "SummarizeActivityLogsExecutor.execute:empty", Map.of("result", empty));
                // #endregion
                return empty;
            }

            String summary = buildSummary(range.label(), logs, includeFailed);
            // #region agent log
            debugLog("H-A,H-E", "SummarizeActivityLogsExecutor.execute:success", Map.of(
                    "resultPreview", summary.length() > 120 ? summary.substring(0, 120) : summary));
            // #endregion
            return summary;
        } catch (IllegalArgumentException e) {
            // #region agent log
            debugLog("H-A,H-C,H-D", "SummarizeActivityLogsExecutor.execute:badDateRange", Map.of(
                    "error", e.getMessage(),
                    "date_range", String.valueOf(date_range)));
            // #endregion
            return "Error: " + e.getMessage();
        } catch (Exception e) {
            e.printStackTrace();
            return "Error summarizing activity logs: " + e.getMessage();
        }
    }

    static DateRange resolveDateRange(String dateRange, String startDate, String endDate) {
        if (dateRange == null || dateRange.isBlank()) {
            throw new IllegalArgumentException(
                    "date_range is required (yesterday, today, last_week, last_7_days, this_month, or custom).");
        }

        LocalDate today = LocalDate.now(ZONE);
        String normalized = normalizeDateRangeKey(dateRange);

        return switch (normalized) {
            case "yesterday" -> {
                LocalDate day = today.minusDays(1);
                yield new DateRange("yesterday (" + day + ")", toInstantStart(day), toInstantStart(day.plusDays(1)));
            }
            case "today" -> new DateRange("today (" + today + ")", toInstantStart(today), Instant.now());
            case "last_7_days", "last7days" -> {
                LocalDate start = today.minusDays(6);
                yield new DateRange("the last 7 days (" + start + " to " + today + ")",
                        toInstantStart(start), Instant.now());
            }
            case "last_week", "lastweek" -> {
                LocalDate startOfThisWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                LocalDate startOfLastWeek = startOfThisWeek.minusWeeks(1);
                LocalDate endOfLastWeek = startOfThisWeek.minusDays(1);
                yield new DateRange("last week (" + startOfLastWeek + " to " + endOfLastWeek + ")",
                        toInstantStart(startOfLastWeek), toInstantStart(endOfLastWeek.plusDays(1)));
            }
            case "this_month", "thismonth" -> {
                LocalDate start = today.withDayOfMonth(1);
                yield new DateRange("this month (" + start + " to " + today + ")",
                        toInstantStart(start), Instant.now());
            }
            case "custom" -> {
                if (startDate == null || startDate.isBlank() || endDate == null || endDate.isBlank()) {
                    throw new IllegalArgumentException("start_date and end_date are required when date_range is custom.");
                }
                LocalDate start = parseDate(startDate);
                LocalDate end = parseDate(endDate);
                if (end.isBefore(start)) {
                    throw new IllegalArgumentException("end_date must be on or after start_date.");
                }
                yield new DateRange(start + " to " + end,
                        toInstantStart(start), toInstantStart(end.plusDays(1)));
            }
            default -> throw new IllegalArgumentException(
                    "Unsupported date_range '" + dateRange
                            + "'. Use yesterday, today, last_week, last_7_days, this_month, or custom.");
        };
    }

    static String normalizeDateRangeKey(String dateRange) {
        return dateRange.trim().toLowerCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');
    }

    private static LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid date '" + value + "'. Use YYYY-MM-DD.");
        }
    }

    private static Instant toInstantStart(LocalDate date) {
        return date.atStartOfDay(ZONE).toInstant();
    }

    static String buildSummary(String rangeLabel, List<ActivityLog> logs, boolean includeFailed) {
        long successCount = logs.stream().filter(l -> "success".equalsIgnoreCase(l.getStatus())).count();
        long failedCount = logs.size() - successCount;

        Map<String, Long> actionCounts = logs.stream()
                .collect(Collectors.groupingBy(
                        log -> humanReadableAction(log.getAction()),
                        LinkedHashMap::new,
                        Collectors.counting()));

        StringBuilder sb = new StringBuilder();
        sb.append("Activity summary for ").append(rangeLabel).append(":\n");
        sb.append("- Total actions: ").append(logs.size());
        sb.append(" (").append(successCount).append(" succeeded");
        if (includeFailed && failedCount > 0) {
            sb.append(", ").append(failedCount).append(" failed");
        }
        sb.append(")\n");
        sb.append("- By action:\n");
        actionCounts.forEach((action, count) ->
                sb.append("  • ").append(count).append(" × ").append(action).append("\n"));

        sb.append("- Details (chronological):\n");
        logs.stream().limit(MAX_DETAIL_LINES).forEach(log -> {
            sb.append("  • [").append(DETAIL_TIME.format(log.getTimestamp())).append("] ");
            sb.append(humanReadableAction(log.getAction()));
            sb.append(" (").append(log.getStatus()).append(")");
            if (log.getCommand() != null && !log.getCommand().isBlank()) {
                String command = log.getCommand().length() > 120
                        ? log.getCommand().substring(0, 117) + "..."
                        : log.getCommand();
                sb.append(" — request: \"").append(command).append("\"");
            }
            sb.append("\n");
        });

        if (logs.size() > MAX_DETAIL_LINES) {
            sb.append("  • ... and ").append(logs.size() - MAX_DETAIL_LINES).append(" more action(s)\n");
        }

        return sb.toString().trim();
    }

    static String humanReadableAction(String action) {
        if (action == null || action.isBlank()) {
            return "unknown action";
        }
        return switch (action) {
            case "send_email" -> "sent email";
            case "send_email_with_attachment" -> "sent email with attachment";
            case "list_emails" -> "listed emails";
            case "read_email" -> "read email";
            case "create_drive_file" -> "created Drive file";
            case "write_drive_file" -> "updated Drive file";
            case "upload_drive_file_from_attachment" -> "uploaded file to Drive";
            case "list_drive_files" -> "listed Drive files";
            case "read_drive_file" -> "read Drive file";
            case "add_contact" -> "added contact";
            case "get_contact_by_name" -> "looked up contact";
            default -> action.replace('_', ' ');
        };
    }

    record DateRange(String label, Instant start, Instant end) {}

    // #region agent log
    private static void debugLog(String hypothesisId, String location, Map<String, String> data) {
        try {
            StringBuilder dataJson = new StringBuilder("{");
            data.forEach((k, v) -> dataJson.append("\"").append(k.replace("\"", "\\\"")).append("\":\"")
                    .append(String.valueOf(v).replace("\"", "\\\"")).append("\","));
            if (!data.isEmpty()) {
                dataJson.setLength(dataJson.length() - 1);
            }
            dataJson.append("}");
            String line = "{\"sessionId\":\"32c2a9\",\"timestamp\":" + System.currentTimeMillis()
                    + ",\"hypothesisId\":\"" + hypothesisId + "\",\"location\":\"" + location
                    + "\",\"message\":\"debug\",\"data\":" + dataJson + "}" + System.lineSeparator();
            Path logPath = Path.of("d:/dev/gagent/debug-32c2a9.log");
            Files.writeString(logPath, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ignored) {
        }
    }
    // #endregion
}
