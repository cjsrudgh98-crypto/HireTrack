import { useMemo } from 'react';
import DiffMatchPatch from 'diff-match-patch';

const dmp = new DiffMatchPatch();

/**
 * 문자 단위 비교. 한국어는 띄어쓰기 단위보다 문자 단위가 자연스럽다.
 * cleanupSemantic 으로 한 글자씩 쪼개진 잡음을 사람이 읽기 좋은 덩어리로 합친다.
 */
export function DiffView({ before, after }: { before: string; after: string }) {
  const diffs = useMemo(() => {
    const result = dmp.diff_main(before, after);
    dmp.diff_cleanupSemantic(result);
    return result;
  }, [before, after]);

  const stats = useMemo(() => {
    let added = 0;
    let removed = 0;
    for (const [op, text] of diffs) {
      if (op === DiffMatchPatch.DIFF_INSERT) added += [...text].length;
      if (op === DiffMatchPatch.DIFF_DELETE) removed += [...text].length;
    }
    return { added, removed };
  }, [diffs]);

  return (
    <div className="diff">
      <div className="diff-stats">
        <span className="diff-stat-add">+{stats.added}자</span>
        <span className="diff-stat-del">−{stats.removed}자</span>
      </div>
      <p className="diff-body">
        {diffs.map(([op, text], i) =>
          op === DiffMatchPatch.DIFF_INSERT ? (
            <ins key={i}>{text}</ins>
          ) : op === DiffMatchPatch.DIFF_DELETE ? (
            <del key={i}>{text}</del>
          ) : (
            <span key={i}>{text}</span>
          ),
        )}
      </p>
    </div>
  );
}
