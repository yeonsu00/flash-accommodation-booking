# Flash Accommodation Booking

재고 정합성, 공정성, 멱등성, 결제 확장성, 장애 대응 등 주요 기술적 쟁점과 선택의 근거는 [DECISIONS.md](./DECISIONS.md)에 정리했습니다.

브랜치별 구체적인 작업 내용은 PR 메시지로 정리해두었습니다. 

## 기술 스택

| 분류 | 기술 |
|------|------|
| Language | Java 21 |
| Framework | Spring Boot 3.5 |
| Database | MySQL 8.0 |
| Cache | Redis 7 |
| Infra | Docker, Nginx (애플리케이션 서버 2대) |
| 분산 락 | Redisson |
| 장애 대응 | Resilience4j (CircuitBreaker, Retry) |

---

## 시스템 아키텍처

```
         ┌─────────────────────────────────────────┐
         │              Client                     │
         └────────────────────┬────────────────────┘
                              │ :80
         ┌────────────────────▼────────────────────┐
         │          Nginx (Load Balancer)           │
         │             Round-Robin                 │
         └──────────┬──────────────────┬───────────┘
                    │                  │
         ┌──────────▼──────┐  ┌────────▼────────┐
         │   App Server 1  │  │  App Server 2   │
         │  Spring Boot    │  │  Spring Boot    │
         │   :8080         │  │   :8080         │
         └──────────┬──────┘  └────────┬────────┘
                    │                  │
         ┌──────────▼──────────────────▼──────────┐
         │                                        │
         │   MySQL :3306        Redis :6379        │
         │                                        │
         └────────────────────────────────────────┘
```

---

## 실행 방법

### 1. 환경변수 파일 생성

`.env.example`을 참고해 프로젝트 루트에 `.env` 파일을 생성합니다. 

```bash
cp .env.example .env
```

### 2. 애플리케이션 빌드

```bash
./gradlew build -x test
```

### 3. Docker Compose 실행

```bash
docker-compose up --build
```


### 4. API 확인

- Swagger UI: `http://localhost/swagger-ui/index.html`

---

## API 목록

### 대기열

| Method | Path | 설명 |
|--------|------|------|
| `POST` | `/queue/enter` | 대기열 등록 및 대기열 토큰 발급 |
| `GET` | `/queue/status?queueToken={token}` | 현재 대기 순번 및 상태 조회 |

### 재고 선점 / 주문서

| Method | Path | 설명 |
|--------|------|------|
| `POST` | `/checkout` | 재고 선점 및 결제용 checkoutToken 발급 (TTL 5분) |
| `GET` | `/checkout/{checkoutToken}` | 주문서 조회 (상품 정보 + 포인트 잔액) |

### 예약

| Method | Path | 설명 |
|--------|------|------|
| `POST` | `/bookings` | 결제 진행 및 예약 확정 |

`POST /bookings` 요청 헤더에 `Idempotency-Key`를 포함해야 합니다.

---

## 시퀀스 다이어그램

### 전체 흐름

```mermaid
sequenceDiagram
    actor User
    participant Q as Queue API
    participant Scheduler
    participant C as Checkout API
    participant B as Booking API

    User->>Q: POST /queue/enter (userId, productId)
    Q->>Q: 대기열에 score=수신 시각으로 등록
    Q-->>User: queueToken

    loop 폴링 (100ms 간격 서버 배치)
        User->>Q: GET /queue/status?queueToken=...
        Q-->>User: WAITING (rank: N)
        Scheduler->>Scheduler: 100ms마다 5명씩 ADMITTED 처리
    end

    User->>Q: GET /queue/status?queueToken=...
    Q-->>User: ADMITTED

    User->>C: POST /checkout (queueToken)
    C->>C: 입장 허가 검증
    C->>C: Redis Lua 스크립트로 재고 원자 차감
    C->>C: checkoutToken 발급 (Redis + DB 이중 저장)
    C-->>User: checkoutToken

    User->>C: GET /checkout/{checkoutToken}
    C-->>User: 상품 정보 (명칭, 가격, 체크인/아웃) + 포인트 잔액

    User->>B: POST /bookings (Idempotency-Key 헤더)
    B-->>User: bookingId
```

