package com.gagent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@Slf4j
public class GitHubLogFetcher {

    private static final int JOB_LOG_BUDGET = 20_000;
    private static final int TOTAL_LOG_BUDGET = 40_000;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String fetchFailureLog(String owner, String repo, long runId, String githubAccessToken) {
        try {
            boolean tokenValid = isTokenValid(githubAccessToken);

            if (!tokenValid) {
                log.warn("GitHub token invalid or expired for {}/{} — using unauthenticated API where possible. "
                        + "Reconnect GitHub in settings for full log access and PR creation.", owner, repo);
            }

            String jobsUrl = "https://api.github.com/repos/" + owner + "/" + repo
                    + "/actions/runs/" + runId + "/jobs";
            ResponseEntity<String> jobsResponse = fetchJobs(jobsUrl, githubAccessToken, tokenValid);

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
            if (!tokenValid) {
                logBuilder.append("NOTE: GitHub token is invalid or expired — step metadata only, no full logs. "
                        + "Reconnect GitHub in GAgent settings.\n\n");
            }

            List<JsonNode> orderedJobs = new ArrayList<>();
            jobs.forEach(orderedJobs::add);
            orderedJobs.sort(Comparator
                    .comparing((JsonNode job) -> !"failure".equals(job.path("conclusion").asText()))
                    .thenComparing(job -> job.path("name").asText("unknown")));

            for (JsonNode job : orderedJobs) {
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

                if (tokenValid && job.has("id")) {
                    long jobId = job.get("id").asLong();
                    String jobLog = fetchJobLog(owner, repo, jobId, githubAccessToken);
                    logBuilder.append(jobLog).append("\n");
                }
            }

            return CiLogCompactor.compactForDiagnosis(logBuilder.toString(), TOTAL_LOG_BUDGET);
        } catch (Exception e) {
            log.error("Error fetching GitHub failure log for run {}", runId, e);
            return "Error fetching GitHub logs: " + e.getMessage();
        }
    }

    private ResponseEntity<String> fetchJobs(String jobsUrl, String githubAccessToken, boolean tokenValid) {
        HttpEntity<Void> request = tokenValid
                ? authedRequest(githubAccessToken)
                : anonymousRequest();
        try {
            return restTemplate.exchange(jobsUrl, HttpMethod.GET, request, String.class);
        } catch (HttpClientErrorException.Unauthorized e) {
            return restTemplate.exchange(jobsUrl, HttpMethod.GET, anonymousRequest(), String.class);
        }
    }

    private boolean isTokenValid(String githubAccessToken) {
        if (githubAccessToken == null || githubAccessToken.isBlank()) {
            return false;
        }
        try {
            ResponseEntity<String> userResponse = restTemplate.exchange(
                    "https://api.github.com/user",
                    HttpMethod.GET,
                    authedRequest(githubAccessToken),
                    String.class);
            return userResponse.getStatusCode().is2xxSuccessful();
        } catch (HttpClientErrorException e) {
            return false;
        }
    }

    private HttpEntity<Void> authedRequest(String githubAccessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(githubAccessToken);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        return new HttpEntity<>(headers);
    }

    private HttpEntity<Void> anonymousRequest() {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        return new HttpEntity<>(headers);
    }

    private String fetchJobLog(String owner, String repo, long jobId, String githubAccessToken) {
        try {
            String logUrl = "https://api.github.com/repos/" + owner + "/" + repo
                    + "/actions/jobs/" + jobId + "/logs";
            ResponseEntity<byte[]> response = restTemplate.exchange(
                    logUrl, HttpMethod.GET, authedRequest(githubAccessToken), byte[].class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return "(log unavailable for job " + jobId + ")";
            }

            byte[] body = response.getBody();
            String rawLog = CiLogCompactor.extractLogText(body);
            return CiLogCompactor.compactForDiagnosis(rawLog, JOB_LOG_BUDGET);
        } catch (Exception e) {
            return "(log fetch failed for job " + jobId + ": " + e.getMessage() + ")";
        }
    }
}
