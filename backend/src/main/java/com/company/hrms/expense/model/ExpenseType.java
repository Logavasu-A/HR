package com.company.hrms.modules.expense.entity;

import com.company.hrms.common.base.BaseEntity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "expense_type")
@Getter
@Setter
public class ExpenseType extends BaseEntity {

    @NotBlank
    private String name;

    private String description;
}
