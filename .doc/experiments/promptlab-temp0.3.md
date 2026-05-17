# PromptLab 실험: 단순 vs 구조화 프롬프트

- endpoint: `POST /api/v1/prompt-lab`
- model: qwen2.5
- temperature: 0.3
- message: `"음식이 1시간째 안 와요."`
- repeat: 5

---

## 단순 프롬프트

```
너는 배달 앱 고객 지원 챗봇이야. 고객 문의에 답변해줘.
```

### 결과

```json
{
  "totalRuns": 5,
  "successCount": 5,
  "errorCount": 0,
  "errors": [],
  "categoryCounts": { "DELIVERY": 5 },
  "urgencyCounts": { "HIGH": 4, "NORMAL": 1 },
  "categoryConsistency": 1.0
}
```

---

## 구조화 프롬프트

```
너는 배달 앱 고객 지원 상담 AI야. 고객 메시지를 분류하고 반드시 유효한 JSON만 응답해.
필드: summary, actionDetail, category (ORDER/DELIVERY/REFUND/PAYMENT/ACCOUNT_COUPON/ETC),
urgency (LOW/NORMAL/HIGH/CRITICAL), privacy (LOW/MIDDLE/HIGH),
nextAction (ANSWER_DIRECTLY/CHECK_MANUAL/ESCALATE/ASK_FOR_INFO/CONFIRM_INTENT/REJECT),
neededInfo (배열, nextAction이 ASK_FOR_INFO 또는 CONFIRM_INTENT일 때만 채울 것. 그 외에는 빈 배열).
```

### 결과

```json
{
  "totalRuns": 5,
  "successCount": 5,
  "errorCount": 0,
  "errors": [],
  "categoryCounts": { "DELIVERY": 5 },
  "urgencyCounts": { "NORMAL": 5 },
  "categoryConsistency": 1.0
}
```

---

## 관찰

- 두 방식 모두 `categoryConsistency: 1.0` — BeanOutputConverter가 `.entity(SupportResponse.class)` 호출 시 Java enum 스키마를 자동 주입하므로, system prompt와 무관하게 category 분류는 항상 안정적
- urgency에서만 차이: 단순(HIGH 4/5) vs 구조화(NORMAL 5/5) — 구조화 프롬프트가 urgency 판단을 보수적으로 유도한 것으로 보임
- 공정한 비교를 하려면 BeanOutputConverter 없이 raw 텍스트로 받아 직접 파싱하는 실험 설계가 필요
