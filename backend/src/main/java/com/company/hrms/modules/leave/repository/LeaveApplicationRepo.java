package com.company.hrms.modules.leave.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.company.hrms.common.enums.ApprovalStatus;
import com.company.hrms.modules.leave.entity.LeaveApplication;

@Repository
public interface LeaveApplicationRepo extends JpaRepository<LeaveApplication, Long> {
    List<LeaveApplication> findByEmployeeIdOrderByFromDateDesc(Long employeeId);

    List<LeaveApplication> findByEmployeeIdAndStatusIn(Long employeeId, Collection<ApprovalStatus> statuses);

    /** Applications with the given status that overlap the date range. */
    List<LeaveApplication> findByStatusAndFromDateLessThanEqualAndToDateGreaterThanEqual(
            ApprovalStatus status, LocalDate rangeEnd, LocalDate rangeStart);

    long countByStatus(ApprovalStatus status);
}
