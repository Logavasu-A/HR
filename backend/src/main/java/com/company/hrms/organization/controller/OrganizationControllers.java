package com.company.hrms.modules.organization.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.company.hrms.common.base.CrudController;
import com.company.hrms.modules.organization.entity.Branch;
import com.company.hrms.modules.organization.entity.Department;
import com.company.hrms.modules.organization.entity.Designation;
import com.company.hrms.modules.organization.repository.BranchRepo;
import com.company.hrms.modules.organization.repository.DepartmentRepo;
import com.company.hrms.modules.organization.repository.DesignationRepo;

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
