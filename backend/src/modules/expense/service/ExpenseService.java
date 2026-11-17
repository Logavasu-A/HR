package modules.expense.service;

import modules.expense.model.ExpenseClaim;
import modules.expense.model.ExpenseClaimLine;
import modules.expense.repository.ExpenseClaimRepo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import shared.common.ApprovalStatus;
import shared.common.ApiException;
import modules.employee.model.Employee;
import modules.employee.service.EmployeeService;
import modules.notification.service.NotificationService;
import shared.security.CurrentUser;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ExpenseService {

    private final ExpenseClaimRepo claims;
    private final EmployeeService employeeService;
    private final NotificationService notifications;

    @Transactional
    public ExpenseClaim submit(Long employeeId, ExpenseClaim body) {
        Employee employee = employeeService.get(employeeId);
        body.setId(null);
        body.setEmployee(employee);
        body.setStatus(ApprovalStatus.PENDING);
        body.setDecidedBy(null);
        body.setDecidedAt(null);
        if (body.getClaimDate() == null) {
            body.setClaimDate(LocalDate.now());
        }
        fillTotal(body);
        ExpenseClaim saved = claims.save(body);
        notifications.notifyPermission("expense:approve", "Expense claim from " + employee.getFullName(),
                employee.getFullName() + " submitted an expense claim of " + saved.getTotalAmount().toPlainString() + ".");
        return saved;
    }

    @Transactional
    public ExpenseClaim decide(Long id, ApprovalStatus newStatus, String remarks) {
        ExpenseClaim claim = claims.findById(id).orElseThrow(() -> ApiException.notFound("Expense claim not found"));
        ApprovalStatus required = newStatus == ApprovalStatus.PAID ? ApprovalStatus.APPROVED : ApprovalStatus.PENDING;
        if (claim.getStatus() != required) {
            throw ApiException.badRequest("This claim is " + claim.getStatus().name().toLowerCase()
                    + "; it must be " + required.name().toLowerCase() + " for this action");
        }
        claim.setStatus(newStatus);
        claim.setDecidedBy(CurrentUser.usernameOrSystem());
        claim.setDecidedAt(LocalDateTime.now());
        if (remarks != null && !remarks.isBlank()) {
            claim.setRemarks(remarks);
        }
        claim = claims.save(claim);
        notifications.notifyEmployee(claim.getEmployee(), "Expense claim " + newStatus.name().toLowerCase(),
                "Your expense claim of " + claim.getTotalAmount().toPlainString() + " dated " + claim.getClaimDate()
                        + " is now " + newStatus.name().toLowerCase() + ".");
        return claim;
    }

    public static void fillTotal(ExpenseClaim claim) {
        if (claim.getLines() == null || claim.getLines().isEmpty()) {
            throw ApiException.badRequest("Add at least one expense line");
        }
        BigDecimal total = BigDecimal.ZERO;
        for (ExpenseClaimLine line : claim.getLines()) {
            if (line.getAmount() == null || line.getAmount().signum() <= 0) {
                throw ApiException.badRequest("Every expense line needs an amount above zero");
            }
            total = total.add(line.getAmount());
        }
        claim.setTotalAmount(total);
    }
}
