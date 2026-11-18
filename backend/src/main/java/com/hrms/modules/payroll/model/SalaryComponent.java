package com.hrms.modules.payroll.model;

import com.hrms.shared.common.BaseEntity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "salary_component")
@Getter
@Setter
public class SalaryComponent extends BaseEntity {

    public enum Type {
        EARNING, DEDUCTION
    }

    @NotBlank
    private String name;

    private String abbr;

    @NotNull
    @Enumerated(EnumType.STRING)
    private Type type;

    /** When true the amount is reduced in proportion to unpaid days. */
    private boolean dependsOnPaymentDays = true;
}
