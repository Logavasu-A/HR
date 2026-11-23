package modules.attendance.repository;

import modules.attendance.model.Attendance;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AttendanceRepo extends JpaRepository<Attendance, Long> {
    Optional<Attendance> findByEmployeeIdAndAttendanceDate(Long employeeId, LocalDate date);

    List<Attendance> findByAttendanceDate(LocalDate date);

    List<Attendance> findByAttendanceDateBetweenOrderByAttendanceDateDesc(LocalDate from, LocalDate to);

    Page<Attendance> findByAttendanceDateBetweenOrderByAttendanceDateDesc(LocalDate from, LocalDate to, Pageable pageable);

    List<Attendance> findByEmployeeIdAndAttendanceDateBetweenOrderByAttendanceDate(
            Long employeeId, LocalDate from, LocalDate to);

    Page<Attendance> findByEmployeeIdAndAttendanceDateBetweenOrderByAttendanceDate(
            Long employeeId, LocalDate from, LocalDate to, Pageable pageable);
}
