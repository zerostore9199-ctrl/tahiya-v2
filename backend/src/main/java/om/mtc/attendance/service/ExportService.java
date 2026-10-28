package om.mtc.attendance.service;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import om.mtc.attendance.config.AppProperties;
import om.mtc.attendance.web.dto.ReportDtos.*;
import om.mtc.attendance.web.dto.SessionDtos.SessionSummary;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/** Renders reports as Excel (right-to-left sheets) and PDF (Arabic, RTL tables). */
@Service
public class ExportService {
    private static final Logger log = LoggerFactory.getLogger(ExportService.class);
    private static final Map<String, String> STATUS_AR = Map.of(
            "PRESENT", "حاضر", "LATE", "متأخر", "EXCUSED", "مستأذن", "ABSENT", "غائب");
    private static final Map<String, String> METHOD_AR = Map.of("FACE", "بالوجه", "MANUAL", "يدوي", "AUTO", "تلقائي");
    private static final Map<String, String> TYPE_AR = Map.of(
            "MORNING_PARADE", "طابور الصباح", "TRAINING", "حصة تدريبية", "SHIFT", "مناوبة",
            "EVENING_PARADE", "طابور المساء", "OTHER", "أخرى");
    private static final Color OLIVE = new Color(0x4B, 0x53, 0x20);
    private static final Color SAND = new Color(0xEE, 0xE8, 0xD5);

    private final AppProperties props;

    public ExportService(AppProperties props) {
        this.props = props;
    }

    // ================================================================= Excel

    public byte[] monthlyExcel(MonthlyReport r) {
        try (Workbook wb = new XSSFWorkbook()) {
            Styles st = new Styles(wb);
            Sheet sh = wb.createSheet("التقرير الشهري");
            sh.setRightToLeft(true);
            int row = title(sh, st, "التقرير الشهري للحضور والانضباط — " + r.month(), r.platoonName(), 9);
            row = totalsBlock(sh, st, row, r.totals(), r.previousMonth());
            row++;
            String[] head = {"الرقم العسكري", "الاسم", "الرتبة", "الفصيل", "الجلسات", "حاضر", "متأخر",
                    "مستأذن", "غائب", "نسبة الحضور %", "الطريقة الغالبة"};
            header(sh, st, row++, head);
            for (SoldierStats s : r.soldiers()) {
                Row x = sh.createRow(row++);
                cell(x, 0, s.militaryNumber(), st.text);
                cell(x, 1, s.fullName(), st.text);
                cell(x, 2, s.militaryRank(), st.text);
                cell(x, 3, s.platoonName(), st.text);
                num(x, 4, s.sessions(), st.num);
                num(x, 5, s.present(), st.num);
                num(x, 6, s.late(), st.num);
                num(x, 7, s.excused(), st.num);
                num(x, 8, s.absent(), st.num);
                num(x, 9, s.attendanceRate(), st.pct);
                cell(x, 10, s.mainMethod() == null ? "" : METHOD_AR.get(s.mainMethod().name()), st.text);
            }
            autosize(sh, head.length);
            return write(wb);
        } catch (IOException e) {
            throw new IllegalStateException("Excel export failed", e);
        }
    }