### 예약 API 상세 (멱등성 + 결제 보상)

```mermaid
sequenceDiagram
    participant Client
    participant API as Booking API
    participant Redis
    participant DB
    participant PG as PG (Simulated)

    Client->>API: POST /bookings (Idempotency-Key 헤더 포함)
    API->>Redis: 이 요청이 처음인지 확인 (멱등성 키 조회)

    alt 이미 처리 중이거나 완료된 요청
        Redis-->>API: 이미 처리된 요청
        API->>Redis: 완료된 예약 ID 조회
        Redis-->>API: bookingId
        API-->>Client: 기존 예약 ID 반환 (200)
    else 처음 들어온 요청
        Redis-->>API: 최초 요청 확인
        API->>DB: 예약 생성 (결제 대기 상태)
        API->>DB: 결제 기록 생성 (중복 방지용 UNIQUE 제약)
        loop 결제 수단별
            API->>PG: 결제 승인 요청 (실패 시 최대 3회 재시도)
            PG-->>API: 승인 완료
        end
        API->>DB: 예약 확정
        Note over API,DB: 트랜잭션 커밋 후
        API->>Redis: 완료된 예약 ID 저장 (이후 중복 요청 대비)
        API->>DB: 결제 토큰 삭제
        API-->>Client: 예약 ID 반환 (200)
    end

    Note over API,DB: 결제 중 오류 발생 시 (트랜잭션 롤백 후)
    API->>PG: 이미 승인된 결제 수단 취소
    API->>Redis: 멱등성 키 삭제 (다음 재시도 허용)
```

### PG사 장애 시 대응 흐름

```mermaid
sequenceDiagram
    participant Client
    participant API as Booking API
    participant PG as PG (Simulated)

    Client->>API: POST /bookings (결제 요청)

    alt PG 정상
        API->>PG: 결제 승인 요청
        PG-->>API: 승인 성공
        API-->>Client: 예약 완료 (200)

    else PG 일시적 오류 (네트워크 오류 / 타임아웃)
        API->>PG: 결제 승인 요청 (1차 시도)
        PG-->>API: 실패
        API->>PG: 결제 승인 요청 (2차 재시도, 500ms 후)
        PG-->>API: 실패
        API->>PG: 결제 승인 요청 (3차 재시도, 500ms 후)
        PG-->>API: 성공
        API-->>Client: 예약 완료 (200)

    else 재시도 3회 모두 실패
        API->>PG: 결제 승인 요청 (1~3차 시도 모두 실패)
        PG-->>API: 실패
        Note over API: 실패율 누적 → 임계치(50%) 초과 시 서킷 OPEN
        API-->>Client: 503 결제 서비스 일시 불가

    else 서킷 OPEN 상태 (PG 장애 지속 중)
        Note over API: PG 호출 없이 즉시 실패 처리
        API-->>Client: 503 결제 서비스 일시 불가
    end
```

### Redis 장애 시 재고 선점 폴백

```mermaid
sequenceDiagram
    participant API as Checkout API
    participant Redis
    participant DB

    API->>Redis: 재고 차감 요청

    alt Redis 정상
        Redis-->>API: 재고 차감 성공
        API->>DB: 결제 토큰 저장 (Redis 장애 대비 백업)
    else Redis 장애
        Redis-->>API: 장애로 실패
        API->>DB: DB 락으로 재고 조회 (동시성 보장)
        alt 재고 있음
            DB-->>API: 재고 확인
            API->>DB: 재고 차감 + 결제 토큰 저장
            API-->>API: 재고 선점 성공
        else 재고 없음
            API-->>API: 409 품절
        end
    end
```

---

## ERD

