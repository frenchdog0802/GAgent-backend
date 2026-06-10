package com.gagent.service;

import com.gagent.dto.CiDiagnoseResult;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CiDiagnoseService {

    private final ChatLanguageModel chatLanguageModel;

    public List<String> diagnose(String failureLog, List<String> filePaths, Path sandboxPath) {
        String userPrompt = buildPrompt(failureLog, filePaths);

        String json = chatLanguageModel.chat(
                SystemMessage.from("""
                    You diagnose CI failures. Reply with JSON only, no markdown.
                    Format: {"root_cause":"...","files_to_fix":["path/to/file.go"]}
                    Pick the smallest set of files likely responsible for the failure.
                    Only include paths that appear in the provided file list.
                    """),
                UserMessage.from(userPrompt)
        ).aiMessage().text();

        CiDiagnoseResult result = CiJsonParser.parse(json, CiDiagnoseResult.class);
        log.info("Diagnose root cause: {}", result.rootCause());

        return validatePaths(result.filesToFix(), filePaths, sandboxPath);
    }

    public List<String> diagnoseWithTestOutput(String failureLog, List<String> filePaths, Path sandboxPath,
            String lastTestOutput) {
        String userPrompt = buildPrompt(failureLog, filePaths)
                + "\n\nPrevious fix attempt failed. Latest test output:\n"
                + lastTestOutput;

        String json = chatLanguageModel.chat(
                SystemMessage.from("""
                    You diagnose CI failures. Reply with JSON only, no markdown.
                    Format: {"root_cause":"...","files_to_fix":["path/to/file.go"]}
                    The previous fix did not pass tests — reconsider which files need changes.
                    Only include paths from the provided file list.
                    """),
                UserMessage.from(userPrompt)
        ).aiMessage().text();

        CiDiagnoseResult result = CiJsonParser.parse(json, CiDiagnoseResult.class);
        return validatePaths(result.filesToFix(), filePaths, sandboxPath);
    }

    private String buildPrompt(String failureLog, List<String> filePaths) {
        String tree = filePaths.stream().sorted().collect(Collectors.joining("\n"));
        return "CI failure log:\n" + failureLog + "\n\nRepo file paths (git ls-files):\n" + tree;
    }

    private List<String> validatePaths(List<String> filesToFix, List<String> allPaths, Path sandboxPath) {
        if (filesToFix == null || filesToFix.isEmpty()) {
            throw new IllegalStateException("AI returned no files_to_fix");
        }

        List<String> valid = new ArrayList<>();
        for (String path : filesToFix) {
            if (!allPaths.contains(path)) {
                log.warn("AI suggested path not in repo: {}", path);
                continue;
            }
            Path file = sandboxPath.resolve(path).normalize();
            if (!file.startsWith(sandboxPath.normalize()) || !Files.exists(file)) {
                log.warn("AI suggested path not found in sandbox: {}", path);
                continue;
            }
            valid.add(path);
        }

        if (valid.isEmpty()) {
            throw new IllegalStateException("None of the AI-suggested paths exist in the sandbox");
        }
        return valid;
    }
}
