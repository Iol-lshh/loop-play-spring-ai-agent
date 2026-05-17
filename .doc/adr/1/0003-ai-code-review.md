# AI 코드 리뷰: Week 1 구현 검토

## 검토 대상

`round1` 브랜치 전체. model·controller·advisor 레이어 중심.

---

## SupportResponse — compact constructor

**잘된 점**: invariant(nextAction ↔ neededInfo 정합성)를 역직렬화 시점에 강제하는 구조가 정교하다. LLM이 `ASK_FOR_INFO`를 반환하면서 `neededInfo`를 비우는 실제 오작동을 잡아낸다.

**개선 포인트**:

1. `@NonNull`(line: `@NonNull BaedalManual.Privacy privacy`)은 compact constructor의 null 체크와 중복이다. 어노테이션은 컴파일 타임 경고 용도이고, 런타임 방어는 이미 아래에 있으므로 제거해도 무방.

2. `neededInfo = List.copyOf(neededInfo)` 는 인자가 null일 때 NPE를 발생시킨다. compact constructor에서 이미 `neededInfo == null` 케이스를 처리하고 있지만, null 처리(`List.of()`) → `List.copyOf()` 순서가 보장되어 있으므로 현재는 안전. 하지만 switch 분기 추가 시 순서가 뒤바뀔 위험이 있다. null 처리를 switch 앞으로 끌어올리는 것을 권장한다:
   ```java
   neededInfo = (neededInfo == null) ? List.of() : neededInfo;
   // ... switch ...
   neededInfo = List.copyOf(neededInfo);
   ```

---

## PromptLabController — 실험 격리

**잘된 점**: try-catch로 iteration별 에러를 격리하여 전체 실험이 중단되지 않도록 한 구조가 ADR 0003의 "retry/fallback" 방향과 일치한다.

**개선 포인트**:

1. `new java.util.ArrayList<>()` — FQCN을 직접 쓰고 있다. 파일 상단 import에 `java.util.ArrayList`를 추가하면 된다.

2. `PromptLabRequest`, `PromptLabResult` inner record가 컨트롤러 안에 있다. 지금은 적절하지만, 실험 필드가 늘어나면 `model` 패키지로 분리를 고려.

---

## PerformanceLoggingAdvisor — 순서 설계

**잘된 점**: `getOrder()` 반환값에 주석으로 의도("LLM 왕복 시간 전체 측정")를 명시했다. null 방어도 단계적으로 처리하고 있다.

**개선 포인트**:

1. Spring AI `CallAdvisor`의 `getOrder()`는 낮은 값이 체인 바깥쪽이다(Ordered 인터페이스 기준). 100은 체인 안쪽이므로, 여러 Advisor가 추가되면 PerformanceLogging이 일부 Advisor의 처리 시간을 놓칠 수 있다. LLM 왕복 전체를 재려면 `Ordered.LOWEST_PRECEDENCE`(Integer.MAX_VALUE) 또는 명시적으로 낮은 숫자(예: 0)를 쓰는 것이 의도에 맞다.

2. `usage.getPromptTokens()`, `usage.getCompletionTokens()`는 Ollama 로컬 모델에서 null을 반환하는 케이스가 있다. `Optional.ofNullable(usage).map(...).orElse(0L)` 수준의 방어를 추가하면 NPE 리스크를 줄인다.

---

## BaedalManual + PromptEnum — enum→프롬프트 테이블 패턴

**잘된 점**: `PromptEnum` 인터페이스로 `toPromptTable(Class<E>)`를 정적 유틸로 만든 것이 1주차에서 가장 재사용성이 높은 설계다. `BaedalPrompt.SYSTEM_PROMPT`가 클래스 로드 시점에 한 번만 `formatted()`를 호출하므로 요청마다 프롬프트를 재조립하는 오버헤드가 없다.

**개선 포인트**:

1. `Privacy` enum의 마지막 필드가 `securityPolicy`인데, `PromptEnum.manual()` 기본 구현은 빈 문자열을 반환한다. 즉 `Privacy`의 보안 정책은 프롬프트 테이블에 포함되지 않는다. 이게 의도라면(보안 정책을 LLM에 노출하지 않음) ADR에 명시할 것. 의도가 아니라면 `Privacy`도 `manual()` override가 필요하다.

2. `toPromptRow()`에서 `examples()`가 없을 때 `"-"`을 반환한다. 빈 예시를 가진 Privacy는 테이블에서 examples 컬럼이 모두 `-`로 나온다. LLM이 예시 없이 기준만 보고 판단해야 하는 상황이므로, Privacy에도 examples를 추가하는 것을 권장한다.

---

## Controller 레이어 전반

`ChatClient.Builder` → 생성자에서 한 번 `.build()` → `ChatClient` 필드 유지 패턴이 4개 컨트롤러에 일관되게 적용됐다. thread-safety 관점에서 올바른 구조다.

**잔여 개선**: `ChatController`의 생성자 접근 제어자가 `protected`인데, 다른 컨트롤러도 모두 `protected`로 통일되어 있다. Spring이 CGLIB 프록시 생성 시 `protected` 생성자도 사용할 수 있지만, 의미상 `public` 또는 package-private(`(none)`)이 더 명확하다.

---

## 종합

| 항목 | 평가 |
|---|---|
| SupportResponse 불변식 설계 | 매우 좋음 — 역직렬화 방어의 교과서적 사례 |
| PromptEnum 패턴 | 좋음 — 재사용성과 유지보수성 모두 확보 |
| PerformanceLoggingAdvisor 순서 | 주의 필요 — `getOrder()` 값 재검토 권장 |
| PromptLab 에러 격리 | 좋음 — invariant 위반을 실험 단위로 격리 |
| Controller ChatClient 위임 | 좋음 — thread-safe, stateless |
