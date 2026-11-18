package com.hrms.modules.performance.model;

import java.time.LocalDate;

import com.hrms.shared.common.BaseEntity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "appraisal_cycle")
@Getter
@Setter
public class AppraisalCycle extends BaseEntity {

    public enum Status {
        PLANNED, IN_PROGRESS, COMPLETED
    }

    @NotBlank
    private String name;

    private LocalDate startDate;

    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    private Status status = Status.PLANNED;
}
