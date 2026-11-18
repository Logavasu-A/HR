package com.hrms.modules.recruitment.model;

import java.time.LocalDateTime;

import com.hrms.shared.common.BaseEntity;
import com.hrms.modules.employee.model.Employee;

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
@Table(name = "interview")
@Getter
@Setter
public class Interview extends BaseEntity {

    public enum Status {
        SCHEDULED, COMPLETED, CANCELLED
    }

    public enum Mode {
        IN_PERSON, PHONE, VIDEO
    }

    @NotNull
    @ManyToOne
    @JoinColumn(name = "applicant_id")
    private JobApplicant applicant;

    private LocalDateTime scheduledAt;

    @ManyToOne
    @JoinColumn(name = "interviewer_id")
    private Employee interviewer;

    @Enumerated(EnumType.STRING)
    private Mode mode;

    @Enumerated(EnumType.STRING)
    private Status status = Status.SCHEDULED;

    private String feedback;

    private Integer rating;
}
