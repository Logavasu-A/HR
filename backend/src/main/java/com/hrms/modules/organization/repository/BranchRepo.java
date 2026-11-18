package com.hrms.modules.organization.repository;

import com.hrms.modules.organization.model.Branch;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BranchRepo extends JpaRepository<Branch, Long> {
}
