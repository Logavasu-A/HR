package com.hrms.modules.organization.repository;

import com.hrms.modules.organization.model.Designation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DesignationRepo extends JpaRepository<Designation, Long> {
}
