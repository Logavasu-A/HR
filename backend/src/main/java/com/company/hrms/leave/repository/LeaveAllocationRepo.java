package com.company.hrms.modules.leave.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.company.hrms.modules.leave.entity.LeaveAllocation;

@Repository
public interface LeaveAllocationRepo extends JpaRepository<LeaveAllocation, Long> {
    List<LeaveAllocation> findByEmployeeId(Long employeeId);
}
