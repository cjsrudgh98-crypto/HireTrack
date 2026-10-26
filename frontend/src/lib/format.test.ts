import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { daysUntil, formatDday, fromDatetimeLocal, toDatetimeLocal } from './format';
import { countText } from './textCount';

describe('마감 D-day', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-10-26T09:00:00+09:00'));
  });
  afterEach(() => vi.useRealTimers());

  it('오늘 기준 남은 일수를 센다', () => {
    expect(daysUntil('2026-10-26')).toBe(0);
    expect(daysUntil('2026-10-29')).toBe(3);
    expect(daysUntil('2026-10-25')).toBe(-1);
  });

  it('3일 이내는 긴급, 지난 마감은 "마감됨"', () => {
    expect(formatDday('2026-10-26')).toEqual({ text: 'D-Day', urgent: true, past: false });
    expect(formatDday('2026-10-29')).toEqual({ text: 'D-3', urgent: true, past: false });
    expect(formatDday('2026-10-30')).toEqual({ text: 'D-4', urgent: false, past: false });
    expect(formatDday('2026-10-20')).toEqual({ text: '마감됨', urgent: false, past: true });
  });
});

describe('면접 일시 입력 변환', () => {
  it('datetime-local 값을 시간대 오프셋이 붙은 ISO 로 바꾸고 되돌릴 수 있다', () => {
    expect(fromDatetimeLocal('2026-11-03T14:00')).toBe('2026-11-03T14:00:00+09:00');
    expect(toDatetimeLocal('2026-11-03T05:00:00Z')).toBe('2026-11-03T14:00');
  });
});

describe('자소서 글자 수', () => {
  it('공백 포함·제외·바이트(한글 2, 줄바꿈 2)를 센다', () => {
    expect(countText('두 번째 답변 abc')).toEqual({ withSpaces: 11, withoutSpaces: 8, bytes: 5 * 2 + 3 + 3 });
    expect(countText('가\n나').bytes).toBe(6);
  });

  it('이모지처럼 두 코드 유닛인 문자도 한 글자로 센다(서버 charCount 와 같은 기준)', () => {
    expect(countText('👍').withSpaces).toBe(1);
  });
});
