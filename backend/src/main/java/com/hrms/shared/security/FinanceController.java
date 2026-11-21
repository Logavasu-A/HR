package com.hrms.shared.security;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sensitive")
@ConditionalOnProperty(name = "app.finance.controls", havingValue = "true")
@RequiredArgsConstructor
public class FinanceController {
    private final FinanceControls finance;

    public record Verification(@NotNull @Size(max = 72) String password) {}
    public record Decision(@NotNull Boolean approved) {}

    @PostMapping("/step-up")
    public Map<String, Boolean> verify(@Valid @RequestBody Verification verification, HttpServletRequest request) {
        return Map.of("verified", finance.stepUp(finance.actor(request), verification.password(), request));
    }

    @GetMapping("/approvals")
    public List<Map<String, Object>> approvals(HttpServletRequest request) {
        return finance.queue(finance.actor(request));
    }

    @PostMapping("/approvals/{id}/decision")
    public ResponseEntity<Void> decide(@PathVariable UUID id, @Valid @RequestBody Decision decision,
                                       HttpServletRequest request) {
        finance.decide(finance.actor(request), id, decision.approved(), request);
        return ResponseEntity.noContent().build();
    }
}
