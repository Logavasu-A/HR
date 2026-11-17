package modules.organization.controller;

import modules.organization.model.Branch;
import modules.organization.model.Department;
import modules.organization.model.Designation;
import modules.organization.repository.BranchRepo;
import modules.organization.repository.DepartmentRepo;
import modules.organization.repository.DesignationRepo;

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
