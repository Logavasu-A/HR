package com.hrms.modules.auth.model;

public record LoginRequest(String companyCode, String username, String password) {
}
