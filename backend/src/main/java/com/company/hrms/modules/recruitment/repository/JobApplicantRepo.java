package com.company.hrms.modules.recruitment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.company.hrms.modules.recruitment.entity.JobApplicant;

@Repository
public interface JobApplicantRepo extends JpaRepository<JobApplicant, Long> {
}
