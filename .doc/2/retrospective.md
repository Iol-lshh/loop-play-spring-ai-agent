# Round 2 — 공통 학습 기록

## 내가 배운 것

**Tool Calling은 LLM이 "판단"하고 코드가 "실행"한다는 구조가 생각보다 명확하게 분리된다.**
처음에는 `@Tool` 어노테이션만 달면 자동으로 다 되는 줄 알았는데, 실제로는 LLM이 description을 읽고 "이 Tool을 쓸지 말지" 결정하고, Spring AI가 그 결정을 가로채서 Java 메서드를 실행하고, 결과를 다시 LLM에게 넘기는 2-round trip 구조라는 걸 로그로 직접 확인했다. 입력 토큰이 Tool 호출 시 2.14배로 뛰는 것도 이 구조 때문이다.

**description이 LLM에게는 유일한 API 문서다.**
버전 C 실험에서 "메뉴와 결제 금액만 반환한다"고 썼더니 LLM이 `getDeliveryStatus` 대신 `getOrderDetail`을 호출했다. 코드가 뭘 하든 description이 틀리면 LLM은 틀린 Tool을 고른다. description은 주석이 아니라 프로덕션 코드다.

**멱등성 분기를 제거하면 오류 메시지 자체가 잘못된 방향을 가리킨다.**
ALREADY_CANCELED 체크를 제거했을 때, 이미 취소된 주문을 다시 취소 요청하면 `isCancelable()` false → `NOT_CANCELABLE`이 반환되어 LLM이 "조리가 시작되어 취소 불가"라고 안내한다. 코드가 죽거나 예외가 발생하는 게 아니라, **그럴싸하게 틀린 안내를 한다**는 점이 더 위험하다.

**Ollama `/api/chat`과 `/v1/chat/completions`는 다르게 동작한다.**
`/api/chat`은 Tool 3개 조합 시 `tool_calls` 대신 `<tool_call>` XML을 텍스트로 출력하는 문제가 있었다. `/v1/chat/completions`(OpenAI 호환)는 동일 모델에서 안정적으로 `tool_calls`를 반환했다. Spring AI Ollama 어댑터가 어떤 엔드포인트를 쓰는지 확인해야 한다.

---

## 의문점

- **Tool이 여러 개 동시에 호출될 때 순서와 트랜잭션은 어떻게 되는가?**
  qwen2.5가 `getOrderDetail`과 `getDeliveryStatus`를 한 번의 응답에서 동시에 요청한 적이 있었다. Spring AI가 이를 병렬로 실행하는지, 직렬로 실행하는지 확인하지 못했다. 하나가 실패하면 나머지도 롤백되는가?

- **description의 언어(한국어/영어)가 실제로 Tool 선택 확률에 영향을 주는가?**
  버전 B에서 "배달 정보 조회" 한 줄(한국어)이 버전 A의 긴 한국어 description보다 호출 성공률이 높았다(5/5 vs 2/5). 짧아서인지, 언어 일치 때문인지, 아니면 단순 무작위 변동인지 구분하지 못했다.

- **PerformanceLoggingAdvisor가 집계하는 토큰은 1차 + 2차 합산인가, 마지막 호출 기준인가?**
  로그상 "입력 토큰: 2215"가 2차 요청 기준으로 보이는데, Spring AI가 내부적으로 어떻게 usage를 합산하는지 소스를 확인하지 못했다.

---

## Round 3에 시도하고 싶은 것

**"그거 취소해주세요" 같은 지시 대명사 처리.**
현재는 매번 "주문번호 2024-1234"를 명시해야 Tool이 올바르게 호출된다. Chat Memory에 직전 조회한 `orderId`를 저장해두면, "방금 배달 조회한 주문 취소해줘"처럼 대화 맥락을 유지할 수 있을 것 같다.

**Tool 호출 실패를 Memory에 기록해 재시도 전략 세우기.**
qwen2.5가 같은 질문에도 Tool을 호출하지 않을 때가 있었다. Memory에 "이 사용자는 배달 위치를 물어봤고 Tool 호출이 필요하다"는 컨텍스트를 유지하면, LLM이 다음 턴에 더 확실하게 Tool을 선택할 수 있을지 실험해보고 싶다.
