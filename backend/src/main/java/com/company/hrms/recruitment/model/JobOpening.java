package com.company.hrms.recruitment.model;

import java.time.LocalDate;

import com.company.hrms.common.BaseEntity;
import com.company.hrms.organization.model.Department;
import com.company.hrms.organization.model.Designation;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "job_opening")
@Getter
@Setter
public class JobOpening extends BaseEntity {

    public enum Status {
        OPEN, ON_HOLD, CLOSED
    }

    @NotBlank
    private String title;

    @ManyToOne
    @JoinColumn(name = "department_id")
    private Department department;

    @ManyToOne
    @JoinColumn(name = "designation_id")
    private Designation designation;

    private Integer vacancies;

    private String description;

    @Enumerated(EnumType.STRING)
    private Status status = Status.OPEN;

    private LocalDate postedOn;

    private LocalDate closesOn;
}
