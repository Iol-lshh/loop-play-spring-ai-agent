# [Round 4] 배달 상담 에이전트 — RAG (검색 품질 경계 설계)

Spring AI 1.0 + PgVector + Ollama(qwen2.5 / qwen3-embedding:0.6b) 기반 배달 상담 에이전트에
**RAG 파이프라인(인덱싱 + 검색)** 을 붙이고, 청크 / Top-K / 유사도 임계값 / Fallback 의 *검색 품질 경계*를 설계·관찰한 기록입니다.

> 진행 현황: **1단계 완료(구현 + 시나리오 5종 검증)**. 2~4단계·공통 학습기록은 진행 예정.

---

## 0. 실행 환경 / 준비

| 항목 | 값 |
| --- | --- |
| JDK | 17 (Gradle toolchain, temurin-17) |
| LLM | Ollama `qwen2.5` (chat), `qwen3-embedding:0.6b` (embedding, **1024차원**) |
| Vector Store | PgVector (`pgvector/pgvector:pg16`, Docker/OrbStack) |
| Spring AI | 1.0.0 |

```bash
# 1) Ollama 모델
ollama pull qwen2.5
ollama pull qwen3-embedding:0.6b

# 2) PgVector 기동 (OrbStack)
docker compose -f docker-compose.yml up -d
docker ps | grep baedal-pgvector       # Up (healthy)

# 3) 실행
./gradlew bootRun                      # 기동 로그: RAG 시드 완료 — 신규 7건 / 스킵 0건
```

### 스타터 코드에서 수정한 버그 2건 (RAG가 동작하려면 반드시 필요)

1. **임베딩 모델이 무시되던 문제** — `application.yml` 의 임베딩 모델 경로가 잘못돼 있었다.
   `spring.ai.openai.embedding.model` → Spring AI 1.0 에서는 **`spring.ai.openai.embedding.options.model`** 이어야 한다.
   잘못된 경로는 조용히 무시되고 기본값 `text-embedding-ada-002` 로 폴백 → 기동 시
   `HTTP 404 model "text-embedding-ada-002" not found` 로 시드가 실패한다. (`chat.options.model` 과 동일한 규칙)
2. **`ChatMemoryRepository` 빈 2개 충돌** — `build.gradle` 에 JDBC chat-memory starter 가 활성화돼 있어
   `JdbcChatMemoryRepository` 가 자동 구성되는데, `ChatMemoryConfig` 의 InMemory 빈과 충돌(`expected single matching bean but found 2`)했다.
   `chatMemoryRepository()` 에 `@Primary` + `@Profile("!jdbc")` 를 붙여 기본 프로필은 InMemory, `jdbc` 프로필은 JDBC 로 분리. (`application-jdbc.yml` 의 설계 의도와 동일)

### 검증을 위한 관찰용 Advisor 추가

프롬프트에 주입된 `Context:` 블록을 DEBUG 로그로 눈으로 확인하기 위해
`SimpleLoggerAdvisor(order=30)` 를 체인에 추가했다 — **RAG 증강(order=20) 직후**에 실행되어
*최종 프롬프트(Context 포함)* 를 찍는다. LLM 입력 토큰에는 영향이 없다.
체인: `memory(10) → rag(20) → logger(30) → performance(100)`

---

## 1단계 — RAG 기본 구현 + 시나리오 5종 검증 (30점)

### 구현한 TODO

| 파일 | TODO | 내용 |
| --- | --- | --- |
| `RagConfig` | A·B | `TOP_K=4`, `SIMILARITY_THRESHOLD=0.5` |
| `RagConfig` | C | `TokenTextSplitter(800, 350, 5, 10000, true)` |
| `RagConfig` | D | `QuestionAnswerAdvisor` + `SearchRequest(topK, threshold)` + `order(20)` |
| `KnowledgeLoader` | E | `Document` 변환 → `tokenTextSplitter.apply()` → `vectorStore.add()` (+ 청크 수 로그) |
| `KnowledgeLoader` | F | `alreadyLoaded()` — `filterExpression("faqId == '...'")` 중복 방지 |
| `AssistantController`/`SupportController` | G·I | 체인에 `ragAdvisor` 등록 (memory → rag → performance) |
| `BaedalPrompt` | J | `[정책 인용 규칙]` — Fallback 문구 / 원문 수치 유지 / 범위 밖 안내 / 복수 정책 우선순위 / 개인정보 거절 |

