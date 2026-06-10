package com.gagent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record CiDiagnoseResult(
        @JsonProperty("root_cause") String rootCause,
        @JsonProperty("files_to_fix") List<String> filesToFix
) {}
