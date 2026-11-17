package modules.admin.repository;

import modules.admin.model.AppUser;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepo extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByUsernameIgnoreCase(String username);

    Optional<AppUser> findFirstByEmployeeId(Long employeeId);
}
