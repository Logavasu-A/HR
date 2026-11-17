package com.company.hrms.performance.repository;

import com.company.hrms.performance.model.Goal;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface GoalRepo extends JpaRepository<Goal, Long> {
    List<Goal> findByEmployeeIdOrderByIdDesc(Long employeeId);
}
