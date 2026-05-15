package com.baedal.support.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

public class BaedalManual {
    /**
     * cs 유형
     */
    @Getter
    @AllArgsConstructor
    public enum Category implements PromptEnum {
        ORDER(
                "주문/변경",
                "주문 접수, 메뉴 변경, 옵션 추가 관련",
                List.of("아직 출발 안 했으면 메뉴 추가할 수 있나요?",
                        "주문한 거 매운맛으로 바꿔주세요",
                        "방금 주문했는데 옵션 잘못 골랐어요"),
                "변경 가능 여부는 가게 접수 상태에 달림. 직접 변경 약속 금지. 가게 확인이 필요한 사안임을 안내."
        ),
        DELIVERY(
                "배달 관제",
                "배달 지연, 위치 확인, 라이더 연락 관련",
                List.of("음식이 1시간째 안 와요",
                        "라이더가 어디쯤 오고 있나요?",
                        "배달원이 전화를 안 받아요"),
                "실시간 위치·도착 시각 단정 금지. 라이더 개인정보(연락처·이름) 노출 금지. 지연이 길면 ESCALATE 권장."
        ),
        REFUND(
                "취소/환불",
                "결제 취소, 음식 문제로 인한 환불 요청 관련",
                List.of("음식이 상해서 왔어요",
                        "주문한 거랑 다른 게 왔는데 환불해주세요",
                        "결제 취소하고 싶어요"),
                "환불 가부·금액은 매뉴얼 검증 필요. 직접 환불 약속 금지. CHECK_MANUAL 또는 ESCALATE로 분기."
        ),
        PAYMENT(
                "결제/영수증",
                "결제 실패, 중복 결제, 증빙 서류 발급 관련",
                List.of("결제가 두 번 됐어요",
                        "현금영수증 발급해주세요",
                        "카드 결제가 자꾸 실패해요"),
                "결제 수단 정보(카드번호·CVC 등) 입력 요청 절대 금지. 중복 결제는 매뉴얼 확인 후 처리. 증빙은 정상 발급 절차 안내."
        ),
        ACCOUNT_COUPON(
                "계정/혜택",
                "로그인 문제, 쿠폰 적용, 멤버십 혜택 관련",
                List.of("쿠폰이 적용이 안 돼요",
                        "비밀번호 찾기가 안 돼요",
                        "멤버십 등급은 어떻게 올라가나요?"),
                "쿠폰·할인·혜택 임의 지급·약속 금지. 절차 안내까지만. 로그인은 표준 복구 플로우 안내."
        ),
        ETC(
                "기타",
                "위 분류에 해당하지 않는 일반 상담 및 앱 건의사항",
                List.of("앱이 자꾸 꺼져요",
                        "이런 기능 추가됐으면 좋겠어요",
                        "그냥 궁금해서 물어봐요"),
                "위 5개 분류에 모두 해당 없을 때만 선택. 모호하면 CONFIRM_INTENT로 의도 확인 우선."
        );

        private final String label;
        private final String criteria;
        private final List<String> examples;
        private final String manual;
    }

    /**
     * 긴급도
     */
    @Getter
    @AllArgsConstructor
    public enum Urgency implements PromptEnum {
        LOW(
                "단순 문의",
                "단순 정보 확인(영업 시간, 메뉴 구성 등). 즉각적인 조치 불필요.",
                List.of("이 가게 몇 시까지 영업해요?",
                        "메뉴에 알레르기 정보 있나요?",
                        "배달 가능 지역인지 확인해주세요"),
                "정보 전달 중심의 간결한 응대. 과도한 사과·공감 표현 자제."
        ),
        NORMAL(
                "일반 요청",
                "배달지 변경, 메뉴 수정 등 일상적인 서비스 흐름 내의 요청.",
                List.of("배달지를 다른 동으로 바꾸고 싶어요",
                        "메뉴를 변경할 수 있을까요?",
                        "주문 시간을 좀 미룰 수 있나요?"),
                "표준 응대 톤. 가능 여부 확인 후 단계별 안내. 처리 완료 시각 단정 금지."
        ),
        HIGH(
                "우선 처리",
                "배달 지연 30분 이상, 오배송, 음식 품질 불만 등 고객 불만이 고조된 상태.",
                List.of("1시간이 지나도 안 와요. 도대체 언제 오는 거예요?",
                        "주문이랑 완전 다른 게 왔어요",
                        "음식에서 머리카락이 나왔어요"),
                "공감 표명 우선. 사실 확인은 최소 질문으로. ESCALATE 권장. 보상 약속 금지."
        ),
        CRITICAL(
                "즉시 개입",
                "라이더 사고, 고객 결제 오류로 인한 중복 결제, 상담사 폭언 등 시스템/안전상의 위기 상황.",
                List.of("라이더가 사고 났다고 연락이 왔어요",
                        "결제가 다섯 번이나 됐어요. 200만 원이 빠져나갔어요",
                        "상담사가 욕을 했어요"),
                "안전·금전 사안은 즉시 NextAction=ESCALATE. 의료·법적 조언 금지. 안전 우선 짧은 안내 후 인간 개입."
        );