```mermaid
erDiagram
    users {
        BIGINT id PK
        VARCHAR(100) name
        INT point
        DATETIME created_at
        DATETIME updated_at
    }
    accommodation_product {
        BIGINT id PK
        VARCHAR(200) name
        INT price
        DATETIME check_in
        DATETIME check_out
        DATETIME open_at
        INT stock
        VARCHAR(20) status
        DATETIME created_at
        DATETIME updated_at
    }
    booking {
        BIGINT id PK
        BIGINT user_id FK
        BIGINT accommodation_product_id FK
        VARCHAR(20) status
        INT total_amount
        DATETIME created_at
        DATETIME updated_at
    }
    payment {
        BIGINT id PK
        BIGINT booking_id
        VARCHAR(64) idempotency_key UK
        VARCHAR(20) status
        INT total_amount
        DATETIME created_at
        DATETIME updated_at
    }
    payment_method_detail {
        BIGINT id PK
        BIGINT payment_id FK
        VARCHAR(20) method_type
        INT amount
        VARCHAR(100) pg_transaction_id
        DATETIME created_at
    }
    checkout_token {
        VARCHAR token PK
        BIGINT user_id
        BIGINT product_id
        DATETIME expired_at
        DATETIME created_at
        DATETIME updated_at
    }

    users ||--o{ booking : ""
    accommodation_product ||--o{ booking : ""
    booking ||--|| payment : ""
    payment ||--o{ payment_method_detail : ""
```

---

## DDL

```sql
CREATE TABLE users (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(100) NOT NULL,
    point       INT          NOT NULL,
    created_at  DATETIME(6),
    updated_at  DATETIME(6),
    PRIMARY KEY (id)
);

CREATE TABLE accommodation_product (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(200) NOT NULL,
    price       INT          NOT NULL,
    check_in    DATETIME(6)  NOT NULL,
    check_out   DATETIME(6)  NOT NULL,
    open_at     DATETIME(6)  NOT NULL,
    stock       INT          NOT NULL,
    status      VARCHAR(20)  NOT NULL,
    created_at  DATETIME(6),
    updated_at  DATETIME(6),
    PRIMARY KEY (id)
);

CREATE TABLE booking (
    id                       BIGINT      NOT NULL AUTO_INCREMENT,
    user_id                  BIGINT      NOT NULL,
    accommodation_product_id BIGINT      NOT NULL,
    status                   VARCHAR(20) NOT NULL,
    total_amount             INT         NOT NULL,
    created_at               DATETIME(6),
    updated_at               DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_booking_user    FOREIGN KEY (user_id)                  REFERENCES users (id),
    CONSTRAINT fk_booking_product FOREIGN KEY (accommodation_product_id) REFERENCES accommodation_product (id)
);

CREATE TABLE payment (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    booking_id       BIGINT      NOT NULL,
    idempotency_key  VARCHAR(64) NOT NULL,
    status           VARCHAR(20) NOT NULL,
    total_amount     INT         NOT NULL,
    created_at       DATETIME(6),
    updated_at       DATETIME(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_idempotency_key (idempotency_key)
);

CREATE TABLE payment_method_detail (
    id                BIGINT      NOT NULL AUTO_INCREMENT,
    payment_id        BIGINT      NOT NULL,
    method_type       VARCHAR(20) NOT NULL,
    amount            INT         NOT NULL,
    pg_transaction_id VARCHAR(100),
    created_at        DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_payment_method_detail_payment FOREIGN KEY (payment_id) REFERENCES payment (id)
);

CREATE TABLE checkout_token (
    token       VARCHAR(255) NOT NULL,
    user_id     BIGINT       NOT NULL,
    product_id  BIGINT       NOT NULL,
    expired_at  DATETIME(6)  NOT NULL,
    created_at  DATETIME(6),
    updated_at  DATETIME(6),
    PRIMARY KEY (token)
);
```

---

## Redis 키 구조

| 키 | 타입 | 설명 | TTL |
|----|------|------|-----|
| `open:{productId}` | Hash | 상품 오픈 시각 (`openAt` 필드) | 없음 |
| `queue:waiting:{productId}` | Sorted Set | 대기열 (score = 요청 수신 timestamp) | 없음 |
| `queue:admitted:{productId}` | Set | 입장 허가된 토큰 목록 | 없음 |
| `queue:token:{queueToken}` | Hash | 대기열 토큰 정보 (userId, productId, status) | 30분 |
| `stock:{productId}` | String | Redis 재고 수량 | 없음 |
| `checkout:{checkoutToken}` | String | `userId:productId` | 5분 |
| `idempotency:{idempotencyKey}` | String | `"processing"` 또는 `bookingId` | 24시간 |
