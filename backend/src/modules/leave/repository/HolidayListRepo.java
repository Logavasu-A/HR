package modules.leave.repository;

import modules.leave.model.HolidayList;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface HolidayListRepo extends JpaRepository<HolidayList, Long> {
    Optional<HolidayList> findFirstByDefaultListTrue();
}
