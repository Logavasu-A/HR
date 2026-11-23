package shared.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FinanceAccessStatusController {
    private final ObjectProvider<FinanceControls> controls;

    public FinanceAccessStatusController(ObjectProvider<FinanceControls> controls) {
        this.controls = controls;
    }

    @GetMapping("/api/sensitive/status")
    public ResponseEntity<Map<String, Boolean>> status(HttpServletRequest request) {
        var finance = controls.getIfAvailable();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(Map.of("approvalsEnabled", finance != null,
                        "unlocked", finance == null || finance.unlocked(request)));
    }
}
