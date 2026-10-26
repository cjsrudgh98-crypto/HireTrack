import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { DiffView } from './DiffView';

describe('버전 비교', () => {
  it('추가된 글자는 ins, 지운 글자는 del 로 표시하고 글자 수를 센다', () => {
    const { container } = render(<DiffView before="장애 대응에 기여하겠습니다." after="장애 대응 자동화로 팀을 돕겠습니다." />);

    expect(container.querySelectorAll('ins').length).toBeGreaterThan(0);
    expect(container.querySelectorAll('del').length).toBeGreaterThan(0);
    // 화면에 보이는 글은 이전 글과 새 글을 모두 담는다(지운 부분 + 남은 부분 = 이전 글)
    const kept = [...container.querySelectorAll('.diff-body > span, .diff-body > del')].map((n) => n.textContent).join('');
    expect(kept).toBe('장애 대응에 기여하겠습니다.');
    const added = [...container.querySelectorAll('ins')].map((n) => [...(n.textContent ?? '')].length).reduce((a, b) => a + b, 0);
    expect(screen.getByText(`+${added}자`)).toBeInTheDocument();
  });

  it('내용이 같으면 +0자 −0자', () => {
    render(<DiffView before="같은 글" after="같은 글" />);

    expect(screen.getByText('+0자')).toBeInTheDocument();
    expect(screen.getByText('−0자')).toBeInTheDocument();
  });
});
