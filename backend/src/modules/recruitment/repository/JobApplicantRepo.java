package modules.recruitment.repository;

import modules.recruitment.model.JobApplicant;

import java.util.Collection;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JobApplicantRepo extends JpaRepository<JobApplicant, Long> {
    long countByStatusNotIn(Collection<JobApplicant.Status> statuses);
}
