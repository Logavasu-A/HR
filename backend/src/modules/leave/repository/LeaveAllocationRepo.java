package com.company.hrms.leave.repository;

import com.company.hrms.leave.model.LeaveAllocation;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface LeaveAllocationRepo extends JpaRepository<LeaveAllocation, Long> {
    List<LeaveAllocation> findByEmployeeId(Long employeeId);
}
