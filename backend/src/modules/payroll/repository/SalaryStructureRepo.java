package modules.payroll.repository;

import modules.payroll.model.SalaryStructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SalaryStructureRepo extends JpaRepository<SalaryStructure, Long> {
}
