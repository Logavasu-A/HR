package com.company.hrms.modules.employee.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.company.hrms.common.exception.ApiException;
import com.company.hrms.modules.employee.entity.Employee;
import com.company.hrms.modules.employee.repository.EmployeeRepo;

import lombok.RequiredArgsConstructor;

@Service
public class EmployeeService {

    private final EmployeeRepo employees;

    public EmployeeService(EmployeeRepo employees) {
        this.employees = employees;
    }

    public Employee get(Long id) {
        return employees.findById(id).orElseThrow(() -> ApiException.notFound("Employee " + id + " not found"));
    }

    @Transactional
    public Employee create(Employee e) {
        if (e.getEmployeeCode() == null || e.getEmployeeCode().isBlank()) {
            e.setEmployeeCode(nextCode());
        } else if (employees.existsByEmployeeCode(e.getEmployeeCode())) {
            throw ApiException.badRequest("Employee code " + e.getEmployeeCode() + " is already used");
        }
        if (e.getStatus() == null) {
            e.setStatus(Employee.Status.ACTIVE);
        }
        return employees.save(e);
    }

    private String nextCode() {
        long n = employees.count() + 1;
        String code = String.format("EMP-%04d", n);
        while (employees.existsByEmployeeCode(code)) {
            n++;
            code = String.format("EMP-%04d", n);
        }
        return code;
    }
}
