import { useState, type ReactNode } from 'react';
import { useQuery } from '@tanstack/react-query';
import {
  Area,
  AreaChart,
  Bar,
  BarChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { statsApi } from '../api';
import { Header } from '../components/Header';
import type { BreakdownBy, Ratio, StageRate } from '../types';

/** "33% (1/3)" — 표본이 작을 때도 의미를 오해하지 않도록 항상 건수를 같이 쓴다 */
function formatRatio(r: Ratio | null | undefined) {
  if (!r || r.rate === null) return '—';
  return `${Math.round(r.rate * 100)}% (${r.numerator}/${r.denominator})`;
}

const BREAKDOWN_OPTIONS: { value: BreakdownBy; label: string }[] = [
  { value: 'employmentType', label: '고용형태' },
  { value: 'location', label: '지역' },
  { value: 'template', label: '자소서 템플릿' },
];

// 차트 공통: 2px 선, 24px 이하 막대, 실선 hairline 그리드, 축 텍스트는 보조 잉크
const AXIS = { tick: { fill: 'var(--text-2)', fontSize: 12 }, axisLine: false, tickLine: false } as const;
const GRID = <CartesianGrid stroke="var(--border)" strokeDasharray="" vertical={false} />;

function ChartTooltip({ active, payload, label, format }: {
  active?: boolean;
  payload?: { value: number; payload: Record<string, unknown> }[];
  label?: string;
  format: (value: number, row: Record<string, unknown>) => string;
}) {
  if (!active || !payload?.length) return null;
  return (
    <div className="chart-tooltip">
      <div className="chart-tooltip-label">{label}</div>
      <div>{format(payload[0].value, payload[0].payload)}</div>
    </div>
  );
}

function Card({ title, subtitle, action, children, table }: {
  title: string;
  subtitle?: ReactNode;
  action?: ReactNode;
  children: ReactNode;
  table?: ReactNode;
}) {
  return (
    <section className="panel chart-card">
      <div className="chart-head">
        <div>
          <h2>{title}</h2>
          {subtitle && <p className="muted">{subtitle}</p>}
        </div>
        {action}
      </div>
      {children}
      {table && (
        <details className="chart-table">
          <summary>표로 보기</summary>
          {table}
        </details>
      )}
    </section>
  );
}

/**
 * 가로 막대 목록. 0건·데이터 없음 행도 라벨이 항상 보여야 해서(차트 라이브러리는 폭 0 막대의 라벨을 생략)
 * HTML 로 그린다. 막대는 24px 두께, 끝만 둥글게, 값 텍스트는 막대 색이 아닌 본문 잉크.
 */
function HBars({ rows }: { rows: { key: string; label: string; ratio: number | null; text: string; detail: string }[] }) {
  return (
    <ul className="hbars">
      {rows.map((r) => (
        <li key={r.key} className="hbar-row" title={`${r.label}: ${r.detail}`}>
          <span className="hbar-label">{r.label}</span>
          <span className="hbar-track">
            {r.ratio !== null && r.ratio > 0 && <span className="hbar-fill" style={{ width: `calc((100% - 140px) * ${r.ratio})` }} />}
            <span className="hbar-text">{r.text}</span>
          </span>
          <span className="hbar-tip" role="tooltip">{r.detail}</span>
        </li>
      ))}
    </ul>
  );
}

function StatTile({ label, value, sub, hint }: { label: string; value: string; sub?: ReactNode; hint?: string }) {
  return (
    <div className="stat-tile" title={hint}>
      <div className="stat-label">{label}</div>
      <div className="stat-value">{value}</div>
      {sub && <div className="stat-sub">{sub}</div>}
    </div>
  );
}

function rateTile(label: string, stage: StageRate | undefined, hint: string) {
  return (
    <StatTile
      label={label}
      value={stage?.decided.rate == null ? '—' : `${Math.round(stage.decided.rate * 100)}%`}
      hint={hint}
      sub={
        stage && (
          <>
            결과 나온 {stage.decided.denominator}건 중 {stage.decided.numerator}건
            <br />
            전체 기준 {formatRatio(stage.overall)}
            {stage.pending > 0 && ` · 대기 ${stage.pending}건`}
          </>
        )
      }
    />
  );
}

export function DashboardPage() {
  const [by, setBy] = useState<BreakdownBy>('employmentType');
  const summary = useQuery({ queryKey: ['stats', 'summary'], queryFn: statsApi.summary });
  const funnel = useQuery({ queryKey: ['stats', 'funnel'], queryFn: statsApi.funnel });
  const weekly = useQuery({ queryKey: ['stats', 'weekly'], queryFn: () => statsApi.weekly() });
  const response = useQuery({ queryKey: ['stats', 'response-time'], queryFn: statsApi.responseTime });
  const breakdown = useQuery({ queryKey: ['stats', 'breakdown', by], queryFn: () => statsApi.breakdown(by) });

  const s = summary.data;
  const weeklyData = weekly.data?.map((p) => ({
    ...p,
    label: `${Number(p.weekStart.slice(5, 7))}/${Number(p.weekStart.slice(8, 10))}`,
  }));
  const funnelMax = Math.max(1, ...(funnel.data?.map((f) => f.count) ?? [0]));
  const funnelRows = funnel.data?.map((f) => ({
    key: f.stage,
    label: f.label,
    ratio: f.count / funnelMax,
    text: `${f.count}건${f.conversion ? ` · ${formatRatio(f.conversion)}` : ''}`,
    detail: `${f.count}건 도달${f.conversion ? `, 직전 단계 대비 ${formatRatio(f.conversion)}` : ''}`,
  }));
  const breakdownRows = breakdown.data?.rows.map((r) => ({
    key: r.key,
    label: r.label,
    ratio: r.decided.rate,
    text: r.decided.rate === null ? `— · 대기 ${r.pending}건` : `${formatRatio(r.decided)}${r.pending ? ` · 대기 ${r.pending}` : ''}`,
    detail: `지원 ${r.applied}건 · 통과 ${r.passed} · 탈락 ${r.failed} · 대기 ${r.pending} · 전체 기준 ${formatRatio(r.overall)}`,
  }));

  return (
    <div className="page">
      <Header />
      <div className="detail dashboard">
        <h1 className="settings-title">지원 통계</h1>
        {summary.error && <p className="form-error">통계를 불러오지 못했습니다: {summary.error.message}</p>}

        <div className="stat-grid">
          <StatTile label="총 지원" value={s ? `${s.totalApplied}건` : '—'} sub={s && `관심 포함 전체 ${s.totalPostings}건`} />
          {rateTile('서류 통과율', s?.documentPass, '지원한 공고 중 서류 합격 이상 단계에 도달한 비율 (현재 상태가 아니라 도달 여부 기준)')}
          {rateTile('면접 전환율', s?.interview, '서류 합격한 공고 중 면접 단계에 도달한 비율')}
          <StatTile
            label="최종 합격"
            value={s ? `${s.acceptedCount}건` : '—'}
            sub={s && <>면접 후 합격률 {formatRatio(s.finalAccept.decided)}{s.finalAccept.pending > 0 && ` · 대기 ${s.finalAccept.pending}건`}</>}
          />
        </div>
        <p className="hint">
          통과율은 <strong>결과가 나온 건 기준</strong>이에요(결과 대기 중인 건은 분모에서 빼요). 면접까지 갔다가 떨어진 공고도 서류 통과로 셉니다.
          바로 되돌린 상태 변경은 제외돼요.
        </p>

        <div className="chart-grid">
          <Card
            title="지원 퍼널"
            subtitle="각 단계에 한 번이라도 도달한 공고 수"
            table={
              <table>
                <thead><tr><th>단계</th><th>도달</th><th>직전 대비</th></tr></thead>
                <tbody>
                  {funnel.data?.map((f) => (
                    <tr key={f.stage}><td>{f.label}</td><td>{f.count}</td><td>{formatRatio(f.conversion)}</td></tr>
                  ))}
                </tbody>
              </table>
            }
          >
            <HBars rows={funnelRows ?? []} />
          </Card>

          <Card
            title="주간 지원 추이"
            subtitle="최근 12주, 주(월요일 시작)별 지원 수"
            table={
              <table>
                <thead><tr><th>주 시작</th><th>지원</th></tr></thead>
                <tbody>{weekly.data?.map((p) => <tr key={p.weekStart}><td>{p.weekStart}</td><td>{p.count}</td></tr>)}</tbody>
              </table>
            }
          >
            <ResponsiveContainer width="100%" height={200}>
              <AreaChart data={weeklyData ?? []} margin={{ left: -16, right: 12, top: 8, bottom: 0 }}>
                {GRID}
                <XAxis dataKey="label" {...AXIS} interval="preserveStartEnd" minTickGap={16} />
                <YAxis allowDecimals={false} {...AXIS} width={40} />
                <Tooltip
                  cursor={{ stroke: 'var(--text-3)', strokeWidth: 1 }}
                  content={<ChartTooltip format={(v) => `${v}건 지원`} />}
                  labelFormatter={(l) => `${l} 주`}
                />
                <Area
                  type="linear"
                  dataKey="count"
                  stroke="var(--series-1)"
                  strokeWidth={2}
                  fill="var(--series-1)"
                  fillOpacity={0.1}
                  dot={{ r: 4, fill: 'var(--series-1)', stroke: 'var(--surface)', strokeWidth: 2 }}
                  activeDot={{ r: 5, stroke: 'var(--surface)', strokeWidth: 2 }}
                  isAnimationActive={false}
                />
              </AreaChart>
            </ResponsiveContainer>
          </Card>

          <Card
            title="응답까지 걸린 기간"
            subtitle={
              response.data && response.data.count > 0 ? (
                <>
                  지원 후 첫 결과까지 · 평균 <strong>{response.data.averageDays!.toFixed(1)}일</strong> · 중앙값{' '}
                  <strong>{response.data.medianDays}일</strong> ({response.data.count}건)
                  {response.data.waiting > 0 && ` · 응답 대기 ${response.data.waiting}건`}
                </>
              ) : (
                '아직 결과가 나온 지원이 없어요.'
              )
            }
            table={
              <table>
                <thead><tr><th>기간</th><th>건수</th></tr></thead>
                <tbody>{response.data?.buckets.map((b) => <tr key={b.label}><td>{b.label}</td><td>{b.count}</td></tr>)}</tbody>
              </table>
            }
          >
            <ResponsiveContainer width="100%" height={200}>
              <BarChart data={response.data?.buckets ?? []} margin={{ left: -16, right: 12, top: 20, bottom: 0 }}>
                {GRID}
                <XAxis dataKey="label" {...AXIS} />
                <YAxis allowDecimals={false} {...AXIS} width={40} />
                <Tooltip cursor={{ fill: 'var(--surface-2)' }} content={<ChartTooltip format={(v) => `${v}건`} />} />
                <Bar dataKey="count" fill="var(--series-1)" radius={[4, 4, 0, 0]} maxBarSize={24} isAnimationActive={false} />
              </BarChart>
            </ResponsiveContainer>
          </Card>

          <Card
            title="조건별 서류 통과율"
            subtitle="결과가 나온 건 기준 · (통과/결과 나온 건)"
            table={
              <table>
                <thead><tr><th>구분</th><th>지원</th><th>통과</th><th>탈락</th><th>대기</th><th>통과율</th><th>전체 기준</th></tr></thead>
                <tbody>
                  {breakdown.data?.rows.map((r) => (
                    <tr key={r.key}>
                      <td>{r.label}</td><td>{r.applied}</td><td>{r.passed}</td><td>{r.failed}</td><td>{r.pending}</td>
                      <td>{formatRatio(r.decided)}</td><td>{formatRatio(r.overall)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            }
            action={
              <select className="chart-filter" value={by} onChange={(e) => setBy(e.target.value as BreakdownBy)} aria-label="구분 기준">
                {BREAKDOWN_OPTIONS.map((o) => (
                  <option key={o.value} value={o.value}>{o.label}별</option>
                ))}
              </select>
            }
          >
            {breakdownRows?.length === 0 ? (
              <p className="muted chart-empty">지원한 공고가 생기면 보여드릴게요.</p>
            ) : (
              <HBars rows={breakdownRows ?? []} />
            )}
          </Card>
        </div>
      </div>
    </div>
  );
}
