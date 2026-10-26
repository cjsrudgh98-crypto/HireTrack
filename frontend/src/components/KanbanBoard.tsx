import { useState } from 'react';
import {
  DndContext,
  DragOverlay,
  KeyboardSensor,
  PointerSensor,
  useDroppable,
  useSensor,
  useSensors,
  type DragEndEvent,
  type DragStartEvent,
} from '@dnd-kit/core';
import { KANBAN_COLUMNS, STATUS_LABEL } from '../constants';
import { useChangeStatus } from '../hooks/usePostings';
import type { ApplicationStatus, PostingSummary } from '../types';
import { PostingCard, PostingCardBody } from './PostingCard';
import { useToast } from './Toast';

function DropZone({
  status,
  postings,
  label,
}: {
  status: ApplicationStatus;
  postings: PostingSummary[];
  label?: string;
}) {
  const { setNodeRef, isOver } = useDroppable({ id: status });
  return (
    <div ref={setNodeRef} className={`dropzone status-${status} ${isOver ? 'dropzone-over' : ''}`}>
      {label && (
        <div className="dropzone-label">
          {label} <span className="count">{postings.length}</span>
        </div>
      )}
      {postings.map((p) => (
        <PostingCard key={p.id} posting={p} />
      ))}
      {postings.length === 0 && <div className="dropzone-empty">여기로 끌어다 놓기</div>}
    </div>
  );
}

export function KanbanBoard({ postings }: { postings: PostingSummary[] }) {
  const toast = useToast();
  const changeStatus = useChangeStatus((error) => toast(`상태 변경 실패: ${error.message}`));
  const [active, setActive] = useState<PostingSummary | null>(null);

  // 클릭(상세 이동)과 드래그를 구분하기 위해 5px 이상 움직여야 드래그로 본다.
  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 5 } }),
    useSensor(KeyboardSensor),
  );

  const byStatus = (status: ApplicationStatus) => postings.filter((p) => p.status === status);

  const handleDragStart = (event: DragStartEvent) => {
    setActive(event.active.data.current as PostingSummary);
  };

  const handleDragEnd = (event: DragEndEvent) => {
    setActive(null);
    const posting = event.active.data.current as PostingSummary | undefined;
    const target = event.over?.id as ApplicationStatus | undefined;
    if (!posting || !target || posting.status === target) return;
    changeStatus.mutate({ id: posting.id, status: target });
  };

  return (
    <DndContext
      sensors={sensors}
      onDragStart={handleDragStart}
      onDragEnd={handleDragEnd}
      onDragCancel={() => setActive(null)}
    >
      <div className="board">
        {KANBAN_COLUMNS.map((column) => {
          const total = column.statuses.reduce((sum, s) => sum + byStatus(s).length, 0);
          const split = column.statuses.length > 1;
          return (
            <section key={column.title} className="column">
              <h2 className="column-title">
                {column.title} <span className="count">{total}</span>
              </h2>
              <div className="column-body">
                {column.statuses.map((status) => (
                  <DropZone
                    key={status}
                    status={status}
                    postings={byStatus(status)}
                    label={split ? STATUS_LABEL[status] : undefined}
                  />
                ))}
              </div>
            </section>
          );
        })}
      </div>
      <DragOverlay>
        {active && (
          <div className="card card-overlay">
            <PostingCardBody posting={active} />
          </div>
        )}
      </DragOverlay>
    </DndContext>
  );
}
