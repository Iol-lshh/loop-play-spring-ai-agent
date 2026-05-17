# 회고: Week 1 미션 이후 추가 고려사항

## CS의 목적 재정의

CS란 고객의 컴플레인을 수용하면서 충성도 이탈을 막고, 충성도를 높이기 위한 행위다.
현재 시스템 프롬프트의 ROLE은 처리 범위(주문/배달/취소/환불)만 정의하고, 왜 이 AI가 존재하는지 목적이 없다.
AI가 목적을 모르면 규칙을 따르는 것과 고객을 대하는 것 사이에서 균형을 잡지 못한다.

→ ROLE에 "고객 불만을 공감하며 수용하고, 충성도 이탈을 방지하는 것이 최우선 목적이다"를 명시해야 한다.

---

## LLM 지시 준수의 한계

이번 작업에서 반복적으로 겪은 문제:

- `ASK_FOR_INFO` / `CONFIRM_INTENT` 선택 시 `neededInfo`를 빈 배열로 반환
- `CHECK_MANUAL` 선택 시에도 summary에서 고객에게 질문을 던지는 행동
- "음식 상태 확인" 같이 AI가 물리적으로 할 수 없는 행위를 summary에 포함

지시를 여러 곳(OUTPUT FORMAT, GUIDELINE, manual 필드)에 반복해서 넣어도 완전히 통제되지 않았다.

→ 프롬프트 텍스트만으로는 한계가 있다. 장기적으로 아래를 고려해야 한다.
  - 응답 검증 후 재시도하는 retry 로직
  - `neededInfo` 불변식 위반 시 fallback 응답 처리
  - 출력 스키마를 더 단순하게 만들어 LLM 오작동 가능성 자체를 줄이기

---

## 프롬프트 실험(PromptLab) 설계의 한계

단순 프롬프트 vs 구조화된 프롬프트를 비교했으나 두 결과 모두 `categoryConsistency: 1.0`이 나왔다.
원인은 Spring AI `BeanOutputConverter`가 `.entity(SupportResponse.class)` 호출 시 Java enum 스키마를 자동으로 프롬프트에 추가하기 때문이다.
즉, 어떤 system prompt를 넣어도 schema 힌트가 공통으로 주입되어 category 분류는 항상 안정적으로 나온다.

**실측 결과** (`temperature=0.3`, `message="음식이 1시간째 안 와요."`, `repeat=5`):

| 실험 | categoryConsistency | urgency 분포 |
|---|---|---|
| 단순 프롬프트 | 1.0 (DELIVERY 5/5) | HIGH 4, NORMAL 1 |
| 구조화 프롬프트 | 1.0 (DELIVERY 5/5) | NORMAL 5 |

→ category는 두 방식 모두 안정. urgency에서 차이 발생 — 구조화 프롬프트가 urgency 판단을 보수적으로 유도.
→ 공정한 비교를 위해서는 BeanOutputConverter 없이 raw 텍스트 응답을 받아 직접 파싱하거나,
   `BaedalPrompt.SYSTEM_PROMPT` 자체를 비교 대상으로 넣는 실험 설계가 필요하다.

원시 데이터: `.doc/experiments/promptlab-temp0.3.md`

---

## Temperature 실험 결과

동일 메시지(`"음식이 1시간째 안 와요."`)를 temperature 0.0 / 0.3 / 0.7에서 각 5회 호출.

| temperature | categoryConsistency | nextActionConsistency | 언어 혼용 | 특이사항 |
|---|---|---|---|---|
| 0.0 | 1.0 | 1.0 (CHECK_MANUAL 5/5) | 있음(고착) | 5회 완전 동일 응답. 잘못된 summary가 반복됨 |
| 0.3 | 1.0 | 0.6 (CHECK_MANUAL 3, ANSWER_DIRECTLY 2) | 1회 | nextAction 분산 시작. summary 품질 양호 |
| 0.7 | 1.0 | 0.4 (3종 혼재) | 없음 | ASK_FOR_INFO 등장. 자연스러운 한국어 유지 |

**발견**:
- category는 모든 temperature에서 100% DELIVERY — BeanOutputConverter schema 주입 효과 재확인
- temperature 0.0: 결정론적이지만, 초기 생성 품질 결함(언어 혼용)이 고착되는 리스크
- temperature 0.7: nextAction 다양성 최대(ASK_FOR_INFO까지 등장), invariant 검증 필요성 확인
- **운영 권장**: 0.3이 품질·일관성 균형점. 단 프롬프트에 '반드시 한국어로만 응답' 명시 보강 필요

원시 데이터: `.doc/experiments/exp-temp-comparison.md`

---

## confidenceLevel의 활용 가능성

추가한 `confidenceLevel` 필드는 단순 로깅 이상으로 확장할 수 있다.

- `LOW`인 경우 자동으로 ESCALATE로 overriding하는 후처리 규칙 적용
- `confidenceLevel`과 `nextAction` 조합으로 상담사 개입 우선순위 큐 구성
- 시간이 지나며 `LOW`가 자주 나오는 케이스를 수집해 프롬프트 개선 포인트로 활용

---

## nextAction 흐름의 단방향성 문제

현재 구조는 한 번의 요청에 하나의 nextAction을 반환한다.
실제 CS 흐름은 `ASK_FOR_INFO → 고객 응답 → CHECK_MANUAL → ESCALATE`처럼 다단계다.
대화 히스토리 없이 매 요청을 독립적으로 처리하면 앞 턴의 맥락이 사라진다.

→ 대화 히스토리를 유지하는 멀티턴 구조, 또는 세션 기반 상태 관리가 필요하다.
