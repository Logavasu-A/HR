package modules.employee.controller;

import modules.employee.model.Employee;
import modules.employee.repository.EmployeeRepo;
import modules.employee.service.EmployeeService;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import shared.common.CrudController;
import shared.common.ApiException;

@RestController
@RequestMapping("/api/core/employees")
public class EmployeeController extends CrudController<Employee> {

    private final EmployeeService service;

    public EmployeeController(EmployeeRepo repo, EmployeeService service) {
        super(repo);
        this.service = service;
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
