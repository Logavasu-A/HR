package com.hrms.modules.performance.repository;

import com.hrms.modules.performance.model.AppraisalCycle;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AppraisalCycleRepo extends JpaRepository<AppraisalCycle, Long> {
}
