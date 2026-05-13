package com.gagent.dto;

import lombok.*;
import java.util.*;

import com.fasterxml.jackson.annotation.JsonProperty;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDto {
    private Integer id;
    private String userName;
    private String email;

    @JsonProperty("isGoogleConnected")
    private boolean isGoogleConnected;

    @JsonProperty("isGoogleLogin")
    private boolean isGoogleLogin;
}
