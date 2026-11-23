package modules.platform;

import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.platform.enabled", havingValue = "true")
public class AdminInvitationController {
    private final CompanyOnboarding onboarding;

    public record Acceptance(@NotBlank @Size(min = 43, max = 43) String token,
                             @NotBlank @Size(min = 12, max = 72) String password) {}

    @PostMapping("/api/auth/accept-admin-invite")
    public Map<String, String> accept(@Valid @RequestBody Acceptance acceptance) {
        return Map.of("companyCode", onboarding.accept(acceptance.token(), acceptance.password()));
    }
}
