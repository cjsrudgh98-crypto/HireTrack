const DAY_MS = 24 * 60 * 60 * 1000;

function startOfToday(): number {
  const now = new Date();
  return new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime();
}

/** 'YYYY-MM-DD' 마감일까지 남은 일수 (오늘 = 0, 지났으면 음수). */
export function daysUntil(deadline: string): number {
  const [y, m, d] = deadline.split('-').map(Number);
  return Math.round((new Date(y, m - 1, d).getTime() - startOfToday()) / DAY_MS);
}

export function formatDday(deadline: string): { text: string; urgent: boolean; past: boolean } {
  const days = daysUntil(deadline);
  if (days < 0) return { text: '마감됨', urgent: false, past: true };
  return { text: days === 0 ? 'D-Day' : `D-${days}`, urgent: days <= 3, past: false };
}

/** ISO 시각 → 'MM/DD' */
export function formatShortDate(iso: string): string {
  const date = new Date(iso);
  return `${date.getMonth() + 1}/${date.getDate()}`;
}

const pad2 = (n: number) => String(n).padStart(2, '0');
const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'];

/** '11/3(화) 14:00' */
export function formatSchedule(iso: string): string {
  const d = new Date(iso);
  return `${d.getMonth() + 1}/${d.getDate()}(${WEEKDAYS[d.getDay()]}) ${pad2(d.getHours())}:${pad2(d.getMinutes())}`;
}

/** ISO 시각 → <input type="datetime-local"> 값 (브라우저 시간대 기준) */
export function toDatetimeLocal(iso: string): string {
  const d = new Date(iso);
  return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}T${pad2(d.getHours())}:${pad2(d.getMinutes())}`;
}

/** <input type="datetime-local"> 값 → 오프셋 포함 ISO ('2026-11-03T14:00:00+09:00') — 서버가 시각을 정확히 알도록 */
export function fromDatetimeLocal(value: string): string {
  const d = new Date(value);
  const offset = -d.getTimezoneOffset();
  const sign = offset >= 0 ? '+' : '-';
  const abs = Math.abs(offset);
  return `${value.length === 16 ? `${value}:00` : value}${sign}${pad2(Math.floor(abs / 60))}:${pad2(abs % 60)}`;
}

export function formatDateTime(iso: string): string {
  const date = new Date(iso);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}.${pad(date.getMonth() + 1)}.${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
}
