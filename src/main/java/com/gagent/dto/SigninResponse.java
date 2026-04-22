package com.gagent.dto;

import lombok.*;
import java.util.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SigninResponse {
    private String token;
    private UserDto user;
}
