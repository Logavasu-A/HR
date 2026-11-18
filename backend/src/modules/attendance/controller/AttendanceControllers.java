package modules.attendance.controller;

import modules.attendance.model.Attendance;
import modules.attendance.model.ShiftType;
import modules.attendance.repository.AttendanceRepo;
import modules.attendance.repository.ShiftTypeRepo;
import modules.attendance.service.AttendanceService;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import shared.common.CrudController;
import shared.common.PageResponse;

public class AttendanceControllers {

    @RestController
    @RequestMapping("/api/attendance/shifts")
    public static class Shifts extends CrudController<ShiftType> {
        public Shifts(ShiftTypeRepo repo) {
            super(repo);
        }
    }

    @RestController
    @RequestMapping("/api/attendance/records")
    public static class Records extends CrudController<Attendance> {

        private final AttendanceRepo attendance;
        private final AttendanceService service;

        public Records(AttendanceRepo repo, AttendanceService service) {
            super(repo);
            this.attendance = repo;
            this.service = service;
        }

        /** The last two months and anything already marked for the coming month. */
        @Override
        protected List<Attendance> findAll() {
            LocalDate today = LocalDate.now();
            return attendance.findByAttendanceDateBetweenOrderByAttendanceDateDesc(
                    today.minusMonths(2), today.plusMonths(1));
        }

        @Override
        protected Page<Attendance> findPage(Pageable pageable) {
            LocalDate today = LocalDate.now();
            return attendance.findByAttendanceDateBetweenOrderByAttendanceDateDesc(
                    today.minusMonths(2), today.plusMonths(1),
                    PageResponse.pageable(pageable.getPageNumber(), pageable.getPageSize()));
        }

        @Override
        protected Attendance doCreate(Attendance body) {
            return service.mark(body);
        }

        @Override
        protected Attendance doUpdate(Attendance existing, Attendance body) {
            return service.mark(body);
        }
    }

    @RestController
    @RequestMapping("/api/attendance/summary")
    public static class Summary {

        private final AttendanceService service;

        public Summary(AttendanceService service) {
            this.service = service;
        }

        @GetMapping
        public Map<String, Object> get(@RequestParam(required = false) Integer year,
                                       @RequestParam(required = false) Integer month) {
            LocalDate now = LocalDate.now();
            int y = year != null ? year : now.getYear();
            int m = month != null ? month : now.getMonthValue();
            return service.monthlySummary(y, m);
        }
    }
}
