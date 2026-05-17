# ADR 0002: SupportResponse 스키마 설계

## 맥락
LLM 응답을 구조화된 DTO로 받기 위해 `SupportResponse` record를 설계했다.
단순 텍스트 응답 대신 구조화된 출력을 선택한 이유는, CS 처리 흐름(분류 → 액션 → 후처리)을 시스템이 프로그래밍적으로 제어하기 위해서다.

## 필드 설계 결정

### 고객 응대 레이어
- `summary`: 고객에게 보여주는 응대 문구. LLM이 생성하되 3문장 이내로 제한.
- `neededInfo`: 고객에게 추가로 요청할 정보 목록. nextAction과 연동하여 구조적으로 관리.

### 분류 레이어 (BaedalManual 연동)
- `category`: CS 유형 (ORDER / DELIVERY / REFUND / PAYMENT / ACCOUNT_COUPON / ETC)
- `urgency`: 긴급도 (LOW / NORMAL / HIGH / CRITICAL)
- `privacy`: 대화 내 개인정보 레벨 (LOW / MIDDLE / HIGH) — 로그 후처리 정책 결정용
- `nextAction`: 다음 처리 흐름 (ANSWER_DIRECTLY / ASK_FOR_INFO / CONFIRM_INTENT / CHECK_MANUAL / ESCALATE / REJECT)

### 디버깅/투명성 필드
- `actionDetail`: LLM이 nextAction을 선택한 평가 과정을 응답에 포함시키는 필드. 별도 persistence 없이 API 응답으로만 전달된다.
  - 현재 한계: 호출자가 저장하지 않으면 기록이 사라진다. 운영에서 감사 로그·CS 이력으로 활용하려면 별도 저장소(DB, 로그 시스템 등) 연동이 필요하다.
  - `SupportResponse` 계약 상 `String` 필드이므로 프롬프트에 반드시 포함해야 LLM이 생성한다. 프롬프트에서 누락 시 역직렬화에서 `null`이 들어올 수 있다.

## 불변식 (compact constructor)

`nextAction`과 `neededInfo`는 반드시 정합해야 한다.

| nextAction | neededInfo |
|---|---|
| ASK_FOR_INFO, CONFIRM_INTENT | 비어있으면 안 됨 (질문/선택지 필수) |
| 그 외 | 반드시 빈 배열 |

이 불변식을 record compact constructor에서 강제하는 이유: LLM이 프롬프트 지시를 따르지 않아 `ASK_FOR_INFO`를 선택하면서 `neededInfo`를 비워두는 경우가 실제로 발생했다. 역직렬화 단계에서 즉시 오류를 내어 잘못된 응답이 그대로 서빙되는 것을 막는다.

null 선검증: `category` / `urgency` / `privacy` / `nextAction`은 switch 진입 전에 명시적으로 null 체크한다. null이면 NPE 대신 `IllegalArgumentException`으로 계약 위반을 일관되게 처리한다.