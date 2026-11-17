package com.company.hrms.modules.performance.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.company.hrms.modules.performance.entity.AppraisalCycle;

@Repository
public interface AppraisalCycleRepo extends JpaRepository<AppraisalCycle, Long> {
}
