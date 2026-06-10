package com.gagent.service;

import com.gagent.dto.ApplyResult;
import com.gagent.dto.Patch;
import com.gagent.dto.TestResult;
import com.gagent.entity.CiWorkflow;
import com.gagent.entity.CiWorkflowStatus;
import com.gagent.repository.CiWorkflowRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CiRemediationService {

    private static final int MAX_ATTEMPTS = 5;

    private final CiWorkflowRepository ciWorkflowRepository;
    private final SandboxCiExecutor sandboxCiExecutor;
    private final TestCommandDetector testCommandDetector;
    private final GitHubLogFetcher gitHubLogFetcher;
    private final CiDiagnoseService ciDiagnoseService;
    private final CiPatchGeneratorService ciPatchGeneratorService;
    private final PatchApplier patchApplier;
    private final GitHubCiExecutor gitHubCiExecutor;

    @Async
    public void startAsync(Long workflowId, String githubAccessToken) {
        CiWorkflow workflow = ciWorkflowRepository.findById(workflowId).orElse(null);
        if (workflow == null) {
            log.error("CiWorkflow {} not found for remediation", workflowId);
            return;
        }
        runRemediation(workflow, githubAccessToken);
    }

    private void runRemediation(CiWorkflow workflow, String githubAccessToken) {
        try {
            if (githubAccessToken == null || githubAccessToken.isBlank()) {
                fail(workflow, "GitHub account not connected — cannot clone private repos");
                return;
            }

            updateStatus(workflow, CiWorkflowStatus.CLONING);
            Path repoPath = sandboxCiExecutor.createSandbox(workflow, githubAccessToken);
            workflow.setSandboxPath(repoPath.toString());
            ciWorkflowRepository.save(workflow);

            updateStatus(workflow, CiWorkflowStatus.LISTING_FILES);
            List<String> filePaths = sandboxCiExecutor.listTrackedFiles(repoPath);

            if (workflow.getTestCommand() == null || workflow.getTestCommand().isBlank()) {
                workflow.setTestCommand(testCommandDetector.detect(filePaths));
                ciWorkflowRepository.save(workflow);
            }

            String failureLog = fetchFailureLog(workflow, githubAccessToken);
            workflow.setFailureLog(failureLog);
            ciWorkflowRepository.save(workflow);

            updateStatus(workflow, CiWorkflowStatus.DIAGNOSING);
            List<String> filesToFix = ciDiagnoseService.diagnose(failureLog, filePaths, repoPath);

            updateStatus(workflow, CiWorkflowStatus.FIXING);
            CiRemediationContext ctx = new CiRemediationContext();
            ctx.setSandboxPath(repoPath);
            ctx.setFailureLog(failureLog);
            ctx.setTestCommand(workflow.getTestCommand());
            ctx.setFilesToFix(filesToFix);

            boolean passed = runFixLoop(workflow, ctx, githubAccessToken);

            if (passed) {
                String branchName = "gagent-fix/" + workflow.getId();
                workflow.setFixBranch(branchName);
                ciWorkflowRepository.save(workflow);

                String[] parts = workflow.getRepository().split("/", 2);
                String owner = parts[0];
                String repo = parts.length > 1 ? parts[1] : parts[0];

                gitHubCiExecutor.pushBranch(repoPath, branchName,
                        "fix: auto-remediate CI failure (workflow " + workflow.getId() + ")",
                        workflow.getCloneUrl(), githubAccessToken);

                String prUrl = gitHubCiExecutor.openPullRequest(owner, repo, branchName,
                        "fix: auto-remediate CI failure",
                        "Automated fix by GAgent for failed workflow run.\n\nPlease review and merge.",
                        githubAccessToken);

                workflow.setPrUrl(prUrl);
                updateStatus(workflow, CiWorkflowStatus.WAIT_HUMAN);
                log.info("Remediation succeeded for workflow {}, PR: {}", workflow.getId(), prUrl);
            } else {
                fail(workflow, ctx.getLastTestOutput() != null
                        ? truncate(ctx.getLastTestOutput(), 4000)
                        : "Tests did not pass after " + MAX_ATTEMPTS + " attempts");
            }
        } catch (Exception e) {
            log.error("Remediation failed for workflow {}", workflow.getId(), e);
            fail(workflow, truncate(stackTrace(e), 4000));
        }
    }

    private boolean runFixLoop(CiWorkflow workflow, CiRemediationContext ctx, String githubAccessToken) {
        Path repoPath = ctx.getSandboxPath();
        List<String> allFilePaths = null;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            ctx.setAttempt(attempt);
            workflow.setFixAttempt(attempt);
            ciWorkflowRepository.save(workflow);

            if (attempt == 1) {
                try {
                    sandboxCiExecutor.resetWorkingTree(repoPath);
                } catch (Exception e) {
                    ctx.setLastApplyError("reset failed: " + e.getMessage());
                    continue;
                }
            }

            if (attempt > 1 && ctx.getLastTestOutput() != null) {
                try {
                    if (allFilePaths == null) {
                        allFilePaths = sandboxCiExecutor.listTrackedFiles(repoPath);
                    }
                    ctx.setFilesToFix(ciDiagnoseService.diagnoseWithTestOutput(
                            ctx.getFailureLog(), allFilePaths, repoPath, ctx.getLastTestOutput()));
                } catch (Exception e) {
                    log.warn("Re-diagnose on attempt {} failed: {}", attempt, e.getMessage());
                }
            }

            Patch patch;
            try {
                patch = ciPatchGeneratorService.generate(ctx);
            } catch (Exception e) {
                ctx.setLastApplyError("patch generation failed: " + e.getMessage());
                continue;
            }

            ApplyResult apply = patchApplier.apply(repoPath, patch, ctx.getFilesToFix());
            if (!apply.success()) {
                ctx.setLastApplyError(apply.message());
                continue;
            }

            try {
                TestResult test = sandboxCiExecutor.runTests(repoPath, ctx.getTestCommand());
                ctx.setLastTestOutput(truncate(test.output(), 16_000));

                if (test.exitCode() == 0) {
                    ctx.setTestsPassed(true);
                    return true;
                }
            } catch (Exception e) {
                ctx.setLastTestOutput("test execution error: " + e.getMessage());
            }
        }
        return false;
    }

    private String fetchFailureLog(CiWorkflow workflow, String githubAccessToken) {
        if (workflow.getGithubRunId() == null) {
            return workflow.getFailureLog() != null ? workflow.getFailureLog() : "No GitHub run ID available";
        }
        String[] parts = workflow.getRepository().split("/", 2);
        if (parts.length < 2) {
            return "Invalid repository format: " + workflow.getRepository();
        }
        return gitHubLogFetcher.fetchFailureLog(parts[0], parts[1], workflow.getGithubRunId(), githubAccessToken);
    }

    private void updateStatus(CiWorkflow workflow, CiWorkflowStatus status) {
        workflow.setStatus(status);
        ciWorkflowRepository.save(workflow);
    }

    private void fail(CiWorkflow workflow, String error) {
        workflow.setStatus(CiWorkflowStatus.REMEDIATION_FAILED);
        workflow.setLastError(error);
        ciWorkflowRepository.save(workflow);
        log.warn("Workflow {} remediation failed: {}", workflow.getId(), truncate(error, 500));
    }

    private String stackTrace(Exception e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    private String truncate(String value, int max) {
        if (value == null) return "";
        if (value.length() <= max) return value;
        return value.substring(0, max) + "...";
    }
}
