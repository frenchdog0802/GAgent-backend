package com.gagent.dto;

import java.util.List;

public record Patch(List<FileEdit> edits) {}
