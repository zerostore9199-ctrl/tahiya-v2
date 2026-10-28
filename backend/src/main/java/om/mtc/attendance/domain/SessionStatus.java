package om.mtc.attendance.domain;

public enum SessionStatus {
    /** Attendance is being taken. */
    OPEN,
    /** Closed by the senior; waiting for the platoon leader's approval. */
    CLOSED,
    /** Approved by the platoon leader; records are locked. */
    APPROVED
}
