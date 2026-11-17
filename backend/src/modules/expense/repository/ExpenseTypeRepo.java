package modules.expense.repository;

import modules.expense.model.ExpenseType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ExpenseTypeRepo extends JpaRepository<ExpenseType, Long> {
}
