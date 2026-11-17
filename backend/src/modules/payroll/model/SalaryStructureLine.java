package modules.payroll.model;

import java.math.BigDecimal;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "salary_structure_line")
@Getter
@Setter
public class SalaryStructureLine {

    public enum AmountType {
        /** A fixed monthly amount. */
        FIXED,
        /** A percentage of the employee's base salary. */
        PERCENT_OF_BASE,
        /** A percentage of gross pay; meant for deductions. */
        PERCENT_OF_GROSS
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "component_id")
    private SalaryComponent component;

    @Enumerated(EnumType.STRING)
    private AmountType amountType;

    private BigDecimal amountValue;
}
