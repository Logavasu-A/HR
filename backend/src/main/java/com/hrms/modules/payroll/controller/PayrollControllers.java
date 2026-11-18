package com.hrms.modules.payroll.controller;

import com.hrms.modules.payroll.model.PayrollRun;
import com.hrms.modules.payroll.model.SalaryAssignment;
import com.hrms.modules.payroll.model.SalaryComponent;
import com.hrms.modules.payroll.model.SalarySlip;
import com.hrms.modules.payroll.model.SalaryStructure;
import com.hrms.modules.payroll.model.SalaryStructureLine;
import com.hrms.modules.payroll.repository.PayrollRunRepo;
import com.hrms.modules.payroll.repository.SalaryAssignmentRepo;
import com.hrms.modules.payroll.repository.SalaryComponentRepo;
import com.hrms.modules.payroll.repository.SalarySlipRepo;
import com.hrms.modules.payroll.repository.SalaryStructureRepo;
import com.hrms.modules.payroll.service.PayrollService;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hrms.shared.common.CrudController;
import com.hrms.shared.common.ChildRecordIds;
import com.hrms.shared.common.ApiException;
import com.hrms.shared.common.PageResponse;

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

        @Override
        protected SalaryStructure doCreate(SalaryStructure body) {
            ChildRecordIds.validate(body.getLines(), List.of(), SalaryStructureLine::getId);
            return super.doCreate(body);
        }

        @Override
        protected SalaryStructure doUpdate(SalaryStructure existing, SalaryStructure body) {
            ChildRecordIds.validate(body.getLines(), existing.getLines(), SalaryStructureLine::getId);
            return super.doUpdate(existing, body);
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
        public Object list(@RequestParam(required = false) Integer page,
                           @RequestParam(defaultValue = "20") int size) {
            if (page != null) {
                return PageResponse.of(runs.findAllByOrderByPeriodYearDescPeriodMonthDesc(PageResponse.pageable(page, size)));
            }
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
        public Object slips(@PathVariable Long id,
                            @RequestParam(required = false) Integer page,
                            @RequestParam(defaultValue = "20") int size) {
            if (page != null) {
                return PageResponse.of(slips.findByRunIdOrderByEmployeeEmployeeCode(id, PageResponse.pageable(page, size)));
            }
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
