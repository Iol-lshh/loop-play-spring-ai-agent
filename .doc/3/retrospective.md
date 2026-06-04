# Round 3 — 공통 학습 기록

## 내가 배운 것

**Memory는 "켜는 것"이 아니라 "경계를 설계하는 것"이다.**
Bean 3개 연결하고 나서 동작은 됐지만, MAX_MESSAGES를 2로 줄이니 3턴 전 언급한 주문번호를 참조 못 하고 "그거"를 엉뚱하게 해석했다. MAX_VALUE로 늘리니 토큰이 매 턴 +91씩 올라가고 T7에서 오히려 오래된 컨텍스트가 판단을 흐렸다. 값 하나가 정확도와 비용을 동시에 결정한다는 걸 수치로 직접 확인했다.

**ChatMemoryRepository 교체는 상위 레이어에 완전히 투명했다.**
`InMemoryChatMemoryRepository`를 `JdbcChatMemoryRepository`로 바꿨을 때 `ChatMemory`, `MessageChatMemoryAdvisor`, 컨트롤러 중 어느 것도 손대지 않았고 시나리오 5종 결과가 동일했다. 저장소 관심사가 Repository 레이어에 완전히 격리된다는 걸 코드가 아니라 실험으로 증명했다.

**Spring AI 1.0.0 JDBC starter에는 H2 스키마가 없다.**
PostgreSQL, MariaDB, HSQLDB 스키마만 포함되어 있고 H2용은 없었다. 자동 구성이 기대하는 classpath 위치(`org/springframework/ai/chat/memory/repository/jdbc/schema-h2.sql`)에 직접 파일을 만들어야 했다. 라이브러리가 "되어야 한다"고 문서에 써 있어도 실제 JAR 내용을 확인해야 한다는 교훈이었다.

**Memory는 비즈니스 스냅샷이자, 컨텍스트 오염 방지이자, SRP다.**
세 관점은 하나로 연결된다. 세션 격리가 있어야 스냅샷이 오염되지 않고, 스냅샷이 오염되지 않아야 각 레이어가 자기 책임만 질 수 있다. Repository가 "어디에 저장하는가"만 알고, ChatMemory가 "얼마나 유지하는가"만 알고, Advisor가 "언제 끼워 넣는가"만 아는 게 가능한 건 — 세션 ID로 경계가 명확하기 때문이다. 경계가 무너지면(`defaultValue="default"` 폴백처럼) SRP도 같이 무너진다. cust-A의 주문 컨텍스트가 cust-B 프롬프트에 섞이는 순간, 어느 레이어도 자기 책임을 온전히 수행할 수 없다.

**`initialize-schema: embedded`는 파일 DB를 임베디드로 보지 않는다.**
`jdbc:h2:mem:*`은 Spring Boot가 EmbeddedDatabaseType으로 판정해서 스키마 자동 초기화가 동작하지만, `jdbc:h2:file:*`은 판정에서 제외된다. "H2니까 임베디드겠지"라는 가정이 틀렸다. 설정 하나의 적용 범위가 예상과 다를 때 어디서 분기하는지 소스 레벨로 따라가야 한다.

---

## 의문점

- **Memory에 Tool 결과가 저장되지 않는 이유가 뭔가? → 해답은 RAG다.**
  `MessageWindowChatMemory`는 USER와 ASSISTANT 메시지만 저장하고 TOOL 메시지는 저장하지 않는다. T1의 `getDeliveryStatus` 결과가 T2 Memory에 없고, T2 입력 토큰이 T1보다 낮게 나온 이유다. Tool 결과는 "대화에서 무슨 말이 오갔는가"가 아니라 "지금 이 순간 무엇이 사실인가"의 영역이다. Memory에 쌓으면 오래된 사실이 컨텍스트를 오염시킨다. RAG는 매 질의마다 현재 사실을 가져오기 때문에, Tool 결과를 Memory 대신 RAG 파이프라인으로 처리하면 입력 → 처리 → 검증 → 개정 각 단계가 독립적으로 책임을 진다. Memory는 "무슨 말이 오갔는가(세션 맥락)"를 담고, RAG는 "무엇이 사실인가(도메인 지식)"를 담는 것이 올바른 역할 분리다.

- **요약 전략을 직접 구현한다면 언제 요약하는가?**
  슬라이딩 윈도우 대신 요약 전략을 쓰면 오래된 대화를 압축해서 토큰을 아낄 수 있다. 그런데 "몇 턴마다" 또는 "몇 토큰 이상일 때" 요약을 트리거할지 기준이 없다. LLM을 한 번 더 써서 요약하면 오히려 비용이 더 드는 구간이 생길 수 있다.

---

## Round 4에 시도하고 싶은 것

**Memory와 RAG를 같은 체인에 붙이면 어떤 질문을 커버할 수 있나.**
Memory는 "아까 그 주문"처럼 세션 안의 맥락을 유지한다. RAG는 "배달 지연 보상 정책이 뭐예요?"처럼 지식 베이스를 참조한다. 두 Advisor가 체인에 함께 있으면 "아까 그 주문, 지연됐는데 보상받을 수 있어요?"처럼 세션 맥락과 정책 지식을 동시에 써야 하는 질문을 처리할 수 있을 것 같다. 두 Advisor의 order 설정이 프롬프트 조립 순서에 어떤 영향을 주는지도 확인하고 싶다.
