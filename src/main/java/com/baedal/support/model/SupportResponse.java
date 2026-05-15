package com.baedal.support.model;

import org.springframework.lang.NonNull;

import java.util.List;

// TODO [1단계]: 필드 1개 이상을 의미 있게 추가하라.
//
// 예시:
// - estimatedResolutionMinutes (예상 해결 시간)
// - suggestedCompensationType (보상 유형 제안)
// - confidenceLevel (응답 확신도)
//
// 설계 결정 문서에 "왜 이 필드를 추가했는가?"를 기록하라.
public record SupportResponse(
        String summary,
        String actionDetail,
        BaedalManual.Category category,
        BaedalManual.Urgency urgency,
        @NonNull BaedalManual.Privacy privacy,
        BaedalManual.NextAction nextAction,
        List<String> neededInfo
) {
    public SupportResponse {
        if (category == null) throw new IllegalArgumentException("category는 null일 수 없습니다");
        if (urgency == null) throw new IllegalArgumentException("urgency는 null일 수 없습니다");
        if (privacy == null) throw new IllegalArgumentException("privacy는 null일 수 없습니다");
        if (nextAction == null) throw new IllegalArgumentException("nextAction은 null일 수 없습니다");

        // invariant: NextAction과 neededInfo 정합성
        // - 이후 검증 예외를 retry 걸어 처리할 수 있겠다.
        // - 이런 일련의 정책 로직은 specification으로 빼는게 좋을 것 같으며,
        // - 제대로된 layered 아키텍처로 구성하고 service로 분리하는게 좋겠다.
        switch (nextAction) {
            case ASK_FOR_INFO, CONFIRM_INTENT -> {
                if (neededInfo == null || neededInfo.isEmpty())
                    throw new IllegalArgumentException(
                            nextAction + "는 neededInfo가 필요합니다");
            }
            case ANSWER_DIRECTLY, CHECK_MANUAL, ESCALATE, REJECT -> {
                if (neededInfo != null && !neededInfo.isEmpty())
                    throw new IllegalArgumentException(
                            nextAction + "는 neededInfo가 비어야 합니다");
            }
        }
        neededInfo = neededInfo == null ? List.of() : List.copyOf(neededInfo);
    }
}
