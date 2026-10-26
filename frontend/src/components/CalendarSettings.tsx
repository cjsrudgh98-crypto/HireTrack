import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { calendarApi } from '../api';
import { formatDateTime } from '../lib/format';
import { useToast } from './Toast';

/** 설정 > 캘린더 구독 URL 복사·재발급 */
export function CalendarSettings() {
  const toast = useToast();
  const queryClient = useQueryClient();
  const { data, error } = useQuery({ queryKey: ['calendar'], queryFn: calendarApi.subscription });
  const regenerate = useMutation({
    mutationFn: calendarApi.regenerate,
    onSuccess: (sub) => {
      queryClient.setQueryData(['calendar'], sub);
      toast('새 구독 URL 을 발급했어요. 캘린더 앱에 다시 등록해 주세요.');
    },
    onError: (e) => toast(e.message),
  });

  const copy = async () => {
    if (!data) return;
    try {
      await navigator.clipboard.writeText(data.url);
      toast('구독 URL 을 복사했어요.');
    } catch {
      toast('복사하지 못했어요. 주소를 직접 선택해 복사해 주세요.');
    }
  };

  return (
    <section className="panel settings-panel">
      <div className="setting-row setting-block">
        <div>
          <div className="setting-name">캘린더 구독</div>
          <div className="setting-desc">
            지원 전 공고의 마감일과 면접 일정을 구글·애플·아웃룩 캘린더에서 볼 수 있어요. 면접은 1시간 전에 알림이 울립니다.
          </div>
        </div>
        {error && <p className="form-error">{error.message}</p>}
        {data && (
          <>
            <div className="calendar-url">
              <input readOnly value={data.url} onFocus={(e) => e.currentTarget.select()} aria-label="캘린더 구독 URL" />
              <button className="btn btn-primary btn-sm" onClick={copy}>
                URL 복사
              </button>
            </div>
            <p className="hint">
              구글 캘린더: 다른 캘린더 + → URL로 추가 / 애플 캘린더: 파일 → 새로운 캘린더 구독. 캘린더 앱이 몇 시간마다 새로 읽어 가서 반영이
              늦을 수 있어요.
            </p>
            <p className="hint">
              이 주소를 아는 사람은 누구나 일정을 볼 수 있어요. 유출됐다면 재발급하세요 (발급 {formatDateTime(data.issuedAt)}).{' '}
              <button
                className="link-btn small"
                onClick={() =>
                  window.confirm('새 URL 을 발급하면 지금 URL 로 구독한 캘린더는 더 이상 갱신되지 않아요. 계속할까요?') &&
                  regenerate.mutate()
                }
              >
                URL 재발급
              </button>
            </p>
          </>
        )}
      </div>
    </section>
  );
}
