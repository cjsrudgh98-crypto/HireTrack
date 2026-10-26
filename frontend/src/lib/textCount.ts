/**
 * 자소서 글자 수. 회사마다 기준이 달라 세 가지를 모두 보여준다.
 * - 공백 포함 / 공백 제외: 유니코드 문자(코드 포인트) 기준 — 서버 charCount 와 같은 방식
 * - 바이트: 채용 사이트 글자 수 세기에서 흔히 쓰는 방식 — 한글 등 비ASCII 2바이트, ASCII 1바이트, 줄바꿈 2바이트(CRLF)
 */
export function countText(text: string) {
  const chars = [...text];
  let bytes = 0;
  for (const ch of chars) {
    if (ch === '\n') bytes += 2;
    else bytes += ch.charCodeAt(0) < 128 ? 1 : 2;
  }
  return {
    withSpaces: chars.length,
    withoutSpaces: chars.filter((ch) => !/\s/.test(ch)).length,
    bytes,
  };
}
