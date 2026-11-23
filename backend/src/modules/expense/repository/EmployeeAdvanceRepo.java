package modules.expense.repository;

import modules.expense.model.EmployeeAdvance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EmployeeAdvanceRepo extends JpaRepository<EmployeeAdvance, Long> {
}
