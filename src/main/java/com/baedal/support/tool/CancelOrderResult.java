package com.baedal.support.tool;

import java.util.EnumMap;
import java.util.Map;

/**
 * 주문 취소 Tool의 결과.
 * <p>
 * 성공/실패를 boolean으로만 돌리면 LLM이 상황을 설명하지 못한다.
 * {@code outcome}을 문자열 enum 값으로 두어 LLM이 그대로 고객에게 설명할 수 있도록 한다.
 */
public record CancelOrderResult(
        String orderId,
        Outcome outcome,
        String message
) {
    public enum Outcome {
        CANCELED,            // 이번 호출에서 취소됨
        ALREADY_CANCELED,    // 이미 취소되어 있었음 (멱등 — 에러 아님)
        NOT_CANCELABLE,      // 조리 시작 이후 등 취소 불가
        NOT_FOUND            // 주문번호 없음
    }

    private static final Map<Outcome, String> MESSAGES = new EnumMap<>(Map.of(
            Outcome.NOT_FOUND,        "존재하지 않는 주문번호입니다.",
            Outcome.ALREADY_CANCELED, "이미 취소된 주문입니다.",
            Outcome.NOT_CANCELABLE,   "조리가 시작된 이후에는 취소할 수 없습니다.",
            Outcome.CANCELED,         "주문이 취소되었습니다."
    ));

    public static CancelOrderResult of(String orderId, Outcome outcome) {
        return new CancelOrderResult(orderId, outcome, MESSAGES.get(outcome));
    }
}
