package modules.expense.controller;

import shared.security.CurrentUser;

import modules.expense.model.EmployeeAdvance;
import modules.expense.model.ExpenseClaim;
import modules.expense.model.ExpenseClaimLine;
import modules.expense.model.ExpenseType;
import modules.expense.repository.EmployeeAdvanceRepo;
import modules.expense.repository.ExpenseClaimRepo;
import modules.expense.repository.ExpenseTypeRepo;
import modules.expense.service.ExpenseService;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import shared.common.CrudController;
import shared.common.ApprovalStatus;
import shared.common.ApiException;
import shared.common.ChildRecordIds;

public class ExpenseControllers {

    public record Decision(String remarks) {
    }

    @RestController
    @RequestMapping("/api/expense/types")
    public static class Types extends CrudController<ExpenseType> {
        public Types(ExpenseTypeRepo repo) {
            super(repo);
        }
    }

    @RestController
    @RequestMapping("/api/expense/claims")
    public static class Claims extends CrudController<ExpenseClaim> {

        private final ExpenseService service;

        public Claims(ExpenseClaimRepo repo, ExpenseService service) {
            super(repo);
            this.service = service;
        }

        @Override
        protected ExpenseClaim doCreate(ExpenseClaim body) {
            if (body.getEmployee() == null || body.getEmployee().getId() == null) {
                throw ApiException.badRequest("Choose an employee");
            }
            return service.submit(body.getEmployee().getId(), body);
        }

        @Override
        protected ExpenseClaim doUpdate(ExpenseClaim existing, ExpenseClaim body) {
            if (existing.getStatus() != ApprovalStatus.PENDING) {
                throw ApiException.badRequest("Only pending claims can be edited");
            }
            body.setStatus(ApprovalStatus.PENDING);
            body.setDecidedAt(existing.getDecidedAt());
            body.setDecidedBy(existing.getDecidedBy());
            ChildRecordIds.validate(body.getLines(), existing.getLines(), ExpenseClaimLine::getId);
            ExpenseService.fillTotal(body);
            return repo.save(body);
        }

        @Override
        protected void doDelete(ExpenseClaim existing) {
            if (existing.getStatus() == ApprovalStatus.APPROVED || existing.getStatus() == ApprovalStatus.PAID) {
                throw ApiException.badRequest("Approved or paid claims cannot be deleted");
            }
            repo.delete(existing);
        }

        @PostMapping("/{id}/approve")
        public ExpenseClaim approve(@PathVariable Long id, @RequestBody(required = false) Decision d) {
            return service.decide(id, ApprovalStatus.APPROVED, d == null ? null : d.remarks());
        }

        @PostMapping("/{id}/reject")
        public ExpenseClaim reject(@PathVariable Long id, @RequestBody(required = false) Decision d) {
            return service.decide(id, ApprovalStatus.REJECTED, d == null ? null : d.remarks());
        }

        @PostMapping("/{id}/pay")
        public ExpenseClaim pay(@PathVariable Long id) {
            return service.decide(id, ApprovalStatus.PAID, null);
        }
    }

    @RestController
    @RequestMapping("/api/expense/advances")
    public static class Advances extends CrudController<EmployeeAdvance> {
        public Advances(EmployeeAdvanceRepo repo) {
            super(repo);
        }

        @Override
        protected EmployeeAdvance doCreate(EmployeeAdvance body) {
            body.setStatus(ApprovalStatus.PENDING);
            return repo.save(body);
        }

        @Override
        protected EmployeeAdvance doUpdate(EmployeeAdvance existing, EmployeeAdvance body) {
            body.setStatus(existing.getStatus());
            return repo.save(body);
        }

        @PostMapping("/{id}/approve")
        public EmployeeAdvance approve(@PathVariable Long id) {
            return setStatus(id, ApprovalStatus.APPROVED);
        }

        @PostMapping("/{id}/reject")
        public EmployeeAdvance reject(@PathVariable Long id) {
            return setStatus(id, ApprovalStatus.REJECTED);
        }

        @PostMapping("/{id}/pay")
        public EmployeeAdvance pay(@PathVariable Long id) {
            return setStatus(id, ApprovalStatus.PAID);
        }

        private EmployeeAdvance setStatus(Long id, ApprovalStatus status) {
            EmployeeAdvance advance = load(id);
            CurrentUser.rejectScopedSelfApproval(advance.getEmployee().getId());
            ApprovalStatus required = status == ApprovalStatus.PAID ? ApprovalStatus.APPROVED : ApprovalStatus.PENDING;
            if (advance.getStatus() != required) {
                throw ApiException.badRequest("Advance status must be " + required.name().toLowerCase(java.util.Locale.ROOT));
            }
            advance.setStatus(status);
            return repo.save(advance);
        }
    }
}
