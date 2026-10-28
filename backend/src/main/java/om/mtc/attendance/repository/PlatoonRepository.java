package om.mtc.attendance.repository;

import om.mtc.attendance.domain.Platoon;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlatoonRepository extends JpaRepository<Platoon, Long> {
    boolean existsByCodeIgnoreCase(String code);

    List<Platoon> findAllByOrderByNameAsc();

    List<Platoon> findByActiveTrueOrderByNameAsc();
}
