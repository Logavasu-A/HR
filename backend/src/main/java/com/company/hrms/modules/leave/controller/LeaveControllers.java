package com.company.hrms.modules.leave.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.company.hrms.common.base.CrudController;
import com.company.hrms.common.enums.ApprovalStatus;
import com.company.hrms.common.exception.ApiException;
import com.company.hrms.modules.leave.entity.HolidayList;
import com.company.hrms.modules.leave.entity.LeaveAllocation;
import com.company.hrms.modules.leave.entity.LeaveApplication;
import com.company.hrms.modules.leave.entity.LeaveType;
import com.company.hrms.modules.leave.repository.HolidayListRepo;
import com.company.hrms.modules.leave.repository.LeaveTypeRepo;
import com.company.hrms.modules.leave.repository.LeaveAllocationRepo;
import com.company.hrms.modules.leave.repository.LeaveApplicationRepo;
import com.company.hrms.modules.leave.service.LeaveService;

public class LeaveControllers {

    public record Decision(String remarks) {
    }

    @RestController
    @RequestMapping("/api/leave/holiday-lists")
    public static class HolidayLists extends CrudController<HolidayList> {
        public HolidayLists(HolidayListRepo repo) {
            super(repo);
        }
    }

    @RestController
    @RequestMapping("/api/leave/types")
    public static class Types extends CrudController<LeaveType> {
        public Types(LeaveTypeRepo repo) {
            super(repo);
        }
    }

    @RestController
    @RequestMapping("/api/leave/allocations")
    public static class Allocations extends CrudController<LeaveAllocation> {
        public Allocations(LeaveAllocationRepo repo) {
            super(repo);
        }

        @Override
        protected LeaveAllocation doCreate(LeaveAllocation body) {
            check(body);
            return repo.save(body);
        }

        @Override
        protected LeaveAllocation doUpdate(LeaveAllocation existing, LeaveAllocation body) {
            check(body);
            return repo.save(body);
        }

        private static void check(LeaveAllocation a) {
            if (a.getToDate().isBefore(a.getFromDate())) {
                throw ApiException.badRequest("To date cannot be before from date");
            }
        }
    }

    @RestController
    @RequestMapping("/api/leave/applications")
    public static class Applications extends CrudController<LeaveApplication> {

        private final LeaveService service;

        public Applications(LeaveApplicationRepo repo, LeaveService service) {
            super(repo);
            this.service = service;
        }

        @Override
        protected LeaveApplication doCreate(LeaveApplication body) {
            if (body.getEmployee() == null || body.getEmployee().getId() == null) {
                throw ApiException.badRequest("Choose an employee");
            }
            return service.apply(body.getEmployee().getId(), body);
        }

        @Override
        protected LeaveApplication doUpdate(LeaveApplication existing, LeaveApplication body) {
            throw ApiException.badRequest("Leave applications cannot be edited. Cancel it and apply again.");
        }

        @Override
        protected void doDelete(LeaveApplication existing) {
            if (existing.getStatus() == ApprovalStatus.APPROVED) {
                throw ApiException.badRequest("Cancel the approved application before deleting it");
            }
            repo.delete(existing);
        }

        @PostMapping("/{id}/approve")
        public LeaveApplication approve(@PathVariable Long id, @RequestBody(required = false) Decision d) {
            return service.decide(id, true, d == null ? null : d.remarks());
        }

        @PostMapping("/{id}/reject")
        public LeaveApplication reject(@PathVariable Long id, @RequestBody(required = false) Decision d) {
            return service.decide(id, false, d == null ? null : d.remarks());
        }

        @PostMapping("/{id}/cancel")
        public LeaveApplication cancel(@PathVariable Long id) {
            return service.cancel(id, null);
        }
    }

    @RestController
    @RequestMapping("/api/leave/balances")
    public static class Balances {

        private final LeaveService service;

        public Balances(LeaveService service) {
            this.service = service;
        }

        @GetMapping
        public List<LeaveService.Balance> balances(@RequestParam Long employeeId) {
            return service.balances(employeeId, LocalDate.now());
        }
    }
}
