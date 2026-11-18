package com.hrms.modules.performance.repository;

import com.hrms.modules.performance.model.Appraisal;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AppraisalRepo extends JpaRepository<Appraisal, Long> {
    List<Appraisal> findByEmployeeIdOrderByIdDesc(Long employeeId);

    Page<Appraisal> findByEmployeeIdOrderByIdDesc(Long employeeId, Pageable pageable);
}
