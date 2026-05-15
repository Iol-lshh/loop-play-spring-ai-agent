# loop-play-spring-ai-agent

Spring AI 기반 배달 상담 에이전트 학습용 스타터 코드입니다.

## 개요

루퍼스 부트캠프 "Spring AI 배달 상담 에이전트" 6주 과정의 Week 1 미션 스타터 코드입니다.
`ChatClient`, System Prompt, Structured Output, Streaming, Observability 개념을 실습합니다.

## 빠른 시작

```bash
./gradlew bootRun
```

## 테스트

이 README는 PR 워크플로우 검증용 테스트 커밋입니다.

## 프롬프트 실험 결과

메시지: `"음식이 1시간째 안 와요."` / 각 5회 호출

| | 단순 프롬프트 | 구조화된 프롬프트 |
|---|---|---|
| categoryConsistency | **1.0** | **1.0** |
| categoryCounts | DELIVERY: 5 | DELIVERY: 5 |
| urgencyCounts | HIGH: 4 / NORMAL: 1 | NORMAL: 5 |

**해석**
- category 일관성은 두 프롬프트 모두 1.0으로 동일하다. Spring AI `BeanOutputConverter`가 `.entity(SupportResponse.class)` 호출 시 enum 스키마를 자동 추가하기 때문에, 단순 프롬프트도 schema 힌트를 받아 일관성이 유지된다.
- urgency에서 차이가 드러난다. 단순 프롬프트는 HIGH 위주(4/5), 구조화된 프롬프트는 NORMAL로 수렴(5/5). "1시간 지연"은 `BaedalManual.Urgency` 기준 HIGH(`배달 지연 30분 이상`)에 해당하나, 구조화된 프롬프트의 urgency 기준 설명이 부재하여 LLM이 더 낮게 판단하는 경향을 보인다.
- **결론**: 단순 분류 정확도는 schema 힌트로 보완되나, urgency 같은 판단 기준이 필요한 필드는 상세 기준이 있는 프롬프트(`BaedalPrompt.SYSTEM_PROMPT`)가 필요하다.
