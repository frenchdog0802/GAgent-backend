package com.gagent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record FileEdit(
        String path,
        @JsonProperty("old_string") String oldString,
        @JsonProperty("new_string") String newString
) {}