        private final String label;
        private final String criteria;
        private final List<String> examples;
        private final String manual;
    }

    /**
     * 개인정보 레벨
     */
    @Getter
    @AllArgsConstructor
    public enum Privacy implements PromptEnum {
        LOW(
                "일반 정보",
                "메뉴 문의, 영업 시간 등 공개된 정보 위주의 대화",
                List.of("이 가게 영업시간이 어떻게 되나요?",
                        "메뉴 중에 매운 거 추천해주세요",
                        "배달 가능 지역인가요?"),
                "로그 저장 및 통계 분석에 자유롭게 활용 가능"
        ),
        MIDDLE(
                "개인식별 가능 정보",
                "대화 본문에 고객의 연락처·배달지 상세 주소·공동현관 비밀번호 등 식별 가능 정보가 **실제 등장**할 때.",
                List.of("'010-XXXX-XXXX로 연락주세요' 같은 연락처 공유",
                        "'강남구 역삼동 OOO빌라 101호' 수준의 상세 주소 노출",
                        "'공동현관 비밀번호는 #XXXX' 같은 출입 정보"),
                "로그 마스킹 처리 필수 및 접근 권한 제한 필요"
        ),
        HIGH(
                "극민감 정보",
                "결제 수단 정보, 계좌 번호, 혹은 주민등록번호와 같은 고유식별정보 포함",
                List.of("카드번호·CVC 같은 결제 수단 정보 노출",
                        "주민등록번호 등 고유식별정보 노출",
                        "계좌번호 등 금융계좌 정보 노출"),
                "시스템 로그 저장 금지 및 즉시 비식별화 처리 필요"
        );

        private final String label;
        private final String criteria;
        private final List<String> examples;   // PromptEnum 인터페이스용
        private final String securityPolicy;   // 시스템 후처리용(프롬프트 비노출)
    }

    /**
     * 다음 액션
     */
    @Getter
    @AllArgsConstructor
    public enum NextAction implements PromptEnum {
        ANSWER_DIRECTLY(
                "즉시 답변",
                "충분한 정보가 모였고, 답변 내용이 매뉴얼 범위 안에 있을 때.",
                List.of("영업시간 문의", "메뉴 구성 질문", "주문 가능 지역 확인"),
                "summary에 자기완결적 답변 작성. 매뉴얼 범위 밖 추측 금지. 보상·환불 약속 금지."
        ),
        ASK_FOR_INFO(
                "정보 요청",
                "분류·응대에 필요한 객관적 정보(주문번호, 주소, 시각)가 부족할 때.",
                List.of("'배달이 안 와요'에서 주문번호 미제공",
                        "'환불해주세요'에서 어떤 주문인지 불명확"),
                "neededInfo에 1~2개의 구체 질문 작성. 개인 식별 정보 요청 시 사유 함께 제시. summary는 짧은 공감·안내만."
        ),
        CONFIRM_INTENT(
                "의도 확인",
                "정보는 있지만 사용자의 의도·목표가 다의적일 때.",
                List.of("'환불해줘' — 결제 취소? 부분 환불? 음식값 보상?",
                        "'바꿔주세요' — 배달지 변경? 메뉴 변경?"),
                "neededInfo에 2~3개의 선택지 형태 질문 작성. 사용자가 고를 수 있도록 명확한 옵션 제시."
        ),
        CHECK_MANUAL(
                "매뉴얼 확인",
                "금액·보상·환불 가부 등 매뉴얼 검증이 선행돼야 할 때.",
                List.of("'1시간 늦었는데 얼마 받을 수 있나요?'",
                        "'음식이 식어서 왔는데 환불 가능?'"),
                "summary에 '확인이 필요합니다' 명시. 임의 단정·약속 금지. 후속 절차 안내만. 고객에게 추가 정보를 되묻지 말 것 — 정보가 부족하면 ASK_FOR_INFO를 선택할 것. '음식 상태 확인' 같은 표현 절대 금지 — AI는 음식을 직접 볼 수 없고 그것이 목적도 아님. 고객의 신고 내용은 이미 입력으로 접수된 것이며, 여기서 '확인'이란 내부 환불 정책/매뉴얼을 검토하는 것임."
        ),
        ESCALATE(
                "상담사 연결",
                "AI 책임 범위를 벗어나거나 매뉴얼만으로 해결 불가한 사안.",
                List.of("복잡한 환불 분쟁", "라이더 행동 컴플레인", "반복 문의에도 해결 안 됨"),
                "상담사 연결 안내. 대기 시간 단정 금지(실시간 정보 없음). 사과 톤 유지."
        ),
        REJECT(
                "응대 거절",
                "정책 금지 요청 또는 응대 범위 밖.",
                List.of("'사장님 번호 알려주세요'", "'쿠팡이츠랑 비교해줘'"),
                "정중한 거절. 사유 한 줄로 명시. 가능한 정상 채널 안내. 사과적 톤 유지."
        )
        ;

        private final String label;
        private final String criteria;
        private final List<String> examples;
        private final String manual;
    }
}
