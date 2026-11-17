package com.company.hrms.performance.model;

import java.math.BigDecimal;

import com.company.hrms.common.BaseEntity;
import com.company.hrms.employee.model.Employee;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "goal")
@Getter
@Setter
public class Goal extends BaseEntity {

    public enum Status {
        OPEN, IN_PROGRESS, COMPLETED, DROPPED
    }

    @NotNull
    @ManyToOne
    @JoinColumn(name = "employee_id")
    private Employee employee;

    @ManyToOne
    @JoinColumn(name = "cycle_id")
    private AppraisalCycle cycle;

    @NotBlank
    private String title;

    private String description;

    /** Share of the appraisal this goal carries, in percent. */
    private BigDecimal weightage;

    /** 0 to 100. */
    private Integer progress = 0;

    @Enumerated(EnumType.STRING)
    private Status status = Status.OPEN;
}
