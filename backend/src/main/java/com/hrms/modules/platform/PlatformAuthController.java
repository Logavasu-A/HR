package com.hrms.modules.platform;

import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.hrms.shared.common.ApiException;

@RestController
@RequestMapping("/api/platform")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.platform.enabled", havingValue = "true")
public class PlatformAuthController {
    private final PlatformAuth auth;

    public record Login(@NotBlank @Size(max = 80) String username, @NotBlank @Size(max = 72) String password) {}

    @PostMapping("/auth/login")
    public Map<String, String> login(@Valid @RequestBody Login credentials) {
        String token = auth.login(credentials.username(), credentials.password());
        if (token == null) throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        return Map.of("token", token);
    }

    @GetMapping("/session")
    public Map<String, String> session(@AuthenticationPrincipal PlatformAuth.Owner owner) {
        return Map.of("username", owner.username());
    }

    @PostMapping("/logout")
    public void logout(@AuthenticationPrincipal PlatformAuth.Owner owner) {
        auth.logout(owner);
    }
}
