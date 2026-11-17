package com.company.hrms.payroll.controller;

import com.company.hrms.payroll.model.PayrollRun;
import com.company.hrms.payroll.model.SalaryAssignment;
import com.company.hrms.payroll.model.SalaryComponent;
import com.company.hrms.payroll.model.SalarySlip;
import com.company.hrms.payroll.model.SalaryStructure;
import com.company.hrms.payroll.repository.PayrollRunRepo;
import com.company.hrms.payroll.repository.SalaryAssignmentRepo;
import com.company.hrms.payroll.repository.SalaryComponentRepo;
import com.company.hrms.payroll.repository.SalarySlipRepo;
import com.company.hrms.payroll.repository.SalaryStructureRepo;
import com.company.hrms.payroll.service.PayrollService;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.company.hrms.common.CrudController;
import com.company.hrms.common.ApiException;

public class PayrollControllers {

    public record RunRequest(Integer year, Integer month) {
    }

    @RestController
    @RequestMapping("/api/payroll/components")
    public static class Components extends CrudController<SalaryComponent> {
        public Components(SalaryComponentRepo repo) {
            super(repo);
        }
    }

    @RestController
    @RequestMapping("/api/payroll/structures")
    public static class Structures extends CrudController<SalaryStructure> {
        public Structures(SalaryStructureRepo repo) {
            super(repo);
        }
    }

    @RestController
    @RequestMapping("/api/payroll/assignments")
    public static class Assignments extends CrudController<SalaryAssignment> {
        public Assignments(SalaryAssignmentRepo repo) {
            super(repo);
        }
    }

    @RestController
    @RequestMapping("/api/payroll/runs")
    public static class Runs {

        private final PayrollRunRepo runs;
        private final SalarySlipRepo slips;
        private final PayrollService service;

        public Runs(PayrollRunRepo runs, SalarySlipRepo slips, PayrollService service) {
            this.runs = runs;
            this.slips = slips;
            this.service = service;
        }

        @GetMapping
        public List<PayrollRun> list() {
            return runs.findAllByOrderByPeriodYearDescPeriodMonthDesc();
        }

        /** Calculates draft slips for the month; safe to repeat until the run is submitted. */
        @PostMapping
        public PayrollRun run(@RequestBody RunRequest req) {
            if (req.year() == null || req.month() == null) {
                throw ApiException.badRequest("Year and month are required");
            }
            return service.run(req.year(), req.month());
        }

        @GetMapping("/{id}/slips")
        public List<SalarySlip> slips(@PathVariable Long id) {
            return slips.findByRunIdOrderByEmployeeEmployeeCode(id);
        }

        @PostMapping("/{id}/submit")
        public PayrollRun submit(@PathVariable Long id) {
            return service.submit(id);
        }

        @DeleteMapping("/{id}")
        public void delete(@PathVariable Long id) {
            service.deleteRun(id);
        }
    }
}
