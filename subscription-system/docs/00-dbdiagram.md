# 구독 결제 시스템을 위한 DB Diagram IO에서 사용할 ERD 구성 스크립트

```
//// --------------------------------------
//// 기존 사용자 테이블 (참조용)
//// --------------------------------------
Table Users as U {
  user_id int [pk, increment]
  email varchar(255) [not null, unique]
  password_hash varchar(255) [not null]
  name varchar(100)
  created_at timestamp [default: `now()`]
}

//// --------------------------------------
//// 1. 구독 플랜 (Plan)
//// --------------------------------------
Table Plans as P {
  plan_id int [pk, increment]
  name varchar(255) [not null, note: 'e.g., 베이직, 프로, 프리미엄']
  description text
  price decimal(10, 2) [not null]
  billing_interval varchar(50) [not null, note: 'e.g., monthly, yearly']
  trial_period_days int [not null, default: 0]
  status varchar(50) [not null, default: 'active', note: 'e.g., active, inactive']
  created_at timestamp [default: `now()`]
}

//// --------------------------------------
//// 2. 정기 결제 수단 (Payment Methods)
//// --------------------------------------
Table PaymentMethods as PM {
  method_id int [pk, increment]
  user_id int [not null, ref: > U.user_id]
  customer_uid varchar(255) [not null, unique, note: 'PortOne 정기결제용 빌링키 (customer_uid)']
  card_brand varchar(50) [note: 'e.g., Visa, Mastercard']
  last4 varchar(4) [note: 'e.g., 4242']
  is_default boolean [not null, default: false]
  created_at timestamp [default: `now()`]
}

//// --------------------------------------
//// 3. 사용자 구독 내역 (Subscriptions)
//// --------------------------------------
Table Subscriptions as Sub {
  subscription_id int [pk, increment]
  user_id int [not null, ref: > U.user_id]
  plan_id int [not null, ref: > P.plan_id]
  payment_method_id int [ref: > PM.method_id, note: '사용자가 선택한 기본 결제 수단']
  status varchar(50) [not null, note: 'e.g., trialing, active, past_due, canceled, ended']
  current_period_start timestamp [not null, note: '현재 빌링 주기 시작일']
  current_period_end timestamp [not null, note: '다음 결제 예정일 (현재 주기 종료일)']
  trial_end timestamp [note: '체험 기간 종료일']
  started_at timestamp [default: `now()`, note: '구독 시작일']
  canceled_at timestamp [note: '사용자가 취소를 *요청*한 시간']
  ended_at timestamp [note: '구독 접근이 *실제* 종료된 시간']
}

//// --------------------------------------
//// 4. 정기 결제 청구 내역 (Subscription Invoices)
//// --------------------------------------
Table SubscriptionInvoices as SI {
  invoice_id int [pk, increment]
  subscription_id int [not null, ref: > Sub.subscription_id]
  amount decimal(10, 2) [not null]
  status varchar(50) [not null, note: 'e.g., paid, failed, pending, refunded']
  due_date timestamp [not null, note: '결제 시도일 (결제 예정일)']
  paid_at timestamp [note: '결제 성공일']
  imp_uid varchar(255) [unique, note: 'PortOne 거래 ID (결제 성공 시)']
  attempt_count int [not null, default: 0]
  error_message text [note: '결제 실패 시 사유']
  created_at timestamp [default: `now()`]
}

//// --------------------------------------
//// 5. 구독 환불 내역 (Subscription Refunds)
//// --------------------------------------
Table SubscriptionRefunds as SR {
  refund_id int [pk, increment]
  invoice_id int [not null, ref: > SI.invoice_id]
  amount decimal(10, 2) [not null]
  reason text
  status varchar(50) [not null, note: 'e.g., completed, failed']
  refunded_at timestamp [default: `now()`]
}
```