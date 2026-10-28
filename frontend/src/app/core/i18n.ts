import { Injectable, Pipe, PipeTransform, signal, computed } from '@angular/core';

export type Lang = 'ar' | 'en';

/** Arabic is the primary language; English is provided for non-Arabic readers and demos. */
const DICT: Record<string, { ar: string; en: string }> = {
  appName: { ar: 'تاهييه', en: 'Tahiya' },
  appFull: { ar: 'النظام العسكري الذكي لإدارة الحضور والانضباط', en: 'Military Smart Attendance & Discipline System' },
  // roles
  'role.SENIOR': { ar: 'السنير', en: 'Senior' },
  'role.GENERAL_SENIOR': { ar: 'السنير العام', en: 'General Senior' },
  'role.PLATOON_LEADER': { ar: 'قائد الفصيل', en: 'Platoon Leader' },
  'role.COMPANY_AGENT': { ar: 'وكيل السرية', en: 'Company Agent' },
  // statuses
  'status.PRESENT': { ar: 'حاضر', en: 'Present' },
  'status.LATE': { ar: 'متأخر', en: 'Late' },
  'status.EXCUSED': { ar: 'مستأذن', en: 'Excused' },
  'status.ABSENT': { ar: 'غائب', en: 'Absent' },
  'status.NONE': { ar: 'لم يُسجَّل', en: 'Not recorded' },
  'method.FACE': { ar: 'بالوجه', en: 'Face' },
  'method.MANUAL': { ar: 'يدوي', en: 'Manual' },
  'method.AUTO': { ar: 'تلقائي', en: 'Auto' },
  'session.OPEN': { ar: 'مفتوحة', en: 'Open' },
  'session.CLOSED': { ar: 'بانتظار الاعتماد', en: 'Awaiting approval' },
  'session.APPROVED': { ar: 'معتمدة', en: 'Approved' },
  'type.MORNING_PARADE': { ar: 'طابور الصباح', en: 'Morning parade' },
  'type.TRAINING': { ar: 'حصة تدريبية', en: 'Training' },
  'type.SHIFT': { ar: 'مناوبة', en: 'Shift' },
  'type.EVENING_PARADE': { ar: 'طابور المساء', en: 'Evening parade' },
  'type.OTHER': { ar: 'أخرى', en: 'Other' },
  'face.MATCHED': { ar: 'تم التعرف', en: 'Recognized' },
  'face.MATCHED_LATE': { ar: 'تم التعرف (متأخر)', en: 'Recognized (late)' },
  'face.ALREADY_RECORDED': { ar: 'مسجّل مسبقاً', en: 'Already recorded' },
  'face.UNKNOWN': { ar: 'غير معروف', en: 'Unknown' },
  'face.LOW_QUALITY': { ar: 'غير واضح', en: 'Unclear' },
  // nav
  'nav.home': { ar: 'الرئيسية', en: 'Home' },
  'nav.dashboard': { ar: 'لوحة المتابعة', en: 'Live dashboard' },
  'nav.sessions': { ar: 'جلسات التحضير', en: 'Sessions' },
  'nav.approvals': { ar: 'الاعتمادات', en: 'Approvals' },
  'nav.soldiers': { ar: 'الجنود', en: 'Soldiers' },
  'nav.platoons': { ar: 'الفصائل', en: 'Platoons' },
  'nav.reports': { ar: 'التقارير', en: 'Reports' },
  'nav.users': { ar: 'المستخدمون', en: 'Users' },
  'nav.audit': { ar: 'سجل العمليات', en: 'Audit log' },
  'nav.settings': { ar: 'الإعدادات', en: 'Settings' },
  'nav.logout': { ar: 'تسجيل الخروج', en: 'Sign out' },
  // common
  save: { ar: 'حفظ', en: 'Save' }, cancel: { ar: 'إلغاء', en: 'Cancel' }, edit: { ar: 'تعديل', en: 'Edit' },
  add: { ar: 'إضافة', en: 'Add' }, search: { ar: 'بحث', en: 'Search' }, all: { ar: 'الكل', en: 'All' },
  loading: { ar: 'جارٍ التحميل…', en: 'Loading…' }, retry: { ar: 'إعادة المحاولة', en: 'Retry' },
  back: { ar: 'رجوع', en: 'Back' }, close: { ar: 'إغلاق', en: 'Close' }, confirm: { ar: 'تأكيد', en: 'Confirm' },
  name: { ar: 'الاسم', en: 'Name' }, rank: { ar: 'الرتبة', en: 'Rank' }, platoon: { ar: 'الفصيل', en: 'Platoon' },
  militaryNumber: { ar: 'الرقم العسكري', en: 'Military no.' }, status: { ar: 'الحالة', en: 'Status' },
  date: { ar: 'التاريخ', en: 'Date' }, actions: { ar: 'إجراءات', en: 'Actions' }, note: { ar: 'ملاحظة', en: 'Note' },
  total: { ar: 'الإجمالي', en: 'Total' }, rate: { ar: 'نسبة الحضور', en: 'Attendance rate' },
  active: { ar: 'نشط', en: 'Active' }, inactive: { ar: 'غير نشط', en: 'Inactive' },
  prev: { ar: 'السابق', en: 'Previous' }, next: { ar: 'التالي', en: 'Next' },
  noData: { ar: 'لا توجد بيانات', en: 'No data' },
  offline: { ar: 'لا يوجد اتصال بالشبكة — سيتم حفظ التحضير اليدوي وإرساله عند عودة الاتصال', en: 'Offline — manual marks are queued and sent when the connection returns' },
  queued: { ar: 'عمليات بانتظار الإرسال', en: 'queued actions' },
  language: { ar: 'English', en: 'العربية' },
  // login
  'login.title': { ar: 'تسجيل الدخول', en: 'Sign in' },
  'login.username': { ar: 'اسم المستخدم', en: 'Username' },
  'login.password': { ar: 'كلمة المرور', en: 'Password' },
  'login.submit': { ar: 'دخول', en: 'Sign in' },
  'pwd.title': { ar: 'تغيير كلمة المرور', en: 'Change password' },
  'pwd.required': { ar: 'يجب تغيير كلمة المرور المؤقتة قبل المتابعة', en: 'You must change your temporary password to continue' },
  'pwd.current': { ar: 'كلمة المرور الحالية', en: 'Current password' },
  'pwd.new': { ar: 'كلمة المرور الجديدة', en: 'New password' },
  'pwd.confirm': { ar: 'تأكيد كلمة المرور', en: 'Confirm password' },
  'pwd.rules': { ar: '10 أحرف على الأقل، تحتوي حروفاً وأرقاماً', en: 'At least 10 characters with letters and digits' },
  'pwd.mismatch': { ar: 'كلمتا المرور غير متطابقتين', en: 'Passwords do not match' },
  'pwd.done': { ar: 'تم تغيير كلمة المرور', en: 'Password changed' },
  // home
  'home.welcome': { ar: 'مرحباً', en: 'Welcome' },
  'home.start': { ar: 'بدء تحضير جديد', en: 'Start new attendance' },
  'home.continue': { ar: 'متابعة الجلسة المفتوحة', en: 'Continue open session' },
  'home.lastSession': { ar: 'آخر جلسة', en: 'Last session' },
  'home.today': { ar: 'جلسات اليوم', en: "Today's sessions" },
  'home.noSessions': { ar: 'لا توجد جلسات اليوم بعد', en: 'No sessions yet today' },
  // sessions
  'sess.new': { ar: 'جلسة تحضير جديدة', en: 'New attendance session' },
  'sess.type': { ar: 'نوع الجلسة', en: 'Session type' },
  'sess.title': { ar: 'عنوان اختياري', en: 'Optional title' },
  'sess.scheduled': { ar: 'وقت البدء المقرر (لاحتساب التأخير)', en: 'Scheduled start (for lateness)' },
  'sess.open': { ar: 'فتح الجلسة', en: 'Open session' },
  'sess.camera': { ar: 'التحضير بالكاميرا', en: 'Camera' },
  'sess.roster': { ar: 'الكشف اليدوي', en: 'Roster' },
  'sess.close': { ar: 'إغلاق الجلسة', en: 'Close session' },
  'sess.closeConfirm': { ar: 'سيتم تسجيل كل جندي بلا سجل غائباً. هل تريد إغلاق الجلسة؟', en: 'Everyone without a record will be marked absent. Close the session?' },
  'sess.closed': { ar: 'تم إغلاق الجلسة ونشر كشف الحضور', en: 'Session closed and roster published' },
  'sess.approve': { ar: 'اعتماد الكشف', en: 'Approve roster' },
  'sess.approved': { ar: 'تم اعتماد الكشف', en: 'Roster approved' },
  'sess.openedBy': { ar: 'فتحها', en: 'Opened by' },
  'sess.remaining': { ar: 'المتبقون', en: 'Remaining' },
  'sess.filterRemaining': { ar: 'غير المسجّلين فقط', en: 'Unrecorded only' },
  'sess.changes': { ar: 'تعديلات غير محفوظة', en: 'unsaved changes' },
  'sess.readOnly': { ar: 'الجلسة للعرض فقط', en: 'Read-only session' },
  'sess.noFaces': { ar: 'بدون بصمة وجه', en: 'No face enrolled' },
  // camera
  'cam.start': { ar: 'تشغيل الكاميرا', en: 'Start camera' },
  'cam.capture': { ar: 'التقاط', en: 'Capture' },
  'cam.retake': { ar: 'إعادة', en: 'Retake' },
  'cam.upload': { ar: 'رفع صورة', en: 'Upload photo' },
  'cam.switch': { ar: 'تبديل الكاميرا', en: 'Switch camera' },
  'cam.processing': { ar: 'جارٍ التعرف على الوجوه…', en: 'Recognizing faces…' },
  'cam.denied': { ar: 'تعذّر تشغيل الكاميرا. اسمح بالوصول إليها أو استخدم رفع صورة.', en: 'Camera unavailable. Allow access or upload a photo.' },
  'cam.tip': { ar: 'اجعل الشمس خلفك، والتقط الطابور كاملاً بوضوح', en: 'Keep the sun behind you and frame the whole formation' },
  'cam.recognized': { ar: 'تم التعرف', en: 'Recognized' },
  'cam.unknown': { ar: 'غير معروف', en: 'Unknown' },
  'cam.detected': { ar: 'وجوه مكتشفة', en: 'Faces detected' },
  'cam.already': { ar: 'مسجّل مسبقاً', en: 'Already recorded' },
  'cam.noFace': { ar: 'لم يتم اكتشاف أي وجه — أعد التصوير', en: 'No face found — please retake' },
  'cam.fallback': { ar: 'خدمة التعرف غير متاحة — أكمل التحضير يدوياً', en: 'Recognition unavailable — continue manually' },
  'cam.secure': { ar: 'الكاميرا تتطلب اتصالاً آمناً (HTTPS)', en: 'Camera requires a secure (HTTPS) connection' },
  // approvals
  'appr.title': { ar: 'الكشوف بانتظار الاعتماد', en: 'Rosters awaiting approval' },
  'appr.none': { ar: 'لا توجد كشوف بانتظار الاعتماد', en: 'Nothing awaiting approval' },
  // soldiers
  'sol.new': { ar: 'إضافة جندي', en: 'Add soldier' },
  'sol.edit': { ar: 'تعديل بيانات الجندي', en: 'Edit soldier' },
  'sol.consent': { ar: 'موافقة الجندي على معالجة بياناته البيومترية (مطلوبة لتسجيل الوجه)', en: 'Biometric consent (required for face enrollment)' },
  'sol.faces': { ar: 'صور الوجه', en: 'Face photos' },
  'sol.enroll': { ar: 'تسجيل الوجه', en: 'Enroll face' },
  'sol.enrollHint': { ar: 'سجّل 3 إلى 5 صور بالزي العسكري وفي إضاءة مختلفة للتعرّف الدقيق. يُحفظ تمثيل رقمي مشفّر فقط من هذه الصور، ولا تُحفظ الصور نفسها.', en: 'Enroll 3–5 photos in uniform under different lighting for accurate recognition. Only an encrypted template is kept from these captures — not the photos themselves.' },
  'photo.title': { ar: 'الصورة الشخصية', en: 'Profile photo' },
  'photo.hint': { ar: 'تُحفظ مشفّرة وتظهر في القوائم وكشف التحضير. صور الكاميرا أثناء التحضير لا تُحفظ أبداً.', en: 'Stored encrypted and shown in lists and rosters. Photos taken during attendance are never stored.' },
  'photo.enrollToo': { ar: 'تسجيل الوجه للتعرّف من هذه الصورة أيضاً', en: 'Also enroll the face for recognition from this photo' },
  'photo.take': { ar: 'التقط صورة الوجه أو ارفعها من الجهاز', en: 'Take a face photo or upload one' },
  'photo.choose': { ar: 'اختيار صورة', en: 'Choose photo' },
  'photo.remove': { ar: 'حذف الصورة', en: 'Remove photo' },
  'photo.removeConfirm': { ar: 'حذف الصورة الشخصية؟', en: 'Remove the profile photo?' },
  'photo.saved': { ar: 'تم حفظ الصورة', en: 'Photo saved' },
  'photo.removed': { ar: 'تم حذف الصورة', en: 'Photo removed' },
  'photo.unreadable': { ar: 'تعذّر قراءة الصورة، اختر صورة JPEG أو PNG', en: 'Could not read the image, choose a JPEG or PNG' },
  'set.myPhoto': { ar: 'صورتي', en: 'My photo' },
  'sol.enrolled': { ar: 'تم تسجيل صورة الوجه', en: 'Face enrolled' },
  'sol.deactivate': { ar: 'إنهاء الخدمة في الوحدة', en: 'End of service' },
  'sol.deactivateConfirm': { ar: 'سيتم إلغاء تنشيط الجندي وحذف بياناته البيومترية نهائياً. متابعة؟', en: 'The soldier will be deactivated and biometrics deleted permanently. Continue?' },
  'sol.reactivate': { ar: 'إعادة التنشيط', en: 'Reactivate' },
  'sol.erase': { ar: 'حذف البيانات البيومترية', en: 'Erase biometrics' },
  'sol.eraseConfirm': { ar: 'حذف جميع بصمات الوجه لهذا الجندي؟', en: 'Delete all face templates for this soldier?' },
  'sol.saved': { ar: 'تم حفظ بيانات الجندي', en: 'Soldier saved' },
  'sol.searchPh': { ar: 'ابحث بالاسم أو الرقم العسكري', en: 'Search by name or military number' },
  'sol.empty': { ar: 'لا يوجد جنود مطابقون', en: 'No matching soldiers' },
  // platoons
  'plt.new': { ar: 'إضافة فصيل', en: 'Add platoon' },
  'plt.code': { ar: 'الرمز', en: 'Code' },
  'plt.company': { ar: 'السرية', en: 'Company' },
  'plt.soldiers': { ar: 'عدد الجنود', en: 'Soldiers' },
  // users
  'usr.new': { ar: 'إضافة مستخدم', en: 'Add user' },
  'usr.role': { ar: 'الدور', en: 'Role' },
  'usr.tempPwd': { ar: 'كلمة مرور مؤقتة', en: 'Temporary password' },
  'usr.enabled': { ar: 'الحساب مفعّل', en: 'Account enabled' },
  'usr.reset': { ar: 'إعادة تعيين كلمة المرور', en: 'Reset password' },
  'usr.unlock': { ar: 'فك القفل', en: 'Unlock' },
  'usr.locked': { ar: 'مقفل', en: 'Locked' },
  'usr.lastLogin': { ar: 'آخر دخول', en: 'Last login' },
  'usr.saved': { ar: 'تم حفظ المستخدم', en: 'User saved' },
  // reports
  'rep.monthly': { ar: 'التقرير الشهري', en: 'Monthly report' },
  'rep.daily': { ar: 'التقرير اليومي', en: 'Daily report' },
  'rep.month': { ar: 'الشهر', en: 'Month' },
  'rep.discipline': { ar: 'نسبة الانضباط', en: 'Discipline rate' },
  'rep.sessions': { ar: 'عدد الجلسات', en: 'Sessions' },
  'rep.prevMonth': { ar: 'مقارنة بالشهر السابق', en: 'vs previous month' },
  'rep.mainMethod': { ar: 'الطريقة الغالبة', en: 'Main method' },
  'rep.exceptions': { ar: 'الغياب والتأخير والاستئذان', en: 'Absences, lateness & excuses' },
  'rep.pdf': { ar: 'تصدير PDF', en: 'Export PDF' },
  'rep.xlsx': { ar: 'تصدير Excel', en: 'Export Excel' },
  // dashboard
  'dash.totalSoldiers': { ar: 'إجمالي الجنود', en: 'Total soldiers' },
  'dash.senior': { ar: 'السنير المسؤول', en: 'Senior' },
  'dash.session': { ar: 'حالة الجلسة', en: 'Session' },
  'dash.noSession': { ar: 'لم تبدأ', en: 'Not started' },
  'dash.trend': { ar: 'نسبة الحضور خلال آخر 7 أيام', en: 'Attendance — last 7 days' },
  'dash.pending': { ar: 'بانتظار الاعتماد', en: 'Pending approval' },
  'dash.activity': { ar: 'آخر العمليات', en: 'Recent activity' },
  'dash.refresh': { ar: 'تحديث', en: 'Refresh' },
  // audit
  'aud.action': { ar: 'العملية', en: 'Action' },
  'aud.user': { ar: 'المستخدم', en: 'User' },
  'aud.details': { ar: 'التفاصيل', en: 'Details' },
  'aud.time': { ar: 'الوقت', en: 'Time' },
  // settings
  'set.system': { ar: 'إعدادات التعرف والنظام', en: 'Recognition & system settings' },
  'set.threshold': { ar: 'عتبة التطابق (تشابه جيب التمام)', en: 'Match threshold (cosine similarity)' },
  'set.thresholdHint': { ar: 'رفعها يقلل القبول الخاطئ ويزيد الرفض الخاطئ. اضبطها أثناء التجربة الميدانية.', en: 'Higher = fewer false accepts, more false rejects. Tune during field trials.' },
  'set.minDet': { ar: 'أدنى ثقة لاكتشاف الوجه في التحضير', en: 'Min detection score (attendance)' },
  'set.enrollMinDet': { ar: 'أدنى ثقة لاكتشاف الوجه في التسجيل', en: 'Min detection score (enrollment)' },
  'set.maxFaces': { ar: 'أقصى عدد صور لكل جندي', en: 'Max photos per soldier' },
  'set.dup': { ar: 'عتبة كشف تكرار الهوية عند التسجيل', en: 'Duplicate-identity threshold' },
  'set.grace': { ar: 'مهلة التأخير (دقائق)', en: 'Late grace (minutes)' },
  'set.retention': { ar: 'مدة الاحتفاظ بسجل العمليات (أيام)', en: 'Audit retention (days)' },
  'set.saved': { ar: 'تم حفظ الإعدادات', en: 'Settings saved' },
  'set.account': { ar: 'حسابي', en: 'My account' },
  'set.readonly': { ar: 'يمكن لوكيل السرية فقط تعديل هذه الإعدادات', en: 'Only the company agent can change these' },
  // errors
  'err.generic': { ar: 'حدث خطأ غير متوقع', en: 'Unexpected error' },
  'err.network': { ar: 'تعذّر الاتصال بالخادم', en: 'Cannot reach the server' },
  'err.forbidden': { ar: 'ليست لديك صلاحية لهذه العملية', en: 'You are not allowed to do this' },
  'nf.title': { ar: 'الصفحة غير موجودة', en: 'Page not found' },
};

@Injectable({ providedIn: 'root' })
export class I18n {
  readonly lang = signal<Lang>(this.initial());
  readonly dir = computed(() => (this.lang() === 'ar' ? 'rtl' : 'ltr'));

  constructor() { this.apply(); }

  t(key: string): string {
    const e = DICT[key];
    return e ? e[this.lang()] : key;
  }

  toggle(): void {
    this.lang.set(this.lang() === 'ar' ? 'en' : 'ar');
    try { localStorage.setItem('lang', this.lang()); } catch { /* storage unavailable */ }
    this.apply();
  }

  locale(): string { return this.lang() === 'ar' ? 'ar-OM' : 'en-GB'; }

  private apply(): void {
    document.documentElement.lang = this.lang();
    document.documentElement.dir = this.dir();
  }

  private initial(): Lang {
    try { return localStorage.getItem('lang') === 'en' ? 'en' : 'ar'; } catch { return 'ar'; }
  }
}

/** Impure so labels update when the language is toggled. */
@Pipe({ name: 't', standalone: true, pure: false })
export class TPipe implements PipeTransform {
  constructor(private i18n: I18n) {}
  transform(key: string, prefix?: string): string {
    return this.i18n.t(prefix ? `${prefix}.${key}` : key);
  }
}