    public byte[] dailyExcel(DailyReport r) {
        try (Workbook wb = new XSSFWorkbook()) {
            Styles st = new Styles(wb);
            Sheet sh = wb.createSheet("التقرير اليومي");
            sh.setRightToLeft(true);
            int row = title(sh, st, "التقرير اليومي للحضور — " + r.date(), r.platoonName(), 7);
            row = totalsBlock(sh, st, row, r.totals(), null);
            row++;
            String[] head = {"الفصيل", "الجلسة", "الحالة", "الإجمالي", "حاضر", "متأخر", "مستأذن", "غائب", "النسبة %"};
            header(sh, st, row++, head);
            for (SessionSummary s : r.sessions()) {
                Row x = sh.createRow(row++);
                cell(x, 0, s.platoonName(), st.text);
                cell(x, 1, TYPE_AR.getOrDefault(s.sessionType().name(), s.sessionType().name()), st.text);
                cell(x, 2, s.status().name(), st.text);
                num(x, 3, s.counts().total(), st.num);
                num(x, 4, s.counts().present(), st.num);
                num(x, 5, s.counts().late(), st.num);
                num(x, 6, s.counts().excused(), st.num);
                num(x, 7, s.counts().absent(), st.num);
                num(x, 8, s.counts().attendanceRate(), st.pct);
            }
            row++;
            header(sh, st, row++, new String[]{"الاسم", "الرقم العسكري", "الحالة", "الملاحظة", "الجلسة"});
            for (DailyAbsence a : r.exceptions()) {
                Row x = sh.createRow(row++);
                cell(x, 0, a.fullName(), st.text);
                cell(x, 1, a.militaryNumber(), st.text);
                cell(x, 2, STATUS_AR.getOrDefault(a.status(), a.status()), st.text);
                cell(x, 3, a.note() == null ? "" : a.note(), st.text);
                cell(x, 4, a.sessionLabel(), st.text);
            }
            autosize(sh, head.length);
            return write(wb);
        } catch (IOException e) {
            throw new IllegalStateException("Excel export failed", e);
        }
    }

    private int title(Sheet sh, Styles st, String title, String platoon, int span) {
        Row t = sh.createRow(0);
        cell(t, 0, title, st.title);
        sh.addMergedRegion(new CellRangeAddress(0, 0, 0, span));
        Row p = sh.createRow(1);
        cell(p, 0, "النطاق: " + (platoon == null ? "-" : platoon) + "   |   تاريخ الإصدار: " + now(), st.text);
        sh.addMergedRegion(new CellRangeAddress(1, 1, 0, span));
        return 3;
    }

    private int totalsBlock(Sheet sh, Styles st, int row, Totals t, Totals prev) {
        header(sh, st, row++, new String[]{"البند", "الشهر الحالي", prev == null ? "" : "الشهر السابق"});
        Object[][] lines = {
                {"عدد الجلسات", t.sessions(), prev == null ? null : prev.sessions()},
                {"حاضر", t.present(), prev == null ? null : prev.present()},
                {"متأخر", t.late(), prev == null ? null : prev.late()},
                {"مستأذن", t.excused(), prev == null ? null : prev.excused()},
                {"غائب", t.absent(), prev == null ? null : prev.absent()},
                {"نسبة الحضور %", t.attendanceRate(), prev == null ? null : prev.attendanceRate()},
                {"نسبة الانضباط %", t.disciplineRate(), prev == null ? null : prev.disciplineRate()},
        };
        for (Object[] l : lines) {
            Row x = sh.createRow(row++);
            cell(x, 0, (String) l[0], st.text);
            num(x, 1, ((Number) l[1]).doubleValue(), st.num);
            if (l[2] != null) {
                num(x, 2, ((Number) l[2]).doubleValue(), st.num);
            }
        }
        return row;
    }

    private static void header(Sheet sh, Styles st, int rowIdx, String[] cols) {
        Row h = sh.createRow(rowIdx);
        for (int i = 0; i < cols.length; i++) {
            cell(h, i, cols[i], st.header);
        }
    }

    private static void cell(Row r, int i, String v, CellStyle s) {
        Cell c = r.createCell(i);
        c.setCellValue(v == null ? "" : v);
        c.setCellStyle(s);
    }

    private static void num(Row r, int i, double v, CellStyle s) {
        Cell c = r.createCell(i);
        c.setCellValue(v);
        c.setCellStyle(s);
    }

    private static void autosize(Sheet sh, int cols) {
        for (int i = 0; i <= cols; i++) {
            sh.setColumnWidth(i, i == 1 ? 32 * 256 : 16 * 256);
        }
    }

