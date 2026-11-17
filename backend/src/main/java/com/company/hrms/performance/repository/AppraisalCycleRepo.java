package com.company.hrms.performance.repository;

import com.company.hrms.performance.model.AppraisalCycle;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface AppraisalCycleRepo extends JpaRepository<AppraisalCycle, Long> {
}
