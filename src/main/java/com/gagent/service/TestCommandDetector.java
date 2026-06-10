package com.gagent.service;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TestCommandDetector {

    public String detect(List<String> trackedFiles) {
        boolean hasMakefile = trackedFiles.stream().anyMatch(p -> p.equals("Makefile") || p.endsWith("/Makefile"));
        boolean hasPackageJson = trackedFiles.stream().anyMatch(p -> p.equals("package.json"));
        boolean hasPomXml = trackedFiles.stream().anyMatch(p -> p.equals("pom.xml"));
        boolean hasBuildGradle = trackedFiles.stream().anyMatch(p -> p.equals("build.gradle") || p.equals("build.gradle.kts"));
        boolean hasRequirements = trackedFiles.stream().anyMatch(p -> p.equals("requirements.txt") || p.equals("pyproject.toml"));
        boolean hasGoMod = trackedFiles.stream().anyMatch(p -> p.equals("go.mod"));

        if (hasMakefile) return "make test";
        if (hasPackageJson) return "npm test";
        if (hasPomXml) return "mvn test";
        if (hasBuildGradle) return "./gradlew test";
        if (hasRequirements) return "pytest";
        if (hasGoMod) return "go test ./...";
        return "echo 'No supported test runner detected in repo'";
    }
}