    private static byte[] write(Workbook wb) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        wb.write(out);
        return out.toByteArray();
    }

    private static final class Styles {
        final CellStyle title, header, text, num, pct;

        Styles(Workbook wb) {
            org.apache.poi.ss.usermodel.Font bold = wb.createFont();
            bold.setBold(true);
            org.apache.poi.ss.usermodel.Font big = wb.createFont();
            big.setBold(true);
            big.setFontHeightInPoints((short) 14);
            title = wb.createCellStyle();
            title.setFont(big);
            title.setAlignment(HorizontalAlignment.RIGHT);
            header = wb.createCellStyle();
            header.setFont(bold);
            header.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            header.setAlignment(HorizontalAlignment.CENTER);
            header.setBorderBottom(BorderStyle.THIN);
            text = wb.createCellStyle();
            text.setAlignment(HorizontalAlignment.RIGHT);
            num = wb.createCellStyle();
            num.setAlignment(HorizontalAlignment.CENTER);
            pct = wb.createCellStyle();
            pct.setAlignment(HorizontalAlignment.CENTER);
            pct.setDataFormat(wb.createDataFormat().getFormat("0.0"));
        }
    }

    // ================================================================= PDF

    public byte[] monthlyPdf(MonthlyReport r) {
        return pdf(doc -> {
            heading(doc, "التقرير الشهري للحضور والانضباط — " + r.month(), r.platoonName());
            doc.add(totalsTable(r.totals(), r.previousMonth()));
            doc.add(spacer());
            PdfPTable t = table(new float[]{2.2f, 3.6f, 1.6f, 1.2f, 1, 1, 1, 1, 1.4f},
                    "الرقم العسكري", "الاسم", "الرتبة", "الجلسات", "حاضر", "متأخر", "مستأذن", "غائب", "النسبة %");
            for (SoldierStats s : r.soldiers()) {
                row(t, s.militaryNumber(), s.fullName(), s.militaryRank(), s.sessions(), s.present(), s.late(),
                        s.excused(), s.absent(), s.attendanceRate());
            }
            doc.add(t);
        });
    }

    public byte[] dailyPdf(DailyReport r) {
        return pdf(doc -> {
            heading(doc, "التقرير اليومي للحضور — " + r.date(), r.platoonName());
            doc.add(totalsTable(r.totals(), null));
            doc.add(spacer());
            PdfPTable t = table(new float[]{2.5f, 2.2f, 1.4f, 1, 1, 1, 1, 1, 1.2f},
                    "الفصيل", "الجلسة", "الحالة", "الإجمالي", "حاضر", "متأخر", "مستأذن", "غائب", "النسبة %");
            for (SessionSummary s : r.sessions()) {
                row(t, s.platoonName(), TYPE_AR.getOrDefault(s.sessionType().name(), ""), s.status().name(),
                        s.counts().total(), s.counts().present(), s.counts().late(), s.counts().excused(),
                        s.counts().absent(), s.counts().attendanceRate());
            }
            doc.add(t);
            doc.add(spacer());
            PdfPTable e = table(new float[]{3.2f, 2f, 1.4f, 3.4f, 3f},
                    "الاسم", "الرقم العسكري", "الحالة", "الملاحظة", "الجلسة");
            for (DailyAbsence a : r.exceptions()) {
                row(e, a.fullName(), a.militaryNumber(), STATUS_AR.getOrDefault(a.status(), a.status()),
                        a.note() == null ? "" : a.note(), a.sessionLabel());
            }
            doc.add(e);
        });
    }

    private interface DocWriter {
        void write(Document doc) throws DocumentException;
    }

    private BaseFont baseFont;

    private synchronized BaseFont font() throws IOException, DocumentException {
        if (baseFont == null) {
            String path = props.reports().pdfFontPath();
            if (path == null || !new File(path).isFile()) {
                log.warn("PDF font not found at {} — Arabic text will not render. Set PDF_FONT_PATH.", path);
                baseFont = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
            } else {
                baseFont = BaseFont.createFont(path, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
            }
        }
        return baseFont;
    }

    private Font f(float size, boolean bold, Color color) {
        try {
            Font x = new Font(font(), size, bold ? Font.BOLD : Font.NORMAL);
            x.setColor(color);
            return x;
        } catch (IOException | DocumentException e) {
            throw new IllegalStateException(e);
        }
    }

    private byte[] pdf(DocWriter w) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4.rotate(), 28, 28, 28, 28);
        try {
            PdfWriter.getInstance(doc, out);
            doc.addTitle("Attendance report");
            doc.open();
            w.write(doc);
            doc.close();
            return out.toByteArray();
        } catch (DocumentException e) {
            throw new IllegalStateException("PDF export failed", e);
        }
    }

    /** RTL text must live in a table cell (OpenPDF shapes Arabic only with an RTL run direction). */
    private void heading(Document doc, String title, String scope) throws DocumentException {
        PdfPTable t = new PdfPTable(1);
        t.setWidthPercentage(100);
        t.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        PdfPCell c1 = new PdfPCell(new Phrase(title, f(16, true, OLIVE)));
        c1.setBorder(Rectangle.NO_BORDER);
        c1.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        t.addCell(c1);
        PdfPCell c2 = new PdfPCell(new Phrase("النظام العسكري الذكي لإدارة الحضور والانضباط   |   النطاق: "
                + (scope == null ? "-" : scope) + "   |   تاريخ الإصدار: " + now(), f(9, false, Color.DARK_GRAY)));
        c2.setBorder(Rectangle.BOTTOM);
        c2.setPaddingBottom(8);
        c2.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        t.addCell(c2);
        doc.add(t);
        doc.add(spacer());
    }

    private PdfPTable totalsTable(Totals t, Totals prev) {
        PdfPTable x = table(new float[]{1, 1, 1, 1, 1, 1.3f, 1.3f},
                "الجلسات", "حاضر", "متأخر", "مستأذن", "غائب", "نسبة الحضور %", "نسبة الانضباط %");
        row(x, t.sessions(), t.present(), t.late(), t.excused(), t.absent(), t.attendanceRate(), t.disciplineRate());
        if (prev != null) {
            PdfPCell c = cell("الشهر السابق: حضور " + prev.attendanceRate() + "% — انضباط " + prev.disciplineRate()
                    + "% — غياب " + prev.absent(), f(9, false, Color.DARK_GRAY), null);
            c.setColspan(7);
            x.addCell(c);
        }
        return x;
    }

    private PdfPTable table(float[] widths, String... headers) {
        PdfPTable t = new PdfPTable(widths);
        t.setWidthPercentage(100);
        t.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        t.setHeaderRows(1);
        for (String h : headers) {
            t.addCell(cell(h, f(10, true, Color.WHITE), OLIVE));
        }
        return t;
    }

    private void row(PdfPTable t, Object... values) {
        Font ft = f(9.5f, false, Color.BLACK);
        boolean shade = (t.getRows().size() % 2) == 0;
        for (Object v : values) {
            t.addCell(cell(v == null ? "" : String.valueOf(v), ft, shade ? SAND : null));
        }
    }

    private PdfPCell cell(String text, Font ft, Color bg) {
        PdfPCell c = new PdfPCell(new Phrase(text, ft));
        c.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        c.setHorizontalAlignment(Element.ALIGN_CENTER);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        c.setPadding(5);
        c.setBorderColor(new Color(0xCC, 0xC6, 0xB0));
        if (bg != null) {
            c.setBackgroundColor(bg);
        }
        return c;
    }

    private static Paragraph spacer() {
        Paragraph p = new Paragraph(" ");
        p.setSpacingAfter(4);
        return p;
    }

    private String now() {
        return ZonedDateTime.now(props.zoneId()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }

    public static String contentType(String format) {
        return "pdf".equals(format) ? "application/pdf"
                : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    }

    public static List<String> formats() {
        return List.of("pdf", "xlsx");
    }
}
