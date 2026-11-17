package com.company.hrms.modules.organization.entity;

import com.company.hrms.common.base.BaseEntity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "designation")
@Getter
@Setter
public class Designation extends BaseEntity {

    @NotBlank
    private String name;

    private String description;
}
