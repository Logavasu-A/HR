package modules.attendance.repository;

import modules.attendance.model.Attendance;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AttendanceRepo extends JpaRepository<Attendance, Long> {
    Optional<Attendance> findByEmployeeIdAndAttendanceDate(Long employeeId, LocalDate date);

    List<Attendance> findByAttendanceDate(LocalDate date);

    List<Attendance> findByAttendanceDateBetweenOrderByAttendanceDateDesc(LocalDate from, LocalDate to);

    List<Attendance> findByEmployeeIdAndAttendanceDateBetweenOrderByAttendanceDate(
            Long employeeId, LocalDate from, LocalDate to);
}
