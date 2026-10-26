import { describe, expect, it } from 'vitest';
import { countText } from './textCount';

describe('자소서 글자 수', () => {
  it('한글은 2바이트, 영문·숫자·공백은 1바이트로 센다', () => {
    // 안녕(2×2) + 공백 + AI(2) + 공백 + 2026(4) = 12
    expect(countText('안녕 AI 2026')).toEqual({ withSpaces: 10, withoutSpaces: 8, bytes: 12 });
  });

  it('줄바꿈은 채용 사이트처럼 2바이트(CRLF)로, 공백 제외에서는 빠진다', () => {
    expect(countText('가\n나')).toEqual({ withSpaces: 3, withoutSpaces: 2, bytes: 6 });
  });

  it('이모지처럼 두 단위로 저장되는 문자도 한 글자로 센다 (서버 글자 수와 같은 기준)', () => {
    expect(countText('👍좋아요').withSpaces).toBe(4);
  });

  it('빈 문자열은 모두 0', () => {
    expect(countText('')).toEqual({ withSpaces: 0, withoutSpaces: 0, bytes: 0 });
  });
});
