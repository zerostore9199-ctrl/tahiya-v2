package om.mtc.attendance.web;

import om.mtc.attendance.security.SecurityUtils;
import om.mtc.attendance.service.DashboardService;
import om.mtc.attendance.web.dto.DashboardDtos.LiveDashboard;
import om.mtc.attendance.web.dto.DashboardDtos.TrendPoint;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    private final DashboardService dashboard;

    public DashboardController(DashboardService dashboard) {
        this.dashboard = dashboard;
    }

    @GetMapping("/live")
    public LiveDashboard live() {
        return dashboard.live(SecurityUtils.currentUser());
    }

    @GetMapping("/trend")
    public List<TrendPoint> trend(@RequestParam(required = false) Long platoonId,
                                  @RequestParam(defaultValue = "7") int days) {
        return dashboard.trend(SecurityUtils.currentUser(), platoonId, days);
    }
}
