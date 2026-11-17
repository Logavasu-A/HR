package com.company.hrms.employee.repository;

import com.company.hrms.employee.model.Employee;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface EmployeeRepo extends JpaRepository<Employee, Long> {
    boolean existsByEmployeeCode(String employeeCode);

    List<Employee> findByStatus(Employee.Status status);
}
