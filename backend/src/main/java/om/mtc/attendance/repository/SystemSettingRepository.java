package om.mtc.attendance.repository;

import om.mtc.attendance.domain.SystemSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemSettingRepository extends JpaRepository<SystemSetting, String> {
}
