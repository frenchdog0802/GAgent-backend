package com.gagent.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RunRequest {
    private String message;
    private String attachmentKey;
    private String attachmentName;
}
