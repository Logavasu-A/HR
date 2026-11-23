package modules.expense.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import shared.common.BaseEntity;
import shared.common.ApprovalStatus;
import modules.employee.model.Employee;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "expense_claim")
@Getter
@Setter
public class ExpenseClaim extends BaseEntity {

    @ManyToOne
    @JoinColumn(name = "employee_id")
    private Employee employee;

    private LocalDate claimDate;

    @Enumerated(EnumType.STRING)
    private ApprovalStatus status;

    /** Sum of the lines. Calculated by the server. */
    private BigDecimal totalAmount;

    private String remarks;

    private String decidedBy;

    private LocalDateTime decidedAt;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "claim_id")
    @OrderBy("id")
    private List<ExpenseClaimLine> lines = new ArrayList<>();
}
