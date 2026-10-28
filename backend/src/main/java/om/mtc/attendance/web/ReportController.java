package om.mtc.attendance.web;

import om.mtc.attendance.security.SecurityUtils;
import om.mtc.attendance.service.AuditService;
import om.mtc.attendance.service.ExportService;
import om.mtc.attendance.service.ReportService;
import om.mtc.attendance.web.dto.ReportDtos.DailyReport;
import om.mtc.attendance.web.dto.ReportDtos.MonthlyReport;
import om.mtc.attendance.web.error.ApiException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;

@RestController
@RequestMapping("/api/reports")
@PreAuthorize("hasAnyRole('GENERAL_SENIOR','PLATOON_LEADER','COMPANY_AGENT')")
public class ReportController {
    private final ReportService reports;
    private final ExportService exports;
    private final AuditService audit;

    public ReportController(ReportService reports, ExportService exports, AuditService audit) {
        this.reports = reports;
        this.exports = exports;
        this.audit = audit;
    }

    @GetMapping("/monthly")
    public MonthlyReport monthly(@RequestParam(required = false) String month,
                                 @RequestParam(required = false) Long platoonId) {
        return reports.monthly(SecurityUtils.currentUser(), parseMonth(month), platoonId);
    }

    @GetMapping("/daily")
    public DailyReport daily(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                             LocalDate date, @RequestParam(required = false) Long platoonId) {
        return reports.daily(SecurityUtils.currentUser(), date, platoonId);
    }

    @GetMapping("/monthly/export")
    public ResponseEntity<byte[]> exportMonthly(@RequestParam(required = false) String month,
                                                @RequestParam(required = false) Long platoonId,
                                                @RequestParam(defaultValue = "pdf") String format) {
        String fmt = checkFormat(format);
        MonthlyReport r = reports.monthly(SecurityUtils.currentUser(), parseMonth(month), platoonId);
        byte[] body = "pdf".equals(fmt) ? exports.monthlyPdf(r) : exports.monthlyExcel(r);
        audit.log("REPORT_EXPORTED", "REPORT", "monthly-" + r.month(), "format=" + fmt + " platoon=" + platoonId);
        return file(body, "attendance-monthly-" + r.month() + "." + fmt, fmt);
    }

    @GetMapping("/daily/export")
    public ResponseEntity<byte[]> exportDaily(@RequestParam(required = false)
                                              @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                              @RequestParam(required = false) Long platoonId,
                                              @RequestParam(defaultValue = "pdf") String format) {
        String fmt = checkFormat(format);
        DailyReport r = reports.daily(SecurityUtils.currentUser(), date, platoonId);
        byte[] body = "pdf".equals(fmt) ? exports.dailyPdf(r) : exports.dailyExcel(r);
        audit.log("REPORT_EXPORTED", "REPORT", "daily-" + r.date(), "format=" + fmt + " platoon=" + platoonId);
        return file(body, "attendance-daily-" + r.date() + "." + fmt, fmt);
    }

    private static ResponseEntity<byte[]> file(byte[] body, String name, String fmt) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(name, StandardCharsets.UTF_8).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.parseMediaType(ExportService.contentType(fmt)))
                .body(body);
    }

    private static String checkFormat(String f) {
        String fmt = f == null ? "pdf" : f.toLowerCase();
        if (!ExportService.formats().contains(fmt)) {
            throw ApiException.badRequest("UNSUPPORTED_FORMAT", "الصيغ المتاحة: pdf أو xlsx");
        }
        return fmt;
    }

    private static YearMonth parseMonth(String m) {
        if (m == null || m.isBlank()) {
            return null;
        }
        try {
            return YearMonth.parse(m.trim());
        } catch (DateTimeParseException e) {
            throw ApiException.badRequest("BAD_MONTH", "صيغة الشهر يجب أن تكون YYYY-MM");
        }
    }
}
