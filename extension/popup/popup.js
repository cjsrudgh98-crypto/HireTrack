// 팝업: 현재 탭 HTML 읽기(content.js) → 서버 추출 → 폼에 채워 보여주기 → 확인·수정 후 저장
const $ = (id) => document.getElementById(id);
const FIELDS = ['company', 'title', 'location', 'employmentType', 'deadline', 'requirements'];
const SOURCE_LABEL = { SARAMIN: '사람인', JOBKOREA: '잡코리아', JSON_LD: '구조화 데이터', OPEN_GRAPH: '페이지 메타 정보' };

async function send(type, payload = {}) {
  const res = await chrome.runtime.sendMessage({ type, ...payload });
  if (!res?.ok) throw Object.assign(new Error(res?.error?.message ?? '알 수 없는 오류'), res?.error ?? {});
  return res.result;
}

function setStatus(text, kind = 'info') {
  const el = $('status');
  el.textContent = text;
  el.className = `status status-${kind}`;
}

function render(state) {
  $('login-view').hidden = state.loggedIn;
  $('main-view').hidden = !state.loggedIn;
  $('logout').hidden = !state.loggedIn;
  $('who').textContent = state.loggedIn ? state.email : '';
  const settings = $('settings-form');
  settings.apiUrl.value = state.apiUrl;
  settings.appUrl.value = state.appUrl;
}

/** activeTab 권한: 사용자가 아이콘을 누른 지금 이 탭에만 content.js 를 주입할 수 있다 */
async function readCurrentTab() {
  const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
  if (!tab?.id || !/^https?:/.test(tab.url ?? '')) {
    throw new Error('이 페이지에서는 읽을 수 없어요. 채용 공고 페이지에서 눌러 주세요.');
  }
  const [injection] = await chrome.scripting.executeScript({ target: { tabId: tab.id }, files: ['content.js'] });
  return injection.result;
}

function fillForm(page, parsed) {
  const form = $('posting-form');
  form.url.value = page.url;
  for (const field of FIELDS) {
    const value = parsed?.data?.[field];
    if (value != null) form[field].value = value;
    const label = form.querySelector(`[data-field="${field}"]`);
    label.classList.toggle('auto', value != null);
    label.classList.toggle('missing', parsed?.success === true && value == null);
  }
  if (!form.company.value && !form.title.value && page.title) form.title.value = page.title;
  form.hidden = false;
}

async function extract() {
  setStatus('공고를 읽는 중…');
  let page;
  try {
    page = await readCurrentTab();
  } catch (e) {
    setStatus(e.message, 'error');
    return;
  }
  try {
    const parsed = await send('parse', { url: page.url, html: page.html });
    fillForm(page, parsed);
    if (parsed.success) {
      const filled = FIELDS.filter((f) => parsed.data[f] != null).length;
      setStatus(`${SOURCE_LABEL[parsed.source] ?? ''}에서 ${filled}개 항목을 채웠어요. 확인 후 저장하세요.`, 'ok');
    } else {
      setStatus(parsed.message ?? '자동 추출에 실패했어요. 직접 입력해 주세요.', 'error');
    }
    if (page.truncated) setStatus('페이지가 너무 커서 일부만 읽었어요. 내용을 확인해 주세요.', 'error');
  } catch (e) {
    fillForm(page, null);
    if (e.status === 401) return render(await send('state'));
    setStatus(e.message, 'error');
  }
}

$('login-form').addEventListener('submit', async (e) => {
  e.preventDefault();
  const form = e.currentTarget;
  try {
    render(await send('login', { email: form.email.value, password: form.password.value }));
    form.reset();
    await extract();
  } catch (err) {
    alert(err.message);
  }
});

$('logout').addEventListener('click', async () => render(await send('logout')));

$('settings-form').addEventListener('submit', async (e) => {
  e.preventDefault();
  const form = e.currentTarget;
  render(await send('saveSettings', { apiUrl: form.apiUrl.value.trim(), appUrl: form.appUrl.value.trim() }));
});

$('posting-form').addEventListener('submit', async (e) => {
  e.preventDefault();
  const form = e.currentTarget;
  const posting = Object.fromEntries(['company', 'title', 'url', 'location', 'employmentType', 'deadline', 'requirements']
    .map((f) => [f, form[f].value.trim() || null]));
  $('save').disabled = true;
  try {
    const saved = await send('save', { posting });
    form.hidden = true;
    setStatus('');
    const box = $('saved');
    box.hidden = false;
    box.innerHTML = '';
    const link = Object.assign(document.createElement('a'), { href: saved.link, target: '_blank', textContent: '보드에서 보기 →' });
    box.append('저장됨 · ', link);
  } catch (err) {
    setStatus(err.message, 'error');
    $('save').disabled = false;
  }
});

(async () => {
  const state = await send('state');
  render(state);
  if (state.loggedIn) await extract();
})();
