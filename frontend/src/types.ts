export type ApplicationStatus =
  | 'INTERESTED'
  | 'APPLIED'
  | 'DOCUMENT_PASSED'
  | 'INTERVIEW'
  | 'ACCEPTED'
  | 'REJECTED';

export type EmploymentType = 'FULL_TIME' | 'FREELANCE' | 'OTHER';

export interface User {
  id: number;
  email: string;
  createdAt: string;
}

export interface TokenResponse {
  accessToken: string;
  tokenType: string;
  user: User;
}

export interface PostingSummary {
  id: number;
  company: string;
  title: string;
  location: string | null;
  employmentType: EmploymentType | null;
  deadline: string | null;
  status: ApplicationStatus;
  appliedAt: string | null;
  createdAt: string;
}

export interface PostingDetail extends PostingSummary {
  url: string | null;
  requirements: string | null;
  memo: string | null;
  updatedAt: string;
}

export interface PostingInput {
  company: string;
  title: string;
  url: string;
  location: string;
  employmentType: EmploymentType | null;
  deadline: string | null;
  requirements: string;
  memo: string;
}

export interface StatusHistory {
  id: number;
  fromStatus: ApplicationStatus | null;
  toStatus: ApplicationStatus;
  changedAt: string;
  /** 실수로 보고 바로 되돌린 변경 — 통계에서 제외 */
  reverted: boolean;
}

// ---------- 자기소개서 ----------

export type EssayCategory = 'MOTIVATION' | 'GROWTH' | 'COMPETENCY' | 'COLLABORATION' | 'OTHER';

export interface EssayAnswer {
  id: number;
  questionId: number;
  version: number;
  content: string;
  charCount: number;
  submitted: boolean;
  sourceTemplateId: number | null;
  createdAt: string;
}

export interface EssayQuestion {
  id: number;
  question: string;
  maxLength: number | null;
  category: EssayCategory;
  displayOrder: number;
  latestAnswer: EssayAnswer | null;
  submittedVersion: number | null;
  draft: { content: string; updatedAt: string } | null;
  locked: boolean;
}

export interface QuestionInput {
  question: string;
  maxLength: number | null;
  category: EssayCategory;
}

export interface AnswerTemplate {
  id: number;
  category: EssayCategory;
  title: string;
  content: string;
  createdAt: string;
  updatedAt: string;
}

export interface TemplateInput {
  category: EssayCategory;
  title: string;
  content: string;
}

export interface AnswerSearchResult {
  answerId: number;
  questionId: number;
  postingId: number;
  company: string;
  postingTitle: string;
  question: string;
  category: EssayCategory;
  version: number;
  content: string;
  charCount: number;
  submitted: boolean;
  createdAt: string;
}

// ---------- 통계 ----------

export interface Ratio {
  numerator: number;
  denominator: number;
  /** 분모가 0 이면 null — 0% 와 "데이터 없음" 구분 */
  rate: number | null;
}

export interface StageRate {
  decided: Ratio;
  overall: Ratio;
  pending: number;
}

export interface StatsSummary {
  totalPostings: number;
  totalApplied: number;
  documentPass: StageRate;
  interview: StageRate;
  finalAccept: StageRate;
  acceptedCount: number;
}

export interface FunnelStep {
  stage: string;
  label: string;
  count: number;
  conversion: Ratio | null;
}

export interface WeeklyPoint {
  weekStart: string;
  count: number;
}

export interface ResponseTime {
  count: number;
  averageDays: number | null;
  medianDays: number | null;
  waiting: number;
  buckets: { label: string; count: number }[];
}

export type BreakdownBy = 'employmentType' | 'location' | 'template';

export interface BreakdownRow {
  key: string;
  label: string;
  applied: number;
  passed: number;
  failed: number;
  pending: number;
  decided: Ratio;
  overall: Ratio;
}

export type ParseSource = 'SARAMIN' | 'JOBKOREA' | 'JSON_LD' | 'OPEN_GRAPH' | 'NONE';

export type ParsedField = 'company' | 'title' | 'location' | 'employmentType' | 'deadline' | 'requirements';

export interface ParseResult {
  success: boolean;
  source: ParseSource;
  data: {
    company: string | null;
    title: string | null;
    location: string | null;
    employmentType: EmploymentType | null;
    deadline: string | null;
    requirements: string | null;
  };
  missingFields: ParsedField[];
  message: string | null;
}

export type RemindDay = 7 | 3 | 1;

export interface NotificationSettings {
  emailEnabled: boolean;
  remindDays: RemindDay[];
  sendHour: number;
  /** 알림 받을 주소(가입 이메일, 읽기 전용) */
  email: string;
}

export interface PostingFilters {
  keyword: string;
  employmentType: EmploymentType | '';
  location: string;
}

// ---------- 면접 · 캘린더 ----------

export type InterviewType = 'FIRST' | 'SECOND' | 'EXECUTIVE' | 'CODING_TEST' | 'ASSIGNMENT';

export interface Interview {
  id: number;
  postingId: number;
  company: string;
  postingTitle: string;
  postingStatus: ApplicationStatus;
  type: InterviewType;
  startsAt: string;
  endsAt: string;
  durationMinutes: number;
  location: string | null;
  memo: string | null;
}

export interface InterviewInput {
  type: InterviewType;
  /** 오프셋 포함 ISO 시각 */
  startsAt: string;
  durationMinutes: number;
  location: string;
  memo: string;
}

export interface CalendarSubscription {
  url: string;
  issuedAt: string;
}