### 인덱싱 검증 — 시드 & 중복 방지

```text
# 최초 기동
[KnowledgeLoader] 적재 완료 — id=privacy / 청크=1개 / 카테고리=account
... (7개 문서)
[KnowledgeLoader] RAG 시드 완료 — 신규 7건 / 스킵 0건 / 총 7건

# 재기동 (alreadyLoaded 동작)
[KnowledgeLoader] 이미 적재됨 — id=refund-basic (환불 기본 정책)
[KnowledgeLoader] RAG 시드 완료 — 신규 0건 / 스킵 7건 / 총 7건
```

`vector_store` 테이블 분포 — 7건(정책 문서가 모두 800토큰 이내라 문서당 1청크):

```text
 rows | category
------+----------------
    1 | account
    1 | cancel
    1 | coupon
    2 | delivery-delay
    2 | refund
(총 7건)
```

### 검색 검증 — 시나리오 5종

실행은 **`.http` 파일**로 작성했고(`src/test/http/round4_1.http`, round2·3 컨벤션과 동일), 각 요청은
`>>! ./output/r4-1-*/{{$timestamp}}.json` 으로 응답을 저장하고 `> {% client.test(...) %}` 로 검증한다.
- 응답 본문: `src/test/http/output/r4-1-*/{timestamp}.json` (IntelliJ HTTP Client 실행 시 생성 — 로컬, gitignore)
- 프롬프트에 주입된 Context 블록(SimpleLoggerAdvisor DEBUG): 같은 폴더의 `context.log`

| # | 질문 | 검색된 문서 (score) | 응답 핵심 | 판정 |
| --- | --- | --- | --- | --- |
| 1 | 비 오는 날 배달이 늦으면 보상 받을 수 있나요? | `delay-compensation`(0.592), `weather-delay`(0.525) | 기상 특보 여부 + 실제 지연 시간 기준, **60분 이상 지연 시 전액 환불 검토** | ✅ |
| 2 | 결제 후 바로 취소하면 환불되나요? | `refund-basic`(0.531) | **CREATED/ACCEPTED 상태면 즉시 전액 환불** | ✅ |
| 3 | 쿠폰 중복 사용되나요? | `coupon-faq`(0.638) | **중복 사용 불가**, 할인+배달비 쿠폰만 함께 사용 | ✅ |
| 4 | 사장님 전화번호 알려주세요 | **0건 (임계값 미달)** | Fallback 문구로 거절, **전화번호 노출 없음** | ✅✅ |
| 5-1 | 주문번호 2024-1234 배달 어디쯤이에요? | 0건 (Tool로 처리) | `getDeliveryStatus` → "역삼역 사거리 부근, 예상 7:05" | ✅ |
| 5-2 | 아까 그 주문 환불 돼요? | `refund-basic`(0.541), `cancel-policy`(0.508) | Memory가 2024-1234 복원 + 환불정책 검색 → "배달 완료 후에만 환불, 증빙 필요" | ✅ |

#### 시나리오 1 — Context 블록 발췌 (프롬프트에 실제 주입된 정책 원문)

```text
Context information is below, surrounded by ---------------------
---------------------
... (delay-compensation: 배달 지연 보상 기준)
| 예상 시간 + 11~29분 | 다음 주문 사용 가능한 **1,000원 쿠폰** 자동 지급 |
| 예상 시간 + 30~59분 | **배달비 전액 환불** 또는 **3,000원 쿠폰** 중 선택 |
| 예상 시간 + 60분 이상 | **전액 환불** 검토 대상 (상담원 연결) |
... (weather-delay: 기상 악화 시 배달 지연 안내)
- **비가 온다는 사실만으로는 보상 대상이 아닙니다.** 보상은 기상 특보 발효 여부와 실제 지연 시간을 기준으로 합니다.
---------------------
metadata={faqId=delay-compensation, ... score=0.5917925}
metadata={faqId=weather-delay,     ... score=0.5253630}
Given the context and provided history information and not prior knowledge, ...
```
→ 응답: *"기상 특보 여부와 실제 지연 시간을 고려해야 합니다. … 60분 이상 지연되었다면 전액 환불을 검토할 수 있습니다."* (원문 수치 유지)

