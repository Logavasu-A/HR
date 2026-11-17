package com.company.hrms.leave.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.company.hrms.common.BaseEntity;
import com.company.hrms.common.ApprovalStatus;
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
@Table(name = "leave_application")
@Getter
@Setter
public class LeaveApplication extends BaseEntity {

    @ManyToOne
    @JoinColumn(name = "employee_id")
    private Employee employee;

    @ManyToOne
    @JoinColumn(name = "leave_type_id")
    private LeaveType leaveType;

    private LocalDate fromDate;

    private LocalDate toDate;

    private boolean halfDay;

    /** Working days in the range, holidays excluded. Calculated by the server. */
    private BigDecimal totalDays;

    private String reason;

    @Enumerated(EnumType.STRING)
    private ApprovalStatus status;

    private String decidedBy;

    private LocalDateTime decidedAt;

    private String remarks;
}
