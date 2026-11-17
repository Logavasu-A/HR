package com.company.hrms.modules.payroll.entity;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.company.hrms.common.base.BaseEntity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.Setter;

/** One payroll run per calendar month. */
@Entity
@Table(name = "payroll_run")
@Getter
@Setter
public class PayrollRun extends BaseEntity {

    public enum Status {
        DRAFT, SUBMITTED
    }

    private Integer periodYear;

    private Integer periodMonth;

    @Enumerated(EnumType.STRING)
    private Status status;

    private Integer slipCount;

    private BigDecimal totalNet;

    /** Employees left out of the last calculation, with the reason. Not stored. */
    @Transient
    private List<String> skipped = new ArrayList<>();
}
