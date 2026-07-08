package com.db_search.dto;

import lombok.Data;

@Data
public class LoginRequest {
    private String username;
    private String password;
}
