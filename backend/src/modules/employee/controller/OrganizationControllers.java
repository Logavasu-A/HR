package modules.employee.controller;

import modules.employee.model.Branch;
import modules.employee.model.Department;
import modules.employee.model.Designation;
import modules.employee.repository.BranchRepo;
import modules.employee.repository.DepartmentRepo;
import modules.employee.repository.DesignationRepo;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import shared.common.CrudController;

public class OrganizationControllers {

    @RestController
    @RequestMapping("/api/core/branches")
    public static class Branches extends CrudController<Branch> {
        public Branches(BranchRepo repo) {
            super(repo);
        }
    }

    @RestController
    @RequestMapping("/api/core/departments")
    public static class Departments extends CrudController<Department> {
        public Departments(DepartmentRepo repo) {
            super(repo);
        }
    }

    @RestController
    @RequestMapping("/api/core/designations")
    public static class Designations extends CrudController<Designation> {
        public Designations(DesignationRepo repo) {
            super(repo);
        }
    }
}
