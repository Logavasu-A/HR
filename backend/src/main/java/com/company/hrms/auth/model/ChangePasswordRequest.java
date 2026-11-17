package com.company.hrms.auth.model;

public record ChangePasswordRequest(String currentPassword, String newPassword) {
}
