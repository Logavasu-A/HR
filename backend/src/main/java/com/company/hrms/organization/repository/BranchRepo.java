package com.company.hrms.modules.organization.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.company.hrms.modules.organization.entity.Branch;

@Repository
public interface BranchRepo extends JpaRepository<Branch, Long> {
}
