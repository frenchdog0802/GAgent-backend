package com.gagent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class GitHubLogFetcher {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String fetchFailureLog(String owner, String repo, long runId, String githubAccessToken) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(githubAccessToken);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            HttpEntity<Void> request = new HttpEntity<>(headers);

            String jobsUrl = "https://api.github.com/repos/" + owner + "/" + repo
                    + "/actions/runs/" + runId + "/jobs";
            ResponseEntity<String> jobsResponse = restTemplate.exchange(
                    jobsUrl, HttpMethod.GET, request, String.class);

            if (!jobsResponse.getStatusCode().is2xxSuccessful() || jobsResponse.getBody() == null) {
                log.warn("Failed to fetch GitHub jobs for run {}", runId);
                return "Could not fetch GitHub Actions job logs (HTTP " + jobsResponse.getStatusCode() + ")";
            }

            JsonNode root = objectMapper.readTree(jobsResponse.getBody());
            JsonNode jobs = root.get("jobs");
            if (jobs == null || !jobs.isArray() || jobs.isEmpty()) {
                return "No jobs found for workflow run " + runId;
            }

            StringBuilder logBuilder = new StringBuilder();
            for (JsonNode job : jobs) {
                String jobName = job.has("name") ? job.get("name").asText() : "unknown";
                String conclusion = job.has("conclusion") ? job.get("conclusion").asText() : "unknown";
                logBuilder.append("=== Job: ").append(jobName).append(" (").append(conclusion).append(") ===\n");

                if (job.has("steps") && job.get("steps").isArray()) {
                    for (JsonNode step : job.get("steps")) {
                        String stepName = step.has("name") ? step.get("name").asText() : "step";
                        String stepConclusion = step.has("conclusion") ? step.get("conclusion").asText() : "";
                        logBuilder.append("--- Step: ").append(stepName)
                                .append(" (").append(stepConclusion).append(") ---\n");
                    }
                }

                if (job.has("id")) {
                    long jobId = job.get("id").asLong();
                    String jobLog = fetchJobLog(owner, repo, jobId, githubAccessToken);
                    logBuilder.append(jobLog).append("\n");
                }
            }

            String result = logBuilder.toString();
            return truncate(result, 32_000);
        } catch (Exception e) {
            log.error("Error fetching GitHub failure log for run {}", runId, e);
            return "Error fetching GitHub logs: " + e.getMessage();
        }
    }

    private String fetchJobLog(String owner, String repo, long jobId, String githubAccessToken) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(githubAccessToken);
            headers.setAccept(List.of(MediaType.TEXT_PLAIN));
            HttpEntity<Void> request = new HttpEntity<>(headers);

            String logUrl = "https://api.github.com/repos/" + owner + "/" + repo
                    + "/actions/jobs/" + jobId + "/logs";
            ResponseEntity<String> response = restTemplate.exchange(
                    logUrl, HttpMethod.GET, request, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return truncate(response.getBody(), 16_000);
            }
            return "(log unavailable for job " + jobId + ")";
        } catch (Exception e) {
            return "(log fetch failed for job " + jobId + ": " + e.getMessage() + ")";
        }
    }

    private String truncate(String value, int max) {
        if (value == null) return "";
        if (value.length() <= max) return value;
        return value.substring(0, max) + "...";
    }
}
