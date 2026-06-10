package com.gagent.service;

import lombok.Getter;
import lombok.Setter;

import java.nio.file.Path;
import java.util.List;

@Getter
@Setter
public class CiRemediationContext {
    private Path sandboxPath;
    private String failureLog;
    private String testCommand;
    private List<String> filesToFix;
    private String lastTestOutput;
    private String lastApplyError;
    private int attempt;
    private boolean testsPassed;
}
