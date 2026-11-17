package com.company.hrms.organization.controller;

import com.company.hrms.organization.model.Branch;
import com.company.hrms.organization.model.Department;
import com.company.hrms.organization.model.Designation;
import com.company.hrms.organization.repository.BranchRepo;
import com.company.hrms.organization.repository.DepartmentRepo;
import com.company.hrms.organization.repository.DesignationRepo;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.company.hrms.common.CrudController;

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
