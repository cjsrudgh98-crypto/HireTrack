import { request } from './client';
import type {
  AnswerSearchResult,
  AnswerTemplate,
  ApplicationStatus,
  BreakdownBy,
  BreakdownRow,
  CalendarSubscription,
  EssayAnswer,
  EssayCategory,
  EssayQuestion,
  FunnelStep,
  Interview,
  InterviewInput,
  QuestionInput,
  ResponseTime,
  StatsSummary,
  TemplateInput,
  WeeklyPoint,
  NotificationSettings,
  ParseResult,
  PostingDetail,
  PostingFilters,
  PostingInput,
  PostingSummary,
  StatusHistory,
  TokenResponse,
  User,
} from '../types';

export const authApi = {
  signup: (email: string, password: string) =>
    request<TokenResponse>('POST', '/api/auth/signup', { email, password }),
  login: (email: string, password: string) =>
    request<TokenResponse>('POST', '/api/auth/login', { email, password }),
  me: () => request<User>('GET', '/api/users/me'),
};

export const postingApi = {
  list: (filters: PostingFilters) => {
    const params = new URLSearchParams();
    if (filters.keyword.trim()) params.set('keyword', filters.keyword.trim());
    if (filters.employmentType) params.set('employmentType', filters.employmentType);
    if (filters.location) params.set('location', filters.location);
    const query = params.toString();
    return request<PostingSummary[]>('GET', `/api/postings${query ? `?${query}` : ''}`);
  },
  get: (id: number) => request<PostingDetail>('GET', `/api/postings/${id}`),
  create: (input: PostingInput) => request<PostingDetail>('POST', '/api/postings', input),
  update: (id: number, input: PostingInput) => request<PostingDetail>('PUT', `/api/postings/${id}`, input),
  remove: (id: number) => request<void>('DELETE', `/api/postings/${id}`),
  changeStatus: (id: number, status: ApplicationStatus) =>
    request<PostingDetail>('PATCH', `/api/postings/${id}/status`, { status }),
  updateMemo: (id: number, memo: string) =>
    request<PostingDetail>('PATCH', `/api/postings/${id}/memo`, { memo }),
  history: (id: number) => request<StatusHistory[]>('GET', `/api/postings/${id}/history`),
  /** URL 에서 공고 정보를 추출만 한다(저장하지 않음) */
  parse: (url: string) => request<ParseResult>('POST', '/api/postings/parse', { url }),
};

export const essayApi = {
  questions: (postingId: number) => request<EssayQuestion[]>('GET', `/api/postings/${postingId}/questions`),
  addQuestion: (postingId: number, input: QuestionInput) =>
    request<EssayQuestion>('POST', `/api/postings/${postingId}/questions`, input),
  updateQuestion: (questionId: number, input: QuestionInput) =>
    request<EssayQuestion>('PUT', `/api/questions/${questionId}`, input),
  deleteQuestion: (questionId: number) => request<void>('DELETE', `/api/questions/${questionId}`),
  versions: (questionId: number) => request<EssayAnswer[]>('GET', `/api/questions/${questionId}/answers`),
  /** baseVersion: 보고 있던 최신 버전(없으면 null). 서버 최신과 다르면 409 VERSION_CONFLICT */
  saveVersion: (questionId: number, content: string, baseVersion: number | null, sourceTemplateId: number | null) =>
    request<EssayAnswer>('POST', `/api/questions/${questionId}/answers`, { content, baseVersion, sourceTemplateId }),
  submit: (questionId: number, version: number) =>
    request<EssayAnswer>('POST', `/api/questions/${questionId}/answers/${version}/submit`),
  saveDraft: (questionId: number, content: string) =>
    request<{ content: string; updatedAt: string }>('PUT', `/api/questions/${questionId}/draft`, { content }),
  search: (params: { keyword?: string; category?: EssayCategory; excludeQuestionId?: number }) => {
    const query = new URLSearchParams();
    if (params.keyword?.trim()) query.set('keyword', params.keyword.trim());
    if (params.category) query.set('category', params.category);
    if (params.excludeQuestionId) query.set('excludeQuestionId', String(params.excludeQuestionId));
    return request<AnswerSearchResult[]>('GET', `/api/answers/search?${query}`);
  },
};

export const templateApi = {
  list: (category?: EssayCategory) =>
    request<AnswerTemplate[]>('GET', `/api/templates${category ? `?category=${category}` : ''}`),
  create: (input: TemplateInput) => request<AnswerTemplate>('POST', '/api/templates', input),
  update: (id: number, input: TemplateInput) => request<AnswerTemplate>('PUT', `/api/templates/${id}`, input),
  remove: (id: number) => request<void>('DELETE', `/api/templates/${id}`),
};

export const statsApi = {
  summary: () => request<StatsSummary>('GET', '/api/stats/summary'),
  funnel: () => request<FunnelStep[]>('GET', '/api/stats/funnel'),
  weekly: (from?: string) => request<WeeklyPoint[]>('GET', `/api/stats/weekly${from ? `?from=${from}` : ''}`),
  responseTime: () => request<ResponseTime>('GET', '/api/stats/response-time'),
  breakdown: (by: BreakdownBy) =>
    request<{ by: BreakdownBy; rows: BreakdownRow[] }>('GET', `/api/stats/breakdown?by=${by}`),
};

export const interviewApi = {
  list: (postingId: number) => request<Interview[]>('GET', `/api/postings/${postingId}/interviews`),
  create: (postingId: number, input: InterviewInput) =>
    request<Interview>('POST', `/api/postings/${postingId}/interviews`, input),
  update: (id: number, input: InterviewInput) => request<Interview>('PUT', `/api/interviews/${id}`, input),
  remove: (id: number) => request<void>('DELETE', `/api/interviews/${id}`),
  upcoming: () => request<Interview[]>('GET', '/api/interviews/upcoming'),
};

export const calendarApi = {
  subscription: () => request<CalendarSubscription>('GET', '/api/users/me/calendar'),
  regenerate: () => request<CalendarSubscription>('POST', '/api/users/me/calendar/regenerate'),
};

export const settingsApi = {
  get: () => request<NotificationSettings>('GET', '/api/users/me/notification-settings'),
  update: (settings: Omit<NotificationSettings, 'email'>) =>
    request<NotificationSettings>('PUT', '/api/users/me/notification-settings', settings),
};
