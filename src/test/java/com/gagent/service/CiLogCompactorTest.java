package com.gagent.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CiLogCompactorTest {

    @Test
    void compactForDiagnosisKeepsTailAndErrorLines() {
        StringBuilder log = new StringBuilder();
        for (int i = 0; i < 500; i++) {
            log.append("[INFO] Downloading dependency ").append(i).append('\n');
        }
        log.append("[ERROR] Tests run: 1, Failures: 0, Errors: 1, Skipped: 0\n");
        log.append("java.lang.NullPointerException: Cannot invoke \"com.demo.payments.Config.getApiKey()\" because \"this.config\" is null\n");
        log.append("[INFO] BUILD FAILURE\n");

        String compacted = CiLogCompactor.compactForDiagnosis(log.toString(), 4_000);

        assertTrue(compacted.contains("NullPointerException"), "should retain exception from tail/error extraction");
        assertTrue(compacted.contains("BUILD FAILURE"), "should retain build failure marker");
        assertTrue(compacted.contains("Downloading dependency 499"), "should retain recent tail output");
        assertFalse(compacted.startsWith("[INFO] Downloading dependency 0"), "should not keep only the noisy head");
    }

    @Test
    void isErrorLineDetectsMavenAndJUnitFailures() {
        assertTrue(CiLogCompactor.isErrorLine("[ERROR] Tests run: 1, Failures: 0, Errors: 1, Skipped: 0"));
        assertTrue(CiLogCompactor.isErrorLine("java.lang.NullPointerException: boom"));
        assertFalse(CiLogCompactor.isErrorLine("[INFO] Downloading from central"));
    }
}
