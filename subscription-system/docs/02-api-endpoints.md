# 구독 시스템 API 명세서

> **구독 기반 결제 시스템의 REST API 엔드포인트 명세**

## 📋 목차
1. [구독 플랜 관리 API](#1-구독-플랜-관리-api)
2. [결제 수단 관리 API](#2-결제-수단-관리-api)
3. [구독 관리 API](#3-구독-관리-api)

---

## 1. 구독 플랜 관리 API

### 1.1 플랜 생성
**`POST /api/plans`**

새로운 구독 플랜을 생성합니다.

**Request Body:**
```json
{
  "name": "프리미엄 플랜",
  "description": "월간 프리미엄 구독",
  "price": 9900,
  "billingInterval": "monthly",
  "trialPeriodDays": 7
}
```

**Response (200 OK):**
```json
{
  "planId": 1,
  "name": "프리미엄 플랜",
  "description": "월간 프리미엄 구독",
  "price": 9900.00,
  "billingInterval": "monthly",
  "trialPeriodDays": 7,
  "status": "ACTIVE",
  "createdAt": "2024-01-01T00:00:00"
}
```

### 1.2 모든 플랜 조회
**`GET /api/plans`**

모든 플랜 목록을 조회합니다.

**Response (200 OK):**
```json
[
  {
    "planId": 1,
    "name": "프리미엄 플랜",
    "description": "월간 프리미엄 구독",
    "price": 9900.00,
    "billingInterval": "monthly",
    "trialPeriodDays": 7,
    "status": "ACTIVE"
  }
]
```

### 1.3 활성 플랜 조회
**`GET /api/plans/active`**

활성 상태인 플랜만 조회합니다.

**Response (200 OK):**
```json
[
  {
    "planId": 1,
    "name": "프리미엄 플랜",
    "price": 9900.00,
    "billingInterval": "monthly",
    "status": "ACTIVE"
  }
]
```

### 1.4 플랜 상세 조회
**`GET /api/plans/{planId}`**

특정 플랜의 상세 정보를 조회합니다.

**Response (200 OK):**
```json
{
  "planId": 1,
  "name": "프리미엄 플랜",
  "description": "월간 프리미엄 구독",
  "price": 9900.00,
  "billingInterval": "monthly",
  "trialPeriodDays": 7,
  "status": "ACTIVE"
}
```

### 1.5 플랜 상태 변경
**`PUT /api/plans/{planId}/status?status=ACTIVE`**

플랜의 상태를 변경합니다.

**Query Parameters:**
- `status`: `ACTIVE` 또는 `INACTIVE`

**Response (200 OK):**
```json
{
  "planId": 1,
  "name": "프리미엄 플랜",
  "status": "ACTIVE"
}
```

---

## 2. 결제 수단 관리 API

### 2.1 결제 수단 등록
**`POST /api/payment-methods/user/{userId}`**

사용자의 결제 수단을 등록합니다.

**Request Body:**
```json
{
  "customerUid": "customer_123456",
  "billingKey": "billing_key_123456",
  "cardBrand": "visa",
  "last4": "1234",
  "isDefault": true
}
```

**Response (200 OK):**
```json
{
  "methodId": 1,
  "userId": 1,
  "customerUid": "customer_123456",
  "billingKey": "billing_key_123456",
  "cardBrand": "visa",
  "last4": "1234",
  "isDefault": true,
  "createdAt": "2024-01-01T00:00:00"
}
```

**Response (400 Bad Request):**
```json
{
  "error": "customerUid는 필수입니다."
}
```

### 2.2 사용자 결제 수단 목록 조회
**`GET /api/payment-methods/user/{userId}`**

특정 사용자의 모든 결제 수단을 조회합니다.

**Response (200 OK):**
```json
[
  {
    "methodId": 1,
    "customerUid": "customer_123456",
    "cardBrand": "visa",
    "last4": "1234",
    "isDefault": true
  }
]
```

### 2.3 결제 수단 상세 조회
**`GET /api/payment-methods/{methodId}`**

특정 결제 수단의 상세 정보를 조회합니다.

**Response (200 OK):**
```json
{
  "methodId": 1,
  "customerUid": "customer_123456",
  "billingKey": "billing_key_123456",
  "cardBrand": "visa",
  "last4": "1234",
  "isDefault": true
}
```

### 2.4 기본 결제 수단 설정
**`PUT /api/payment-methods/user/{userId}/default/{methodId}`**

사용자의 기본 결제 수단을 설정합니다.

**Response (200 OK):**
```json
{
  "methodId": 1,
  "isDefault": true
}
```

### 2.5 결제 수단 삭제
**`DELETE /api/payment-methods/user/{userId}/{methodId}`**

결제 수단을 삭제합니다.

**Response (200 OK):**
```json
{
  "message": "Payment method deleted successfully"
}
```

### 2.6 빌링키 발급
**`POST /api/payment-methods/user/{userId}/issue-billing-key`**

서버를 통한 빌링키 발급 (결제 완료 후 빌링키 등록용).

**Request Body:**
```json
{
  "customerUid": "customer_123456",
  "amount": 1000,
  "orderName": "빌링키 발급"
}
```

**Response (200 OK):**
```json
{
  "billingKey": "billing_key_123456",
  "customerUid": "customer_123456",
  "message": "Billing key issued successfully"
}
```

**Response (400 Bad Request):**
```json
{
  "error": "결제 금액은 최소 1,000원 이상이어야 합니다."
}
```

### 2.7 등록된 결제 수단으로 결제 실행
**`POST /api/payment-methods/user/{userId}/execute-payment`**

등록된 결제 수단을 사용하여 결제를 실행합니다.

**Request Body:**
```json
{
  "methodId": 1,
  "amount": 9900,
  "orderName": "구독료 결제"
}
```

**Response (200 OK):**
```json
{
  "impUid": "imp_1234567890",
  "merchantUid": "merchant_1234567890",
  "amount": 9900,
  "status": "paid"
}
```

---

## 3. 구독 관리 API

### 3.1 구독 생성
**`POST /api/subscriptions/user/{userId}`**

새로운 구독을 생성합니다.

**Request Body:**
```json
{
  "planId": 1,
  "paymentMethodId": 1
}
```

**Response (200 OK):**
```json
{
  "subscription": {
    "subscriptionId": 1,
    "userId": 1,
    "planId": 1,
    "planName": "프리미엄 플랜",
    "paymentMethodId": 1,
    "status": "TRIALING",
    "currentPeriodStart": "2024-01-01T00:00:00",
    "currentPeriodEnd": "2024-02-01T00:00:00",
    "trialEnd": "2024-01-08T00:00:00",
    "startedAt": "2024-01-01T00:00:00"
  },
  "message": "Subscription created successfully"
}
```

**Response (400 Bad Request):**
```json
{
  "error": "Plan is not active: 1"
}
```

### 3.2 사용자 구독 목록 조회
**`GET /api/subscriptions/user/{userId}`**

특정 사용자의 모든 구독을 조회합니다.

**Response (200 OK):**
```json
[
  {
    "subscriptionId": 1,
    "userId": 1,
    "planId": 1,
    "planName": "프리미엄 플랜",
    "status": "ACTIVE",
    "currentPeriodStart": "2024-01-01T00:00:00",
    "currentPeriodEnd": "2024-02-01T00:00:00"
  }
]
```

### 3.3 구독 상세 조회
**`GET /api/subscriptions/{subscriptionId}`**

특정 구독의 상세 정보를 조회합니다.

**Response (200 OK):**
```json
{
  "subscriptionId": 1,
  "userId": 1,
  "planId": 1,
  "planName": "프리미엄 플랜",
  "paymentMethodId": 1,
  "status": "ACTIVE",
  "currentPeriodStart": "2024-01-01T00:00:00",
  "currentPeriodEnd": "2024-02-01T00:00:00",
  "trialEnd": null,
  "startedAt": "2024-01-01T00:00:00"
}
```

### 3.4 구독 취소
**`POST /api/subscriptions/user/{userId}/cancel/{subscriptionId}`**

구독을 취소합니다.

**Response (200 OK):**
```json
{
  "subscription": {
    "subscriptionId": 1,
    "status": "CANCELED",
    "canceledAt": "2024-01-15T00:00:00"
  },
  "message": "Subscription canceled successfully"
}
```

**Response (400 Bad Request):**
```json
{
  "error": "Subscription is already canceled or ended"
}
```

---

## 📝 공통 사항

### 상태 코드
- `200 OK`: 요청 성공
- `400 Bad Request`: 잘못된 요청
- `404 Not Found`: 리소스를 찾을 수 없음
- `500 Internal Server Error`: 서버 오류

### 에러 응답 형식
```json
{
  "error": "에러 메시지"
}
```

### CORS
모든 API는 `CrossOrigin(origins = "*")`로 설정되어 있어 모든 도메인에서 접근 가능합니다.

### 구독 상태 (SubscriptionStatus)
- `TRIALING`: 체험 중
- `ACTIVE`: 활성
- `PAST_DUE`: 결제 연체
- `CANCELED`: 취소됨
- `ENDED`: 종료됨

### 플랜 상태 (PlanStatus)
- `ACTIVE`: 활성
- `INACTIVE`: 비활성

### 빌링 주기 (BillingInterval)
- `monthly`: 월간
- `yearly` 또는 `annual`: 연간

