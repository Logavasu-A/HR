package com.hrms.modules.payroll.model;

import java.util.ArrayList;
import java.util.List;

import com.hrms.shared.common.BaseEntity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "salary_structure")
@Getter
@Setter
public class SalaryStructure extends BaseEntity {

    @NotBlank
    private String name;

    private boolean active = true;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "structure_id")
    @OrderBy("id")
    private List<SalaryStructureLine> lines = new ArrayList<>();
}
