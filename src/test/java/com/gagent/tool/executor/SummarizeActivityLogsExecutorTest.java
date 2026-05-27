package com.gagent.tool.executor;

import com.gagent.config.RequestContext;
import com.gagent.entity.ActivityLog;
import com.gagent.repository.ActivityLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SummarizeActivityLogsExecutorTest {

    private static final ZoneId ZONE = ZoneId.systemDefault();

    @Mock
    private ActivityLogRepository activityLogRepository;

    private RequestContext requestContext;
    private SummarizeActivityLogsExecutor executor;

    @BeforeEach
    void setUp() {
        requestContext = new RequestContext();
        requestContext.setUserId("42");
        executor = new SummarizeActivityLogsExecutor(activityLogRepository, requestContext);
    }

    @Test
    void resolveDateRange_yesterday() {
        LocalDate yesterday = LocalDate.now(ZONE).minusDays(1);

        SummarizeActivityLogsExecutor.DateRange range =
                SummarizeActivityLogsExecutor.resolveDateRange("yesterday", null, null);

        assertThat(range.label()).contains("yesterday");
        assertThat(range.label()).contains(yesterday.toString());
        assertThat(range.start()).isEqualTo(yesterday.atStartOfDay(ZONE).toInstant());
        assertThat(range.end()).isEqualTo(yesterday.plusDays(1).atStartOfDay(ZONE).toInstant());
    }

    @Test
    void resolveDateRange_thisMonth() {
        LocalDate today = LocalDate.now(ZONE);
        LocalDate start = today.withDayOfMonth(1);

        SummarizeActivityLogsExecutor.DateRange range =
                SummarizeActivityLogsExecutor.resolveDateRange("this month", null, null);

        assertThat(range.label()).contains("this month");
        assertThat(range.label()).contains(start.toString());
        assertThat(range.start()).isEqualTo(start.atStartOfDay(ZONE).toInstant());
    }

    @Test
    void resolveDateRange_lastWeek_normalizesAlias() {
        SummarizeActivityLogsExecutor.DateRange range =
                SummarizeActivityLogsExecutor.resolveDateRange("lastweek", null, null);

        assertThat(range.label()).contains("last week");
    }

    @Test
    void normalizeDateRangeKey_handlesSpacesAndHyphens() {
        assertThat(SummarizeActivityLogsExecutor.normalizeDateRangeKey("last week")).isEqualTo("last_week");
        assertThat(SummarizeActivityLogsExecutor.normalizeDateRangeKey("this-month")).isEqualTo("this_month");
    }

    @Test
    void resolveDateRange_custom_requiresDates() {
        assertThat(SummarizeActivityLogsExecutor.resolveDateRange("custom", "2026-05-01", "2026-05-03").label())
                .isEqualTo("2026-05-01 to 2026-05-03");
    }

    @Test
    void resolveDateRange_custom_rejectsEndBeforeStart() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () ->
                SummarizeActivityLogsExecutor.resolveDateRange("custom", "2026-05-10", "2026-05-01"));
    }

    @Test
    void buildSummary_groupsActionsAndIncludesDetails() {
        Instant ts = LocalDate.of(2026, 5, 25).atTime(14, 30).atZone(ZONE).toInstant();
        List<ActivityLog> logs = List.of(
                ActivityLog.builder()
                        .action("send_email")
                        .status("success")
                        .command("Email John about the report")
                        .timestamp(ts)
                        .build(),
                ActivityLog.builder()
                        .action("send_email")
                        .status("success")
                        .command("Follow up with Sarah")
                        .timestamp(ts.plusSeconds(60))
                        .build(),
                ActivityLog.builder()
                        .action("create_drive_file")
                        .status("success")
                        .command("Save meeting notes")
                        .timestamp(ts.plusSeconds(120))
                        .build());

        String summary = SummarizeActivityLogsExecutor.buildSummary("yesterday (2026-05-25)", logs, true);

        assertThat(summary).contains("Total actions: 3");
        assertThat(summary).contains("2 × sent email");
        assertThat(summary).contains("1 × created Drive file");
        assertThat(summary).contains("sent email (success)");
        assertThat(summary).contains("created Drive file (success)");
    }

    @Test
    void execute_returnsEmptyMessageWhenNoLogs() {
        LocalDate yesterday = LocalDate.now(ZONE).minusDays(1);
        when(activityLogRepository.findByUserIdAndTimestampRange(
                eq("42"), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of());

        String result = executor.execute("yesterday", null, null, null);

        assertThat(result).isEqualTo("No recorded activity logs found for yesterday (" + yesterday + ").");
        verify(activityLogRepository).findByUserIdAndTimestampRange(eq("42"), any(Instant.class), any(Instant.class));
    }

    @Test
    void execute_excludesFailedWhenRequested() {
        LocalDate yesterday = LocalDate.now(ZONE).minusDays(1);
        Instant ts = yesterday.atTime(10, 0).atZone(ZONE).toInstant();
        when(activityLogRepository.findByUserIdAndTimestampRange(
                eq("42"), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(
                        ActivityLog.builder().action("send_email").status("success").timestamp(ts).build(),
                        ActivityLog.builder().action("send_email").status("failed").timestamp(ts).build()));

        String result = executor.execute("yesterday", null, null, false);

        assertThat(result).contains("Total actions: 1");
        assertThat(result).doesNotContain("failed");
    }

    @Test
    void humanReadableAction_mapsKnownActions() {
        assertThat(SummarizeActivityLogsExecutor.humanReadableAction("send_email")).isEqualTo("sent email");
        assertThat(SummarizeActivityLogsExecutor.humanReadableAction("create_drive_file"))
                .isEqualTo("created Drive file");
    }
}
