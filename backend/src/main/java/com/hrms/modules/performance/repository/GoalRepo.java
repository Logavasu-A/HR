package com.hrms.modules.performance.repository;

import com.hrms.modules.performance.model.Goal;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GoalRepo extends JpaRepository<Goal, Long> {
    List<Goal> findByEmployeeIdOrderByIdDesc(Long employeeId);

    Page<Goal> findByEmployeeIdOrderByIdDesc(Long employeeId, Pageable pageable);
}
