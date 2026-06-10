package com.gagent.service;

import com.gagent.dto.TestResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class GitHubCiExecutor {

    private static final Pattern PR_URL_PATTERN = Pattern.compile("https://github\\.com/[^\\s]+/pull/\\d+");

    public void pushBranch(Path repoPath, String branchName, String commitMsg, String cloneUrl,
            String githubAccessToken) throws IOException, InterruptedException {
        runGit(repoPath, Duration.ofMinutes(1), "config", "user.email", "gagent-bot@users.noreply.github.com");
        runGit(repoPath, Duration.ofMinutes(1), "config", "user.name", "GAgent Bot");
        runGit(repoPath, Duration.ofMinutes(2), "checkout", "-b", branchName);
        runGit(repoPath, Duration.ofMinutes(2), "add", "-A");
        runGit(repoPath, Duration.ofMinutes(2), "commit", "-m", commitMsg);

        String authedUrl = buildAuthedCloneUrl(cloneUrl, githubAccessToken);
        TestResult pushResult = runGit(repoPath, Duration.ofMinutes(5), "push", authedUrl, branchName);
        if (pushResult.exitCode() != 0) {
            throw new IllegalStateException("git push failed: " + truncate(pushResult.output(), 2000));
        }
        log.info("Pushed branch {} to remote", branchName);
    }

    public String openPullRequest(String owner, String repo, String branchName, String title, String body,
            String githubAccessToken) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add("gh");
        command.add("pr");
        command.add("create");
        command.add("--repo");
        command.add(owner + "/" + repo);
        command.add("--head");
        command.add(branchName);
        command.add("--title");
        command.add(title);
        command.add("--body");
        command.add(body);
        command.add("--draft");

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(Path.of(System.getProperty("user.dir")).toFile());
        pb.redirectErrorStream(true);
        pb.environment().put("GH_TOKEN", githubAccessToken);

        Process process = pb.start();
        String output = new String(process.getInputStream().readAllBytes());
        boolean finished = process.waitFor(2, TimeUnit.MINUTES);
        if (!finished) {
            process.destroyForcibly();
            throw new IllegalStateException("gh pr create timed out");
        }
        if (process.exitValue() != 0) {
            throw new IllegalStateException("gh pr create failed: " + truncate(output, 2000));
        }

        Matcher matcher = PR_URL_PATTERN.matcher(output);
        if (matcher.find()) {
            return matcher.group();
        }
        throw new IllegalStateException("gh pr create succeeded but PR URL not found in output");
    }

    private TestResult runGit(Path repoPath, Duration timeout, String... args) throws IOException, InterruptedException {
        String[] command = new String[args.length + 1];
        command[0] = "git";
        System.arraycopy(args, 0, command, 1, args.length);
        return runCommand(repoPath, timeout, command);
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

    private String buildAuthedCloneUrl(String cloneUrl, String githubAccessToken) {
        if (!cloneUrl.startsWith("https://")) {
            throw new IllegalArgumentException("Only HTTPS clone URLs are supported");
        }
        return cloneUrl.replace("https://", "https://x-access-token:" + githubAccessToken + "@");
    }

    private String truncate(String value, int maxLength) {
        if (value == null) return "";
        if (value.length() <= maxLength) return value;
        return value.substring(0, maxLength) + "...";
    }
}
