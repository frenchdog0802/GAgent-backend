package com.gagent.tool;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record Property(
    String type,
    String description
) {}
