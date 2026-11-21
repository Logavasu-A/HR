package com.hrms.modules.platform;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("local-isolation")
@ConditionalOnProperty(name = "app.platform.admin-view.enabled", havingValue = "true")
@RequestMapping("/api/platform/workspace")
public class OwnerWorkspaceController {
    private final OwnerWorkspaces workspaces;

    public OwnerWorkspaceController(OwnerWorkspaces workspaces) {
        this.workspaces = workspaces;
    }

    public record Selection(@NotNull UUID companyId, UUID expectedWorkspaceId,
                            @NotBlank @Size(max = 300) String reason) {}

    @GetMapping
    public ResponseEntity<OwnerWorkspaces.Workspace> current(@AuthenticationPrincipal PlatformAuth.Owner owner) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(workspaces.current(owner));
    }

    @PutMapping
    public ResponseEntity<OwnerWorkspaces.Workspace> open(@AuthenticationPrincipal PlatformAuth.Owner owner,
                      @Valid @RequestBody Selection selection, HttpServletRequest request) {
        var event = describe(request);
        var workspace = workspaces.open(owner, selection.companyId(), selection.expectedWorkspaceId(),
                selection.reason(), event);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header("X-Request-Id", event.id().toString()).body(workspace);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> close(@AuthenticationPrincipal PlatformAuth.Owner owner,
                                     @PathVariable UUID id, HttpServletRequest request) {
        var event = describe(request);
        workspaces.close(owner, id, event);
        return ResponseEntity.noContent().header("X-Request-Id", event.id().toString()).build();
    }

    static OwnerWorkspaces.Request describe(HttpServletRequest request) {
        return new OwnerWorkspaces.Request(com.hrms.modules.audit.interceptor.AuditCapture.requestId(request), request.getMethod(),
                request.getRequestURI(), request.getRemoteAddr());
    }
}
