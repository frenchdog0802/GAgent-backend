package com.gagent.service;

import com.gagent.dto.ApplyResult;
import com.gagent.dto.FileEdit;
import com.gagent.dto.Patch;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Service
public class PatchApplier {

    public ApplyResult apply(Path repoPath, Patch patch, List<String> filesToFix) {
        if (patch.edits() == null || patch.edits().isEmpty()) {
            return ApplyResult.fail("Patch contains no edits");
        }

        for (FileEdit edit : patch.edits()) {
            if (!filesToFix.contains(edit.path())) {
                return ApplyResult.fail("Path not in files_to_fix: " + edit.path());
            }

            Path filePath = repoPath.resolve(edit.path()).normalize();
            if (!filePath.startsWith(repoPath.normalize())) {
                return ApplyResult.fail("Path escapes sandbox: " + edit.path());
            }

            try {
                String content = Files.readString(filePath);
                int count = countOccurrences(content, edit.oldString());
                if (count == 0) {
                    return ApplyResult.fail("old_string not found in " + edit.path());
                }
                if (count > 1) {
                    return ApplyResult.fail("old_string not unique in " + edit.path());
                }

                String updated = content.replace(edit.oldString(), edit.newString());
                Files.writeString(filePath, updated);
            } catch (IOException e) {
                return ApplyResult.fail("IO error on " + edit.path() + ": " + e.getMessage());
            }
        }
        return ApplyResult.ok();
    }

    private int countOccurrences(String haystack, String needle) {
        int count = 0;
        int idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) != -1) {
            count++;
            idx += needle.length();
        }
        return count;
    }
}
