package com.gagent.service;

import com.gagent.dto.Patch;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;

@Service
@RequiredArgsConstructor
@Slf4j
public class CiPatchGeneratorService {

    private final ChatLanguageModel chatLanguageModel;

    public Patch generate(CiRemediationContext ctx) {
        String userPrompt = buildPrompt(ctx);

        String json = chatLanguageModel.chat(
                SystemMessage.from("""
                    You fix CI failures. Reply with JSON only, no markdown.
                    Format: {"edits":[{"path":"...","old_string":"...","new_string":"..."}]}
                    old_string must match the file exactly once.
                    Only edit paths in the allowed list. Minimal change only.
                    """),
                UserMessage.from(userPrompt)
        ).aiMessage().text();

        return CiJsonParser.parse(json, Patch.class);
    }

    private String buildPrompt(CiRemediationContext ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append("Files to fix (from diagnose):\n").append(ctx.getFilesToFix()).append("\n\n");
        sb.append("Test command: ").append(ctx.getTestCommand()).append("\n\n");
        sb.append("CI failure log:\n").append(ctx.getFailureLog()).append("\n\n");

        if (ctx.getAttempt() > 1) {
            sb.append("Previous patch failed. Last test output:\n");
            sb.append(ctx.getLastTestOutput()).append("\n\n");
        }
        if (ctx.getLastApplyError() != null) {
            sb.append("Last apply error:\n").append(ctx.getLastApplyError()).append("\n\n");
        }

        for (String relPath : ctx.getFilesToFix()) {
            try {
                Path file = ctx.getSandboxPath().resolve(relPath);
                sb.append("--- ").append(relPath).append(" ---\n");
                sb.append(Files.readString(file)).append("\n");
            } catch (Exception e) {
                throw new IllegalStateException("Failed to read file for patch prompt: " + relPath, e);
            }
        }
        return sb.toString();
    }
}
