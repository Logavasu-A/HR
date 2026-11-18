package com.hrms.modules.payroll.model;

import java.math.BigDecimal;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "salary_slip_line")
@Getter
@Setter
public class SalarySlipLine extends com.hrms.shared.tenancy.TenantOwned {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String componentName;

    @Enumerated(EnumType.STRING)
    private SalaryComponent.Type type;

    private BigDecimal amount;

    public static SalarySlipLine of(String componentName, SalaryComponent.Type type, BigDecimal amount) {
        SalarySlipLine line = new SalarySlipLine();
        line.componentName = componentName;
        line.type = type;
        line.amount = amount;
        return line;
    }
}
