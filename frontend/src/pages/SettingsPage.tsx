import { useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { settingsApi } from '../api';
import { CalendarSettings } from '../components/CalendarSettings';
import { Header } from '../components/Header';
import { useToast } from '../components/Toast';
import type { NotificationSettings, RemindDay } from '../types';

const DAY_OPTIONS: RemindDay[] = [7, 3, 1];
const HOURS = Array.from({ length: 24 }, (_, h) => h);

function hourLabel(hour: number) {
  if (hour === 0) return '오전 12시 (자정)';
  if (hour === 12) return '오후 12시 (정오)';
  return hour < 12 ? `오전 ${hour}시` : `오후 ${hour - 12}시`;
}

export function SettingsPage() {
  const toast = useToast();
  const queryClient = useQueryClient();
  const { data, isPending, error } = useQuery({ queryKey: ['notification-settings'], queryFn: settingsApi.get });
  // 서버 값을 state 로 복사하지 않고, 사용자가 고친 값이 있으면 그걸, 없으면 서버 값을 보여준다
  const [edited, setEdited] = useState<NotificationSettings | null>(null);
  const form = edited ?? data ?? null;
  const setForm = (next: NotificationSettings) => setEdited(next);

  const save = useMutation({
    mutationFn: (settings: NotificationSettings) =>
      settingsApi.update({ emailEnabled: settings.emailEnabled, remindDays: settings.remindDays, sendHour: settings.sendHour }),
    onSuccess: (saved) => {
      queryClient.setQueryData(['notification-settings'], saved);
      setEdited(null);
      toast('알림 설정을 저장했어요.');
    },
    onError: (err) => toast(`저장 실패: ${err.message}`),
  });

  const toggleDay = (day: RemindDay) =>
    form &&
    setForm({
      ...form,
      remindDays: form.remindDays.includes(day)
        ? form.remindDays.filter((d) => d !== day)
        : [...form.remindDays, day].sort((a, b) => b - a),
    });

  const noDays = form?.emailEnabled && form.remindDays.length === 0;
  const dirty = Boolean(form && data && JSON.stringify(form) !== JSON.stringify(data));

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault();
    if (form && !noDays) save.mutate(form);
  };

  return (
    <div className="page">
      <Header />
      <div className="detail settings">
        <Link to="/" className="back-link">
          ← 보드로
        </Link>
        <h1 className="settings-title">설정</h1>
        <h2 className="settings-section">알림</h2>

        {isPending && <div className="loading">불러오는 중…</div>}
        {error && <p className="form-error">설정을 불러오지 못했습니다: {error.message}</p>}

        {form && (
          <form className="panel settings-panel" onSubmit={handleSubmit}>
            <div className="setting-row">
              <div>
                <div className="setting-name">마감 임박 이메일 알림</div>
                <div className="setting-desc">
                  지원 전(관심) 공고의 마감이 다가오면 <strong>{form.email}</strong> 로 하루 한 통 모아서 보내드려요.
                </div>
              </div>
              <label className="switch">
                <input
                  type="checkbox"
                  checked={form.emailEnabled}
                  onChange={(e) => setForm({ ...form, emailEnabled: e.target.checked })}
                  aria-label="이메일 알림 켜기"
                />
                <span className="switch-track" />
              </label>
            </div>

            <fieldset className="setting-row setting-block" disabled={!form.emailEnabled}>
              <legend className="setting-name">알림 시점</legend>
              <div className="chip-group">
                {DAY_OPTIONS.map((day) => (
                  <label key={day} className={`chip ${form.remindDays.includes(day) ? 'chip-on' : ''}`}>
                    <input type="checkbox" checked={form.remindDays.includes(day)} onChange={() => toggleDay(day)} />
                    D-{day}
                  </label>
                ))}
              </div>
              {noDays && <p className="form-error">알림 시점을 하나 이상 골라 주세요.</p>}
            </fieldset>

            <fieldset className="setting-row setting-block" disabled={!form.emailEnabled}>
              <legend className="setting-name">알림 받을 시각</legend>
              <select
                className="hour-select"
                value={form.sendHour}
                onChange={(e) => setForm({ ...form, sendHour: Number(e.target.value) })}
              >
                {HOURS.map((h) => (
                  <option key={h} value={h}>
                    {hourLabel(h)}
                  </option>
                ))}
              </select>
              <p className="hint">한국 시간 기준이에요.</p>
            </fieldset>

            <div className="settings-footer">
              <button type="submit" className="btn btn-primary" disabled={!dirty || noDays || save.isPending}>
                {save.isPending ? '저장 중…' : '저장'}
              </button>
            </div>
          </form>
        )}

        <h2 className="settings-section">캘린더</h2>
        <CalendarSettings />
      </div>
    </div>
  );
}