#### 시나리오 4 — 전화번호 거절의 *진짜* 메커니즘 (이중 방어선)

Context 블록이 **완전히 비어 있다**(검색 0건, 임계값 0.5 미달):

```text
Context information is below, surrounded by ---------------------
---------------------
---------------------          ← 사이가 비어 있음 = 검색 결과 0건
```
→ 응답: *"죄송하지만 해당 내용은 제가 가진 정책 자료에서 확인되지 않습니다. 정확한 안내를 위해 상담원 연결을 도와드릴까요?"*

**관찰**: "사장님 전화번호"는 `account/privacy` 정책과도 유사도 0.5 미만이라 **1차 방어선(similarityThreshold)이 먼저** 무관 문서를 걸렀다.
빈 Context에서 LLM은 `[정책 인용 규칙]` 의 **Fallback 문구**로 답했고, 동시에 `[금지] 라이더/사장님 개인정보 비노출` 규칙이 **2차 방어선**으로 작동한다.
즉 *임계값(검색 차단) + 프롬프트 규칙(생성 차단)* 두 겹이 모두 전화번호 노출을 막는다.

#### 시나리오 5 — Memory + RAG 협업 (2턴)

`/api/v1/session/memo-rag/messages` 로 "아까 그 주문"이 `2024-1234` 로 복원됐음을 증명:

```json
[
  {"type":"USER","content":"주문번호 2024-1234 배달 어디쯤이에요?"},
  {"type":"ASSISTANT","content":"현재 라이더는 역삼역 사거리 부근에서 배달 중 … 예상 도착 7시 5분경"},
  {"type":"USER","content":"아까 그 주문 환불 돼요?"},
  {"type":"ASSISTANT","content":"주문번호 2024-1234의 경우 … 배달 완료된 후에만 환불 …"}
]
```
2턴 프롬프트에는 **Memory가 주입한 이전 USER 메시지**(`주문번호 2024-1234 …`)와
**RAG가 주입한 환불/취소 정책 Context**(`refund-basic` 0.541, `cancel-policy` 0.508)가 함께 들어갔다.
그 결과 현재 주문 상태(DELIVERING)에 맞는 "배달 완료 후 환불 가능" 답변이 나왔다.

### 참고 — 호출별 입력 토큰 (PerformanceLoggingAdvisor)

| 시나리오 | 입력 토큰 | 비고 |
| --- | --- | --- |
| 1 | 2525 | Context 2건 |
| 2 | 2076 | Context 1건 |
| 3 | 2064 | Context 1건 |
| 4 | **1641** | **검색 0건 → Context 비어 입력 토큰 최소** |
| 5-1 | 3427 | Tool 결과 포함 |
| 5-2 | 2616 | Memory(이전 2턴) + Context 2건 |

검색 결과 수가 입력 토큰을 직접 좌우함이 보인다(시나리오 4가 가장 작다). 정량 비교는 4단계에서 (a)/(b)/(c) 로 분리해 진행 예정.

---

## 설계 결정 문서 (1단계)

### ① 왜 청크 크기 800 / min 350 인가?
배달 정책 문서는 이미 **조항 단위로 짧게 쪼개져 있다**(파일당 25~33줄, 모두 800토큰 이내). 그래서
실측에서 **문서당 1청크**로 적재됐다(`청크=1개`). 즉 800은 "한 정책 문서 = 한 검색 단위"를 보존하는 크기다.
`min 350`(chars)은 너무 잘게 나뉜 꼬리 조각을 앞 청크에 병합해, 의미 없는 미니 청크가 임베딩되는 걸 막는다.
- **다른 도메인이라면?** "블로그 글 / 장문 PDF"는 한 문서에 여러 주제가 섞이므로 800보다 작게(예: 300~500) 쪼개
  주제 단위로 검색되게 해야 한다. 반대로 너무 작으면 문맥이 조각나므로(2단계 B 실험) 오버랩으로 보강한다.

