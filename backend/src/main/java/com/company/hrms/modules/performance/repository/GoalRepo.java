package com.company.hrms.modules.performance.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.company.hrms.modules.performance.entity.Goal;

@Repository
public interface GoalRepo extends JpaRepository<Goal, Long> {
    List<Goal> findByEmployeeIdOrderByIdDesc(Long employeeId);
}
