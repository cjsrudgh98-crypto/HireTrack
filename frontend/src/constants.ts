import type { ApplicationStatus, EmploymentType, EssayCategory, InterviewType, ParseSource } from './types';

export const STATUS_LABEL: Record<ApplicationStatus, string> = {
  INTERESTED: '관심',
  APPLIED: '지원 완료',
  DOCUMENT_PASSED: '서류 합격',
  INTERVIEW: '면접 진행',
  ACCEPTED: '최종 합격',
  REJECTED: '불합격',
};

export const STATUS_ORDER: ApplicationStatus[] = [
  'INTERESTED',
  'APPLIED',
  'DOCUMENT_PASSED',
  'INTERVIEW',
  'ACCEPTED',
  'REJECTED',
];

/** 칸반 컬럼. '결과' 컬럼은 합격/불합격 두 개의 드롭 영역을 가진다. */
export const KANBAN_COLUMNS: { title: string; statuses: ApplicationStatus[] }[] = [
  { title: '관심', statuses: ['INTERESTED'] },
  { title: '지원 완료', statuses: ['APPLIED'] },
  { title: '서류 합격', statuses: ['DOCUMENT_PASSED'] },
  { title: '면접', statuses: ['INTERVIEW'] },
  { title: '결과', statuses: ['ACCEPTED', 'REJECTED'] },
];

export const EMPLOYMENT_LABEL: Record<EmploymentType, string> = {
  FULL_TIME: '정규직',
  FREELANCE: '프리랜서',
  OTHER: '기타',
};

export const PARSE_SOURCE_LABEL: Record<ParseSource, string> = {
  SARAMIN: '사람인',
  JOBKOREA: '잡코리아',
  JSON_LD: '페이지 구조화 데이터',
  OPEN_GRAPH: '페이지 메타 정보',
  NONE: '',
};

export const ESSAY_CATEGORY_LABEL: Record<EssayCategory, string> = {
  MOTIVATION: '지원동기',
  GROWTH: '성장과정',
  COMPETENCY: '직무역량',
  COLLABORATION: '협업경험',
  OTHER: '기타',
};

export const ESSAY_CATEGORIES = Object.keys(ESSAY_CATEGORY_LABEL) as EssayCategory[];

export const INTERVIEW_TYPE_LABEL: Record<InterviewType, string> = {
  FIRST: '1차 면접',
  SECOND: '2차 면접',
  EXECUTIVE: '임원 면접',
  CODING_TEST: '코딩테스트',
  ASSIGNMENT: '과제',
};

export const REGIONS = [
  '서울', '경기', '인천', '부산', '대구', '대전', '광주', '울산', '세종',
  '강원', '충북', '충남', '전북', '전남', '경북', '경남', '제주', '원격',
];
