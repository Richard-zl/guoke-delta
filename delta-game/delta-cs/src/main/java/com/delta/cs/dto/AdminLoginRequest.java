package com.delta.cs.dto;

import lombok.Data;

@Data
public class AdminLoginRequest {
    private String username;
    private String password;
    private String role;
}
