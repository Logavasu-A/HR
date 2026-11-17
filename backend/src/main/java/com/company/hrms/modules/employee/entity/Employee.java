package com.company.hrms.modules.employee.entity;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.company.hrms.common.base.BaseEntity;
import com.company.hrms.modules.leave.entity.HolidayList;
import com.company.hrms.modules.organization.entity.Branch;
import com.company.hrms.modules.organization.entity.Department;
import com.company.hrms.modules.organization.entity.Designation;

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
@Table(name = "employee")
@Getter
@Setter
public class Employee extends BaseEntity {

    public enum Status {
        ACTIVE, INACTIVE, LEFT
    }

    public enum Gender {
        MALE, FEMALE, OTHER
    }

    public enum EmploymentType {
        FULL_TIME, PART_TIME, CONTRACT, INTERN
    }

    /** Generated as EMP-0001, EMP-0002, ... when left blank. */
    private String employeeCode;

    @NotBlank
    private String firstName;

    private String lastName;
    private String email;
    private String phone;

    @Enumerated(EnumType.STRING)
    private Gender gender;

    private LocalDate dateOfBirth;

    @NotNull
    private LocalDate dateOfJoining;

    private LocalDate relievingDate;

    @Enumerated(EnumType.STRING)
    private Status status = Status.ACTIVE;

    @Enumerated(EnumType.STRING)
    private EmploymentType employmentType;

    @ManyToOne
    @JoinColumn(name = "department_id")
    private Department department;

    @ManyToOne
    @JoinColumn(name = "designation_id")
    private Designation designation;

    @ManyToOne
    @JoinColumn(name = "branch_id")
    private Branch branch;

    @ManyToOne
    @JoinColumn(name = "reports_to_id")
    @JsonIgnoreProperties({"reportsTo", "holidayList", "department", "designation", "branch"})
    private Employee reportsTo;

    @ManyToOne
    @JoinColumn(name = "holiday_list_id")
    @JsonIgnoreProperties({"holidays"})
    private HolidayList holidayList;

    private String address;
    private String bankName;
    private String bankAccountNo;

    public String getFullName() {
        String first = firstName == null ? "" : firstName;
        String last = lastName == null ? "" : lastName;
        return (first + " " + last).trim();
    }
}
