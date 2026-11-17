package com.company.hrms.attendance.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

import com.company.hrms.common.BaseEntity;
import com.company.hrms.employee.model.Employee;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "attendance")
@Getter
@Setter
public class Attendance extends BaseEntity {

    public enum Status {
        PRESENT, ABSENT, HALF_DAY, ON_LEAVE, WORK_FROM_HOME
    }

    @ManyToOne
    @JoinColumn(name = "employee_id")
    private Employee employee;

    private LocalDate attendanceDate;

    @Enumerated(EnumType.STRING)
    private Status status;

    private LocalTime inTime;

    private LocalTime outTime;

    private BigDecimal workingHours;

    @ManyToOne
    @JoinColumn(name = "shift_id")
    private ShiftType shift;

    private String remarks;
}
