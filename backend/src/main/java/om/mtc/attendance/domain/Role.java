package om.mtc.attendance.domain;

/** The four command roles of the unit (الأدوار الأربعة). */
public enum Role {
    /** السنير — takes attendance for his own platoon. */
    SENIOR,
    /** السنير العام — monitors every platoon, read-only. */
    GENERAL_SENIOR,
    /** قائد الفصيل — approves absences/lateness and manages his platoon's soldiers. */
    PLATOON_LEADER,
    /** وكيل السرية — company administrator with full access. */
    COMPANY_AGENT;

    public boolean isPlatoonBound() {
        return this == SENIOR || this == PLATOON_LEADER;
    }
}
