package com.gagent.service;

import com.gagent.dto.TestResult;
import com.gagent.entity.CiWorkflow;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class SandboxCiExecutor {

    private final Path sandboxRoot;

    public SandboxCiExecutor(@Value("${app.ci.sandbox-root:/tmp/ci-sandbox}") String sandboxRoot) {
        this.sandboxRoot = Path.of(sandboxRoot);
    }

    public Path createSandbox(CiWorkflow workflow, String githubAccessToken) throws IOException, InterruptedException {
        if (githubAccessToken == null || githubAccessToken.isBlank()) {
            throw new IllegalStateException("GitHub account not connected");
        }

        Path workspace = sandboxRoot.resolve(String.valueOf(workflow.getId()));
        Files.createDirectories(workspace);

        String cloneUrl = workflow.getCloneUrl();
        String repoDirName = repoDirNameFromUrl(cloneUrl);
        Path repoPath = workspace.resolve(repoDirName);

        if (Files.exists(repoPath)) {
            throw new IllegalStateException("Sandbox already exists: " + repoPath);
        }

        String authedUrl = buildAuthedCloneUrl(cloneUrl, githubAccessToken);
        log.info("Cloning sandbox repo {} into {}", cloneUrl, repoPath);

        List<String> cloneCmd = new ArrayList<>();
        cloneCmd.add("git");
        cloneCmd.add("clone");
        if (workflow.getBranch() != null && !workflow.getBranch().isBlank()) {
            cloneCmd.add("--branch");
            cloneCmd.add(workflow.getBranch());
        }
        cloneCmd.add("--single-branch");
        cloneCmd.add(authedUrl);
        cloneCmd.add(repoDirName);

        TestResult cloneResult = runCommand(workspace, Duration.ofMinutes(5),
                cloneCmd.toArray(new String[0]));
        if (cloneResult.exitCode() != 0) {
            throw new IllegalStateException("git clone failed: " + truncate(cloneResult.output(), 2000));
        }

        String sha = workflow.getFailingShaFull() != null && !workflow.getFailingShaFull().isBlank()
                ? workflow.getFailingShaFull()
                : workflow.getFailingSha();
        TestResult checkoutResult = runCommand(repoPath, Duration.ofMinutes(2),
                "git", "checkout", sha);
        if (checkoutResult.exitCode() != 0) {
            throw new IllegalStateException("git checkout failed: " + truncate(checkoutResult.output(), 2000));
        }

        log.info("Sandbox ready at {} @ {}", repoPath, sha);
        return repoPath;
    }

    public List<String> listTrackedFiles(Path repoPath) throws IOException, InterruptedException {
        TestResult result = runCommand(repoPath, Duration.ofMinutes(1), "git", "ls-files");
        if (result.exitCode() != 0) {
            throw new IllegalStateException("git ls-files failed: " + truncate(result.output(), 2000));
        }

        List<String> paths = new ArrayList<>();
        for (String line : result.output().split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                paths.add(trimmed);
            }
        }
        return paths;
    }

    public TestResult runTests(Path repoPath, String testCommand) throws IOException, InterruptedException {
        return runCommand(repoPath, Duration.ofMinutes(15), "bash", "-lc", testCommand);
    }

    public void resetWorkingTree(Path repoPath) throws IOException, InterruptedException {
        TestResult result = runCommand(repoPath, Duration.ofMinutes(1), "git", "checkout", "--", ".");
        if (result.exitCode() != 0) {
            throw new IllegalStateException("git checkout -- . failed: " + truncate(result.output(), 2000));
        }
    }

    private String buildAuthedCloneUrl(String cloneUrl, String githubAccessToken) {
        if (!cloneUrl.startsWith("https://")) {
            throw new IllegalArgumentException("Only HTTPS clone URLs are supported");
        }
        return cloneUrl.replace("https://", "https://x-access-token:" + githubAccessToken + "@");
    }

    private TestResult runCommand(Path cwd, Duration timeout, String... command)
            throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(cwd.toFile());
        pb.redirectErrorStream(true);

        Process process = pb.start();
        String output = new String(process.getInputStream().readAllBytes());

        boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IllegalStateException("Command timed out: " + String.join(" ", command));
        }
        return new TestResult(process.exitValue(), output);
    }

    private String repoDirNameFromUrl(String cloneUrl) {
        String trimmed = cloneUrl.endsWith(".git") ? cloneUrl.substring(0, cloneUrl.length() - 4) : cloneUrl;
        return trimmed.substring(trimmed.lastIndexOf('/') + 1);
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }
}
