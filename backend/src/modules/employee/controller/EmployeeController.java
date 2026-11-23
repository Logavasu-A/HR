package modules.employee.controller;

import modules.employee.model.Employee;
import modules.employee.repository.EmployeeRepo;
import modules.employee.service.EmployeeBankService;
import modules.employee.service.EmployeeService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import shared.common.CrudController;
import shared.common.ApiException;

@RestController
@RequestMapping("/api/core/employees")
public class EmployeeController extends CrudController<Employee> {

    private final EmployeeService service;
    private final EmployeeBankService banking;

    public EmployeeController(EmployeeRepo repo, EmployeeService service, EmployeeBankService banking) {
        super(repo);
        this.service = service;
        this.banking = banking;
    }

    @GetMapping("/{id}/bank-details")
    public ResponseEntity<EmployeeBankService.Details> bankDetails(@PathVariable long id, HttpServletRequest request) {
        var details = banking.read(id, request.getRequestURI(), request.getRemoteAddr());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(details);
    }

    @PutMapping("/{id}/bank-details")
    public ResponseEntity<Void> updateBankDetails(@PathVariable long id,
                                                   @Valid @RequestBody EmployeeBankService.Details details,
                                                   HttpServletRequest request) {
        banking.update(id, details, request.getRequestURI(), request.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }

    @Override
    protected Employee doCreate(Employee body) {
        return service.create(body);
    }

    @Override
    protected Employee doUpdate(Employee existing, Employee body) {
        if (body.getEmployeeCode() == null || body.getEmployeeCode().isBlank()) {
            body.setEmployeeCode(existing.getEmployeeCode());
        }
        if (body.getReportsTo() != null && existing.getId().equals(body.getReportsTo().getId())) {
            throw ApiException.badRequest("An employee cannot report to themselves");
        }
        if (body.getStatus() == null) {
            body.setStatus(existing.getStatus());
        }
        return repo.save(body);
    }
}
