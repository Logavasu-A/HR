package com.company.hrms.modules.performance.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.company.hrms.modules.performance.entity.Appraisal;

@Repository
public interface AppraisalRepo extends JpaRepository<Appraisal, Long> {
    List<Appraisal> findByEmployeeIdOrderByIdDesc(Long employeeId);
}
