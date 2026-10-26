// coverage/coverage-summary.json → 마크다운 표 (CI 의 Job Summary 에 붙인다)
//   node scripts/coverage-summary.mjs >> "$GITHUB_STEP_SUMMARY"
import { readFileSync } from 'node:fs';

const { total } = JSON.parse(readFileSync(new URL('../coverage/coverage-summary.json', import.meta.url), 'utf8'));
const pct = (key) => `${total[key].pct.toFixed(1)}%`;
console.log('### 프론트 커버리지 (Vitest · v8)');
console.log('| 라인 | 브랜치 | 함수 | 구문 |');
console.log('|---|---|---|---|');
console.log(`| ${pct('lines')} | ${pct('branches')} | ${pct('functions')} | ${pct('statements')} |`);
