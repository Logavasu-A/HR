package com.company.hrms.performance.repository;

import com.company.hrms.performance.model.Appraisal;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface AppraisalRepo extends JpaRepository<Appraisal, Long> {
    List<Appraisal> findByEmployeeIdOrderByIdDesc(Long employeeId);
}
