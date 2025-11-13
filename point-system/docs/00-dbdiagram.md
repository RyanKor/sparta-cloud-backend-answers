# 포인트 결제 시스템을 위한 DB Diagram IO에서 사용할 ERD 구성 스크립트

```
//// --------------------------------------
//// ERD for E-commerce Order/Payment System
//// (with Membership & Points)
//// --------------------------------------

//// --------------------------------------
//// 1. Existing Tables (Orders modified)
//// --------------------------------------
Table Users as U {
  user_id int [pk, increment]
  email varchar(255) [not null, unique]
  password_hash varchar(255) [not null]
  name varchar(100)
  created_at timestamp [default: `now()`]
}

Table Products as Prod {
  product_id int [pk, increment]
  name varchar(255) [not null]
  price decimal(10, 2) [not null]
  stock int [not null, default: 0]
  description text
  created_at timestamp [default: `now()`]
}

// [MODIFIED] Added point usage columns
Table Orders as O {
  order_id varchar(255) [pk, note: 'e.g., UUID or custom ID like ord_...']
  user_id int [not null]
  total_amount decimal(10, 2) [not null, note: 'Final amount after all discounts (including points)']
  points_used int [not null, default: 0, note: 'Number of points redeemed in this order']
  points_discount_amount decimal(10, 2) [not null, default: 0.00, note: 'Monetary value of redeemed points']
  status varchar(50) [not null, note: 'e.g., PENDING_PAYMENT, COMPLETED, CANCELLED']
  ordered_at timestamp [default: `now()`]
}

Table OrderItems as OI {
  order_item_id int [pk, increment]
  order_id varchar(255) [not null]
  product_id int [not null]
  quantity int [not null]
  price decimal(10, 2) [not null, note: 'Price at the time of order']
}

Table Payments as Pay {
  payment_id int [pk, increment]
  order_id varchar(255) [not null, unique]
  method_id int [note: 'FK to PaymentMethods, nullable for one-time payments']
  imp_uid varchar(255) [unique, note: 'Transaction ID from PortOne']
  amount decimal(10, 2) [not null]
  status varchar(50) [not null, note: 'e.g., PAID, FAILED, REFUNDED, PARTIALLY_REFUNDED']
  payment_method varchar(100)
  paid_at timestamp
}

Table Refunds as R {
  refund_id int [pk, increment]
  payment_id int [not null]
  amount decimal(10, 2) [not null, note: 'Supports partial refunds']
  reason text
  status varchar(50) [not null, note: 'e.g., COMPLETED, FAILED']
  refunded_at timestamp [default: `now()`]
}

//// --------------------------------------
//// 2. New Membership & Point Tables
//// --------------------------------------
Table MembershipLevels as ML {
  level_id int [pk, increment]
  name varchar(50) [not null, unique, note: 'e.g., BRONZE, SILVER, GOLD']
  point_accrual_rate decimal(5, 4) [not null, default: 0.01, note: 'e.g., 0.01 for 1% of order amount']
  benefits_description text
}

Table Memberships as M {
  membership_id int [pk, increment]
  user_id int [not null, unique, note: 'One-to-one relationship with Users']
  level_id int [not null]
  joined_at timestamp [default: `now()`]
  expires_at timestamp [null, note: 'Nullable for non-expiring memberships']
}

Table PointTransactions as PT {
  transaction_id int [pk, increment]
  user_id int [not null]
  order_id varchar(255) [null, note: 'Link to order if points earned/spent on a purchase']
  points int [not null, note: 'Positive for earned, negative for spent/expired']
  type varchar(50) [not null, note: 'EARNED, SPENT, EXPIRED, ADJUSTMENT']
  description varchar(255)
  created_at timestamp [default: `now()`]
  expires_at timestamp [null, note: 'Applicable only for EARNED points to track expiration']
}

//// --------------------------------------
//// 3. Relationships
//// --------------------------------------

// --- Original Relationships ---
Ref: U.user_id < O.user_id
Ref: Prod.product_id < OI.product_id
Ref: O.order_id < OI.order_id
Ref: O.order_id - Pay.order_id // One-to-One
Ref: Pay.payment_id < R.payment_id

// --- New Membership & Point Relationships ---
Ref: U.user_id < M.user_id // One-to-One
Ref: ML.level_id < M.level_id // One-to-Many
Ref: U.user_id < PT.user_id // One-to-Many
Ref: O.order_id < PT.order_id // One-to-Many (An order can result in EARNED and/or SPENT transactions)
```