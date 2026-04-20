package com.gagent.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RunResponse {
    private String responseMessage;
    private String status;
    private Instant timestamp;
}
