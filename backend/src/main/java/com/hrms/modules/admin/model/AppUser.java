package com.hrms.modules.admin.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.hrms.shared.common.BaseEntity;
import com.hrms.modules.employee.model.Employee;

import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "app_user")
@Getter
@Setter
public class AppUser extends BaseEntity {

    @NotBlank
    private String username;

    private String email;

    @JsonIgnore
    private String passwordHash;

    /** Plain password, accepted on create / update only and never stored or returned. */
    @Transient
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    @NotNull
    @ManyToOne
    @JoinColumn(name = "role_id")
    private Role role;

    @ManyToOne
    @JoinColumn(name = "employee_id")
    private Employee employee;

    private boolean active = true;
}
