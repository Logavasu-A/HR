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
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "appraisal")
@Getter
@Setter
public class Appraisal extends BaseEntity {

    public enum Status {
        DRAFT, SELF_REVIEWED, MANAGER_REVIEWED, FINALIZED
    }

    @NotNull
    @ManyToOne
    @JoinColumn(name = "employee_id")
    private Employee employee;

    @NotNull
    @ManyToOne
    @JoinColumn(name = "cycle_id")
    private AppraisalCycle cycle;

    /** Ratings are on a 1 to 5 scale. */
    private BigDecimal selfRating;

    private BigDecimal managerRating;

    /** Set when the appraisal is finalized. */
    private BigDecimal finalScore;

    private String feedback;

    @Enumerated(EnumType.STRING)
    private Status status = Status.DRAFT;
}
