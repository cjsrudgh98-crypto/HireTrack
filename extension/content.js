// 현재 탭에 주입되어, 사용자 브라우저에 "이미 렌더링된" 화면의 HTML 을 돌려준다.
// 자바스크립트로 그린 공고, 로그인해야 보이는 공고도 여기서는 보인다 — 서버 추출(2단계)의 한계를 보완.
// chrome.scripting.executeScript({ files: ['content.js'] }) 의 결과값 = 이 파일 마지막 식의 값.
(() => {
  const MAX_CHARS = 2 * 1024 * 1024; // 서버 상한(2MB)과 맞춤

  // 파싱에 필요 없는 큰 덩어리는 빼고 보낸다. 구조화 데이터(JSON-LD)는 파서가 쓰므로 남긴다.
  const clone = document.documentElement.cloneNode(true);
  clone
    .querySelectorAll('script:not([type="application/ld+json"]), style, noscript, svg, iframe, template, link[rel="stylesheet"]')
    .forEach((el) => el.remove());

  let html = '<!DOCTYPE html>' + clone.outerHTML;
  const truncated = html.length > MAX_CHARS;
  if (truncated) html = html.slice(0, MAX_CHARS);

  return { url: location.href, title: document.title, html, truncated };
})();
