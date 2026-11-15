# 와트잇 서버

와트잇 차량 앱이 부르는 서버입니다. Python 3.13, FastAPI, PostgreSQL.

- 요금 엔진: 계절·시간대별 전기요금(`config/tariff.yaml`, 출처 표기)으로 가장 싼 충전 구간을 계산합니다.
- 충전 계획 API: `POST /api/car/plan`, `POST /api/car/briefing`, `POST /api/car/leak-check`, `POST /api/car/charge-target`, 절약 리포트, 출발 시각 학습, 음성 대화(`POST /api/car/voice`).
- 충전소 API: `GET /api/chargers`(지금 빈 충전기만), `POST /api/car/chargers/along-route`(경로 위 빈 급속충전기). 공공데이터포털 충전기 정보를 주기적으로 수집합니다.
- API 명세: `docs/car-api.md`. 차량 앱에서 언제 어떤 값을 보내는지: `docs/car-app-guide.md`.

## 실행

```bash
cp .env.example .env        # DATABASE_URL, CHARGER_API_KEY 등을 채운다
uv sync
uv run alembic upgrade head
uv run uvicorn evcharge.main:app --app-dir src --port 8000
```

음성 대화는 `ANTHROPIC_API_KEY`가 있어야 동작합니다(키는 서버에만 두고 앱에 넣지 않습니다). 없으면 그 API만 꺼지고 나머지는 동작합니다.
`CAR_IP_HASH_SECRET`은 `openssl rand -hex 32`로 만든 값을 넣습니다.

## 테스트

```bash
uv run ruff check .
uv run pytest -q      # PostgreSQL 필요 (.env의 DATABASE_URL)
```
