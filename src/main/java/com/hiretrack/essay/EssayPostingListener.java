package com.hiretrack.essay;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.hiretrack.essay.domain.EssayQuestion;
import com.hiretrack.essay.domain.Submission;
import com.hiretrack.posting.PostingEvents;

/**
 * 공고 이벤트를 받아 자소서를 처리한다. 동기 리스너라 상태 변경과 같은 트랜잭션에서 실행되고,
 * 여기서 실패하면 상태 변경도 함께 롤백된다(상태는 APPLIED 인데 제출본이 없는 상태가 생기지 않음).
 */
@Component
public class EssayPostingListener {

    private static final Logger log = LoggerFactory.getLogger(EssayPostingListener.class);

    private final EssayQuestionRepository questionRepository;
    private final EssayAnswerRepository answerRepository;
    private final EssayDraftRepository draftRepository;

    public EssayPostingListener(EssayQuestionRepository questionRepository, EssayAnswerRepository answerRepository,
                                EssayDraftRepository draftRepository) {
        this.questionRepository = questionRepository;
        this.answerRepository = answerRepository;
        this.draftRepository = draftRepository;
    }

    /** 처음 지원한 시점의 최신 버전을 문항마다 제출본으로 고정 */
    @EventListener
    public void onFirstApplied(PostingEvents.FirstApplied event) {
        int frozen = 0;
        for (EssayQuestion question : questionRepository.findByPostingIdOrderByDisplayOrderAscIdAsc(event.postingId())) {
            if (Submission.freezeLatest(answerRepository.findByQuestionIdOrderByVersionDesc(question.getId())).isPresent()) {
                frozen++;
            }
        }
        log.info("essay-submission-frozen postingId={} questions={}", event.postingId(), frozen);
    }

    /** 실수로 지원 처리했다가 바로 되돌린 경우 고정 해제 */
    @EventListener
    public void onApplyReverted(PostingEvents.ApplyReverted event) {
        answerRepository.findSubmittedByPostingId(event.postingId()).forEach(Submission::unfreeze);
    }

    /** 공고 삭제 전에 딸린 자소서를 지운다(자식 → 부모 순서) */
    @EventListener
    public void onDeleting(PostingEvents.Deleting event) {
        answerRepository.deleteByPostingId(event.postingId());
        draftRepository.deleteByPostingId(event.postingId());
        questionRepository.deleteByPostingId(event.postingId());
    }
}
