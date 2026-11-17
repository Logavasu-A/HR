package modules.auth.model;

public record ChangePasswordRequest(String currentPassword, String newPassword) {
}
