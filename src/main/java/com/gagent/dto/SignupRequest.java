package com.gagent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import lombok.*;
import java.util.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignupRequest {
    @JsonProperty("user_name")
    private @NotBlank String userName;
    private @NotBlank @Email String email;
    private @NotBlank String password;
}
