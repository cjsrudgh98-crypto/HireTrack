// 서비스 워커: API 호출과 토큰 관리. 팝업은 메시지로 요청만 보낸다.
// 토큰은 확장 프로그램 전용(공고 추출·등록만 가능한) 범위 제한 토큰이라, 새어 나가도 자소서·통계는 볼 수 없다.

const DEFAULTS = {
  apiUrl: 'http://localhost:8080', // 운영: https://<도메인>
  appUrl: 'http://localhost:5173', // "보드에서 보기" 링크 (운영은 보통 apiUrl 과 같음)
};

async function settings() {
  const stored = await chrome.storage.local.get(['apiUrl', 'appUrl', 'token', 'email']);
  return { ...DEFAULTS, ...stored };
}

async function api(path, { method = 'GET', body, auth = true } = {}) {
  const { apiUrl, token } = await settings();
  const headers = { 'Content-Type': 'application/json' };
  if (auth && token) headers.Authorization = `Bearer ${token}`;

  let response;
  try {
    response = await fetch(apiUrl.replace(/\/$/, '') + path, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    throw new Error(`서버(${apiUrl})에 연결할 수 없어요. 설정의 서버 주소를 확인해 주세요.`);
  }
  const data = await response.json().catch(() => null);
  if (!response.ok) {
    if (response.status === 401 && auth) await chrome.storage.local.remove(['token', 'email']);
    const error = new Error(data?.message ?? `요청 실패 (HTTP ${response.status})`);
    error.code = data?.code;
    error.status = response.status;
    throw error;
  }
  return data;
}

const handlers = {
  async state() {
    const { token, email, apiUrl, appUrl } = await settings();
    return { loggedIn: Boolean(token), email, apiUrl, appUrl };
  },

  async saveSettings({ apiUrl, appUrl }) {
    await chrome.storage.local.set({ apiUrl, appUrl });
    return handlers.state();
  },

  async login({ email, password }) {
    const res = await api('/api/auth/extension/login', { method: 'POST', body: { email, password }, auth: false });
    await chrome.storage.local.set({ token: res.accessToken, email: res.user.email });
    return handlers.state();
  },

  async logout() {
    await chrome.storage.local.remove(['token', 'email']);
    return handlers.state();
  },

  /** 렌더링된 HTML 을 서버로 보내 2단계와 같은 파서로 추출 (서버는 외부 요청을 보내지 않음) */
  async parse({ url, html }) {
    return api('/api/postings/parse-html', { method: 'POST', body: { url, html } });
  },

  async save({ posting }) {
    const saved = await api('/api/postings', { method: 'POST', body: posting });
    const { appUrl } = await settings();
    return { id: saved.id, link: `${appUrl.replace(/\/$/, '')}/postings/${saved.id}` };
  },
};

chrome.runtime.onMessage.addListener((message, _sender, sendResponse) => {
  const handler = handlers[message?.type];
  if (!handler) return false;
  handler(message)
    .then((result) => sendResponse({ ok: true, result }))
    .catch((error) => sendResponse({ ok: false, error: { message: error.message, code: error.code, status: error.status } }));
  return true; // 비동기 응답
});
