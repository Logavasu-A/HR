package modules.recruitment.repository;

import modules.recruitment.model.JobApplicant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JobApplicantRepo extends JpaRepository<JobApplicant, Long> {
}
