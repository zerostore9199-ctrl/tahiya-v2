export type Role = 'SENIOR' | 'GENERAL_SENIOR' | 'PLATOON_LEADER' | 'COMPANY_AGENT';
export type AttendanceStatus = 'PRESENT' | 'LATE' | 'EXCUSED' | 'ABSENT';
export type AttendanceMethod = 'FACE' | 'MANUAL' | 'AUTO';
export type SessionStatus = 'OPEN' | 'CLOSED' | 'APPROVED';
export type SessionType = 'MORNING_PARADE' | 'TRAINING' | 'SHIFT' | 'EVENING_PARADE' | 'OTHER';

export const ROLES: Role[] = ['SENIOR', 'GENERAL_SENIOR', 'PLATOON_LEADER', 'COMPANY_AGENT'];
export const STATUSES: AttendanceStatus[] = ['PRESENT', 'LATE', 'EXCUSED', 'ABSENT'];
export const SESSION_TYPES: SessionType[] = ['MORNING_PARADE', 'TRAINING', 'SHIFT', 'EVENING_PARADE', 'OTHER'];

export interface UserView {
  id: number; username: string; fullName: string; militaryRank?: string; role: Role;
  platoonId?: number; platoonName?: string; enabled: boolean; mustChangePassword: boolean;
  locked: boolean; photoVersion?: number; lastLoginAt?: string; createdAt: string;
}
export interface TokenResponse { accessToken: string; expiresInSeconds: number; user: UserView; }

export interface Page<T> { items: T[]; page: number; size: number; totalItems: number; totalPages: number; }

export interface Platoon { id: number; code: string; name: string; companyName: string; active: boolean; activeSoldiers: number; }

export interface Soldier {
  id: number; militaryNumber: string; fullName: string; militaryRank: string; platoonId: number;
  platoonName: string; active: boolean; biometricConsent: boolean; enrolledFaces: number; photoVersion?: number; createdAt: string;
}
export interface FaceTemplate { id: number; modelName: string; detScore: number; createdAt: string; }
export interface PhotoResult { soldier: Soldier; enrollment?: EnrollResult; }
export interface EnrollResult { enrolledFaces: number; maxFaces: number; detScore: number; template: FaceTemplate; }

export interface Counts {
  total: number; present: number; late: number; excused: number; absent: number; unrecorded: number;
  attendanceRate: number;
}
export interface SessionSummary {
  id: number; platoonId: number; platoonName: string; sessionType: SessionType; title?: string;
  sessionDate: string; scheduledAt?: string; status: SessionStatus; openedBy: string; openedAt: string;
  closedAt?: string; approvedBy?: string; approvedAt?: string; counts: Counts;
}
export interface RecordView {
  id: number; soldierId: number; militaryNumber: string; fullName: string; militaryRank: string;
  status: AttendanceStatus; method: AttendanceMethod; similarity?: number; note?: string; recordedAt: string;
}
export interface RosterEntry {
  soldierId: number; militaryNumber: string; fullName: string; militaryRank: string; hasFaces: boolean; photoVersion?: number;
  record?: RecordView;
}
export interface SessionDetail { session: SessionSummary; roster: RosterEntry[]; }

export type FaceOutcome = 'MATCHED' | 'MATCHED_LATE' | 'ALREADY_RECORDED' | 'UNKNOWN' | 'LOW_QUALITY';
export interface FaceResult {
  bbox: [number, number, number, number]; detScore: number; outcome: FaceOutcome; soldierId?: number;
  fullName?: string; militaryNumber?: string; similarity?: number;
}
export interface RecognitionResult {
  detected: number; ignoredLowQuality: number; recognized: number; alreadyRecorded: number; unknown: number;
  remaining: number; imageWidth: number; imageHeight: number; elapsedMs: number; faces: FaceResult[]; counts: Counts;
}

export interface Kpis { totalSoldiers: number; present: number; late: number; absent: number; excused: number; attendanceRate: number; }
export interface PlatoonLive {
  platoonId: number; platoonName: string; seniorName?: string; activeSoldiers: number; sessionId?: number;
  sessionType?: SessionType; sessionStatus?: SessionStatus; sessionOpenedAt?: string; counts?: Counts;
}
export interface PendingApproval { sessionId: number; platoonName: string; sessionType: SessionType; sessionDate: string; closedAt: string; absent: number; late: number; }
export interface AuditEntry { occurredAt: string; username?: string; action: string; details?: string; }
export interface LiveDashboard { date: string; kpis: Kpis; platoons: PlatoonLive[]; pendingApprovals: PendingApproval[]; recentActivity: AuditEntry[]; }
export interface TrendPoint { date: string; attendanceRate: number; records: number; }

export interface Totals { sessions: number; records: number; present: number; late: number; excused: number; absent: number; attendanceRate: number; disciplineRate: number; }
export interface SoldierStats {
  soldierId: number; militaryNumber: string; fullName: string; militaryRank: string; platoonName: string;
  sessions: number; present: number; late: number; excused: number; absent: number; attendanceRate: number;
  mainMethod?: AttendanceMethod;
}
export interface MonthlyReport { month: string; platoonId?: number; platoonName?: string; totals: Totals; previousMonth: Totals; soldiers: SoldierStats[]; }
export interface DailyAbsence { fullName: string; militaryNumber: string; status: AttendanceStatus; note?: string; sessionLabel: string; }
export interface DailyReport { date: string; platoonId?: number; platoonName?: string; totals: Totals; sessions: SessionSummary[]; exceptions: DailyAbsence[]; }

export interface AuditView { id: number; occurredAt: string; username?: string; action: string; entityType?: string; entityId?: string; details?: string; ipAddress?: string; success: boolean; }
export interface Settings {
  recognitionThreshold: number; recognitionMinDetScore: number; enrollmentMinDetScore: number;
  enrollmentMaxFaces: number; enrollmentDuplicateThreshold: number; lateGraceMinutes: number; auditRetentionDays: number;
}
export interface ApiError { code: string; message: string; fieldErrors?: Record<string, string>; }
