package modules.recruitment.model;

import java.math.BigDecimal;
import java.time.LocalDate;

import shared.common.BaseEntity;

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
@Table(name = "job_applicant")
@Getter
@Setter
public class JobApplicant extends BaseEntity {

    public enum Status {
        APPLIED, SHORTLISTED, INTERVIEW, OFFERED, HIRED, REJECTED
    }

    @ManyToOne
    @JoinColumn(name = "job_opening_id")
    private JobOpening jobOpening;

    @NotBlank
    private String name;

    private String email;
    private String phone;
    private String source;
    private String resumeUrl;

    @Enumerated(EnumType.STRING)
    private Status status = Status.APPLIED;

    private Integer rating;

    private LocalDate appliedOn;

    private BigDecimal offeredCtc;

    private String notes;
}
