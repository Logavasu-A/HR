package com.company.hrms.leave.model;

import java.math.BigDecimal;

import com.company.hrms.common.BaseEntity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "leave_type")
@Getter
@Setter
public class LeaveType extends BaseEntity {

    @NotBlank
    private String name;

    private BigDecimal maxDaysPerYear;

    private boolean carryForward;

    /** Leave without pay: reduces salary and needs no allocation. */
    private boolean unpaid;

    /** Allows applying even when the balance is not enough. */
    private boolean allowNegative;
}