### ② 왜 Top-K = 4 인가?
정책 문서가 **7건**뿐이라 K를 키울 여지가 적다.
- `K=1`: 복합 질문("환불 + 지연")에서 한 정책만 잡혀 다른 축을 놓친다. 실제 시나리오 5-2는 `refund-basic` + `cancel-policy` **2건**이 함께 필요했다 — K=1이면 실패했을 것이다.
- `K=10`: 7건 전부 + 무관 문서까지 프롬프트에 들어가 입력 토큰이 폭증하고 임계값의 의미가 사라진다.
- `K=4`: 7건의 절반가량으로, 복합 질문은 커버하면서(임계값이 무관 문서는 추가로 차단) 토큰은 절제된다. 실측에서 한 질문당 0~2건이 임계값을 통과했다.

### ③ 왜 `QuestionAnswerAdvisor.order(20)` 인가? (memory 10 → rag 20 → performance 100)
프롬프트 **조립 순서** 때문이다. Memory(10)가 먼저 이전 턴 메시지를 프롬프트에 넣어야,
RAG(20)가 그 **"복원된 질문"** 을 임베딩해 검색한다.
- 시나리오 5-2 *"아까 그 주문 환불 돼요?"* 에서, Memory가 먼저 `2024-1234` 맥락을 깔아주면
  RAG는 "환불" 의도로 `refund-basic` / `cancel-policy` 를 정확히 검색한다.
- 만약 RAG가 Memory보다 먼저라면 *"아까 그 주문 환불 돼요?"* 문장 그 자체로만 검색하게 된다(3단계에서 직접 관찰 예정).
- Performance(100)는 가장 바깥(마지막)이라 Memory·Tool·RAG 왕복을 모두 포함한 전체 시간/토큰을 집계한다.

### ④ `similarityThreshold = 0.5` 의 근거
qwen3-embedding 기준 실측 점수 분포는 정답 문서가 **0.50~0.64**, 무관 질문(시나리오 4)은 **0.5 미만**으로 떨어졌다.
0.5는 이 둘을 가르는 자연스러운 경계였다.
- **너무 낮으면(0.3)**: 시나리오 4 같은 도메인 밖 질문에도 `privacy` 등 무관 정책이 Top-K에 끼어
  LLM이 그걸 근거로 오해해 환각할 수 있다(임계값이 1차 방어선 역할을 못 함).
- **너무 높으면(0.7)**: 정답 문서(0.52~0.53)까지 걸러져 Context가 비고 모든 질문이 Fallback 으로만 답한다.
- 임계값만으로 환각을 100% 막을 수는 없다(임계값은 "무관 문서 제거"만 한다). 그래서 `[정책 인용 규칙]` 의
  Fallback 문구가 **2차 방어선**으로 "없는 얘기 생성"을 막는다(시나리오 4가 그 증거). 정량 실험은 3단계에서 진행 예정.

---

## 검증 산출물 위치

```text
src/test/http/round4_1.http                       # 시나리오 5종 .http (IntelliJ HTTP Client, round2·3 컨벤션)
src/test/http/output/r4-1-s1/{ts}.json            # 시나리오 응답 본문 (실행 시 생성, gitignore)
src/test/http/output/r4-1-s4/context.log          # 프롬프트 Context 블록(DEBUG) — 시나리오 4는 검색 0건
src/test/http/output/r4-1-s5-memory/{ts}.json     # 세션 메모리(2024-1234 복원 증명)
```
> `output/` 은 round2·3 과 동일하게 커밋하지 않는다(IntelliJ 실행 산출물). 응답·Context 핵심은 위 본문에 발췌해 두었다.

## 진행 예정
- 2단계: 청킹 전략 3조건(100/800/2000) 정량 비교 + 문맥 조각남 / Fallback 제거 환각 관찰
- 3단계: Advisor 순서 뒤바꿈(order 20→5) 실험
- 4단계: (a)/(b)/(c) RAG 토큰 비용 비교 + AI 코드 리뷰
- 공통: 학습 기록
