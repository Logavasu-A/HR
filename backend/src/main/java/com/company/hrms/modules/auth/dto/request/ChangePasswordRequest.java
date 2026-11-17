package com.company.hrms.modules.auth.dto.request;

public record ChangePasswordRequest(String currentPassword, String newPassword) {
}
