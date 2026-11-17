package com.company.hrms.organization.model;

import com.company.hrms.common.BaseEntity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "department")
@Getter
@Setter
public class Department extends BaseEntity {

    @NotBlank
    private String name;

    private String description;
}
