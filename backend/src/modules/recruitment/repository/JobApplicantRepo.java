package modules.recruitment.repository;

import modules.recruitment.model.JobApplicant;

import java.util.Collection;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

@Repository
public interface JobApplicantRepo extends JpaRepository<JobApplicant, Long> {
    long countByStatusNotIn(Collection<JobApplicant.Status> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<JobApplicant> findOneById(Long id);
}
