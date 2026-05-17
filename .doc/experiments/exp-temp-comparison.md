# Temperature 실험: 0.0 / 0.3 / 0.7 비교

- endpoint: `POST /api/v1/support`
- model: qwen2.5
- message: `"음식이 1시간째 안 와요."`
- 각 temperature마다 5회 호출

---

## temperature: 0.0

```json
{"summary":"음식이 1시간째 와지 않아 걱정 yourselves. Let me check the status for you.","actionDetail":"[eval] 1:match(배달 지연) -> 2:skip(정보충분)","category":"DELIVERY","urgency":"HIGH","privacy":"LOW","nextAction":"CHECK_MANUAL","neededInfo":[]}
{"summary":"음식이 1시간째 와지 않아 걱정 yourselves. Let me check the status for you.","actionDetail":"[eval] 1:match(배달 지연) -> 2:skip(정보충분)","category":"DELIVERY","urgency":"HIGH","privacy":"LOW","nextAction":"CHECK_MANUAL","neededInfo":[]}
{"summary":"음식이 1시간째 와지 않아 걱정 yourselves. Let me check the status for you.","actionDetail":"[eval] 1:match(배달 지연) -> 2:skip(정보충분)","category":"DELIVERY","urgency":"HIGH","privacy":"LOW","nextAction":"CHECK_MANUAL","neededInfo":[]}
{"summary":"음식이 1시간째 와지 않아 걱정 yourselves. Let me check the status for you.","actionDetail":"[eval] 1:match(배달 지연) -> 2:skip(정보충분)","category":"DELIVERY","urgency":"HIGH","privacy":"LOW","nextAction":"CHECK_MANUAL","neededInfo":[]}
{"summary":"음식이 1시간째 와지 않아 걱정 yourselves. Let me check the status for you.","actionDetail":"[eval] 1:match(배달 지연) -> 2:skip(정보충분)","category":"DELIVERY","urgency":"HIGH","privacy":"LOW","nextAction":"CHECK_MANUAL","neededInfo":[]}
```

- nextAction: CHECK_MANUAL 5/5 (일관성 1.0)
- 5회 완전 동일 응답 — 결정론적
- **문제**: 언어 혼용("yourselves", 영어 혼입)이 고착됨. 초기 생성의 품질 결함이 반복 재현됨

---

## temperature: 0.3

```json
{"summary":"죄송합니다. 음식이 1시간째 와지 않으시다니 기다리신 시간이 길어 보입니다. 현재 배달 상태를 확인해보겠습니다.","actionDetail":"[eval] 1:skip(정보충분) -> 2:match(CHECK_MANUAL)","category":"DELIVERY","urgency":"HIGH","privacy":"LOW","nextAction":"CHECK_MANUAL","neededInfo":[]}
{"summary":"음식이 1시간째 안 와서 불편하시다니 죄송합니다. 배달 상태를 확인해보겠습니다.","actionDetail":"[eval] 1:match(배달 지연) -> 2:skip(정보충분)","category":"DELIVERY","urgency":"HIGH","privacy":"LOW","nextAction":"CHECK_MANUAL","neededInfo":[]}
{"summary":"음식이 1시간째 와지 않으시다니 걱정됩니다. 현재 배달 상태를 확인해보겠습니다.","actionDetail":"[eval] 1:match(배달 지연) -> 2:skip(정보충분)","category":"DELIVERY","urgency":"HIGH","privacy":"LOW","nextAction":"ANSWER_DIRECTLY","neededInfo":[]}
{"summary":"음식이 1시간째 안 와서 불편하셨나 봅니다. 배송 상태를 확인해 보겠습니다.","actionDetail":"[eval] 1:match(배달 지연) -> 2:skip(정보충분)","category":"DELIVERY","urgency":"HIGH","privacy":"LOW","nextAction":"ANSWER_DIRECTLY","neededInfo":[]}
{"summary":"음식이 1시간째 와지 않아 걱정 yourselves. Let me check the status for you.","actionDetail":"[eval] 1:match(DELIVERY) -> 2:skip(信息已足够)","category":"DELIVERY","urgency":"HIGH","privacy":"LOW","nextAction":"CHECK_MANUAL","neededInfo":[]}
```

- nextAction: CHECK_MANUAL 3/5, ANSWER_DIRECTLY 2/5 (일관성 0.6)
- summary 다양성 확보, 자연스러운 한국어 위주
- **문제**: 5번째에서 언어 혼용 재발(영어·중국어 혼입)

---

## temperature: 0.7

```json
{"summary":"음식이 1시간째 안 와서 불편하셨나 봐요. 배달이 지연된 이유를 확인하기 위해 매뉴얼을 확인해볼게요.","actionDetail":"[eval] 1:match(배달 지연) -> 2:skip(정보충분)","category":"DELIVERY","urgency":"HIGH","privacy":"LOW","nextAction":"CHECK_MANUAL","neededInfo":[]}
{"summary":"죄송합니다. 음식이 1시간째 안 오고 있으시다니 기다리실 데가 힘드시겠어요. 현재 상태를 확인하기 위해 주문 번호를 알려주실 수 있을까요?","actionDetail":"[eval] 1:skip(정보충분) -> 2:match(의도모호)","category":"DELIVERY","urgency":"HIGH","privacy":"LOW","nextAction":"ASK_FOR_INFO","neededInfo":["주문번호"]}
{"summary":"안타깝네요, 배달이 늦어진 것 같아요. 현재 상황을 확인하고 있습니다.","actionDetail":"[eval] 1:match(배달 지연) -> 2:skip(정보충분)","category":"DELIVERY","urgency":"HIGH","privacy":"LOW","nextAction":"ANSWER_DIRECTLY","neededInfo":[]}
{"summary":"음식이 1시간째 도착하지 않았네요. 배송 상태를 확인해보겠습니다.","actionDetail":"[eval] 1:match(배달 지연) -> 2:skip(정보충분)","category":"DELIVERY","urgency":"HIGH","privacy":"LOW","nextAction":"CHECK_MANUAL","neededInfo":[]}
{"summary":"음식이 아직 와있지 않네요. 배송이 지연된 것 같습니다.","actionDetail":"[eval] 1:match(배달 지연) -> 2:skip(정상)","category":"DELIVERY","urgency":"HIGH","privacy":"LOW","nextAction":"ANSWER_DIRECTLY","neededInfo":[]}
```

- nextAction: CHECK_MANUAL 2/5, ANSWER_DIRECTLY 2/5, ASK_FOR_INFO 1/5 (일관성 0.4)
- 언어 혼용 없음, 자연스러운 한국어 유지
- ASK_FOR_INFO 등장 → invariant 검증(`neededInfo` 비어있으면 예외) 실제 작동 확인 가능

---

## 요약

| temperature | nextAction 일관성 | 언어 혼용 |
|---|---|---|
| 0.0 | 1.0 (CHECK_MANUAL 5/5) | 고착 |
| 0.3 | 0.6 (2종 혼재) | 1회 |
| 0.7 | 0.4 (3종 혼재) | 없음 |

- category는 세 조건 모두 DELIVERY 100% — BeanOutputConverter schema 주입 효과
- temperature 0.0은 결정론적이지만 초기 품질 결함을 고착시키는 리스크 존재
- 운영 기본값 0.3 유지 권장. 단 프롬프트에 `반드시 한국어로만 응답` 명시 보강 필요
