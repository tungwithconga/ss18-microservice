# 🏦 RikkeiBank API — Hướng dẫn Khởi động Hệ thống

## Yêu cầu môi trường

| Tool | Version |
|---|---|
| Java | 17+ |
| Maven | 3.8+ |
| Docker & Docker Compose | 24+ |
| Postman | Latest |

---

## Bước 1 — Khởi động Infrastructure (Docker)

```bash
# Khởi động MySQL (x4), Redis, Kafka, Zookeeper
docker compose up -d

# Kiểm tra các container đã chạy
docker ps
```

Đợi khoảng 30 giây để MySQL và Kafka fully ready.

---

## Bước 2 — Khởi động Services theo đúng thứ tự

> ⚠️ **QUAN TRỌNG**: Phải khởi động đúng thứ tự

```bash
# 1. Config Server (port 8888) — PHẢI CHẠY ĐẦU TIÊN
cd config-server
mvn spring-boot:run

# 2. Eureka Server (port 8761)
cd eureka-server
mvn spring-boot:run

# 3. API Gateway (port 8080)
cd api-gateway
mvn spring-boot:run

# 4. Các business services (thứ tự không quan trọng)
cd identity-service    && mvn spring-boot:run
cd customer-service    && mvn spring-boot:run
cd account-service     && mvn spring-boot:run
cd transaction-service && mvn spring-boot:run
cd notification-service && mvn spring-boot:run
```

---

## Bước 3 — Kiểm tra Eureka Dashboard

Mở trình duyệt: http://localhost:8761

Phải thấy các services:
- ✅ IDENTITY-SERVICE
- ✅ CUSTOMER-SERVICE
- ✅ ACCOUNT-SERVICE
- ✅ TRANSACTION-SERVICE
- ✅ NOTIFICATION-SERVICE
- ✅ API-GATEWAY

---

## Bước 4 — Demo luồng nghiệp vụ qua Postman

### 4.1 Đăng ký & Đăng nhập

```http
# Đăng ký ADMIN
POST http://localhost:8080/api/auth/register
{
  "username": "admin",
  "password": "Admin@123",
  "role": "ADMIN"
}

# Đăng nhập → lấy accessToken
POST http://localhost:8080/api/auth/login
{
  "username": "admin",
  "password": "Admin@123"
}
```

### 4.2 Tạo dữ liệu mẫu (dùng accessToken từ bước 4.1)

```http
# Tạo khách hàng A
POST http://localhost:8080/api/customers
Authorization: Bearer {accessToken}
{
  "fullName": "Nguyễn Văn A",
  "email": "nguyenvana@gmail.com",
  "phone": "0901234567",
  "nationalId": "012345678901"
}

# Tạo loại tài khoản
POST http://localhost:8080/api/account-types
Authorization: Bearer {accessToken}
{
  "name": "Tài khoản thanh toán",
  "description": "Tài khoản giao dịch hàng ngày",
  "interestRate": 0.1,
  "minBalance": 50000
}

# Mở tài khoản cho khách hàng A (số dư 5,000,000)
POST http://localhost:8080/api/accounts
Authorization: Bearer {accessToken}
{
  "customerId": 1,
  "accountTypeId": 1,
  "initialBalance": 5000000,
  "currency": "VND"
}

# Mở tài khoản cho khách hàng B (số dư 0)
POST http://localhost:8080/api/accounts
Authorization: Bearer {accessToken}
{
  "customerId": 2,
  "accountTypeId": 1,
  "initialBalance": 0,
  "currency": "VND"
}
```

### 4.3 Thực hiện chuyển khoản (Saga Flow)

```http
POST http://localhost:8080/api/transactions/transfer
Authorization: Bearer {accessToken}
{
  "sourceAccountNo": "RK0000000001",
  "targetAccountNo": "RK0000000002",
  "amount": 1000000,
  "description": "Chuyển tiền test"
}
```

**Kết quả mong đợi:**
- Response status: 201 Created
- Transaction status: SUCCESS
- notification-service logs: "📲 Chuyển khoản THÀNH CÔNG"

### 4.4 Demo Saga Rollback

Để test rollback, đóng băng tài khoản đích trước:
```http
PUT http://localhost:8080/api/accounts/2/status
Authorization: Bearer {accessToken}
{ "status": "FROZEN" }
```

Rồi thực hiện chuyển khoản → Credit sẽ fail → Saga compensate:
```http
POST http://localhost:8080/api/transactions/transfer
Authorization: Bearer {accessToken}
{
  "sourceAccountNo": "RK0000000001",
  "targetAccountNo": "RK0000000002",
  "amount": 1000000
}
```

**Kết quả mong đợi:**
- Response status: 422 Unprocessable Entity
- Transaction status: **COMPENSATED**
- notification-service logs: "❌ Chuyển khoản THẤT BẠI"
- Số dư tài khoản nguồn **không thay đổi** (đã hoàn tiền)

### 4.5 Demo Circuit Breaker

```bash
# Dừng account-service
# Thực hiện nhiều request → Circuit Breaker sẽ OPEN
# Kiểm tra trạng thái CB:
GET http://localhost:8084/actuator/health
```

### 4.6 Demo Force Logout (ADMIN)

```http
POST http://localhost:8080/api/auth/revoke/2
Authorization: Bearer {adminToken}
X-Target-Access-Token: {userAccessToken}
```

Sau đó request với token đã revoke sẽ nhận 401.

### 4.7 Kiểm tra Redis Cache

```bash
# Xem cache hits qua Actuator
GET http://localhost:8082/actuator/metrics/cache.gets

# Xem Redis keys
docker exec -it redis redis-cli KEYS "customer::*"
```

---

## Bước 5 — Chạy Unit Tests & JaCoCo Report

```bash
# Chạy tất cả tests + generate report
mvn verify

# Xem report JaCoCo
open transaction-service/target/site/jacoco/index.html
open identity-service/target/site/jacoco/index.html
```

---

## Cấu trúc dự án hoàn chỉnh

```
rikkeibank-api/
├── docker-compose.yml          # Infrastructure
├── pom.xml                     # Parent POM
├── config-server/              # Port 8888
├── eureka-server/              # Port 8761
├── api-gateway/                # Port 8080
│   └── filter/JwtAuthFilter    # JWT validation + Redis blacklist
├── identity-service/           # Port 8081
│   ├── entity/User, RefreshToken
│   ├── service/AuthService     # Login, Refresh, Force-Revoke
│   ├── util/JwtUtil            # JWT generate/parse
│   └── test/AuthServiceTest
├── customer-service/           # Port 8082
│   ├── entity/Customer, Staff
│   ├── service/CustomerService # Cache-Aside @Cacheable
│   └── controller/CustomerController
├── account-service/            # Port 8083
│   ├── entity/Account          # @Version optimistic lock
│   ├── service/AccountService  # debit/credit/reverseDebit
│   └── controller/AccountController  # Internal Saga endpoints
├── transaction-service/        # Port 8084
│   ├── entity/Transaction, SagaState
│   ├── saga/SagaOrchestrator   # ← CORE: Saga Pattern
│   ├── client/AccountServiceClient   # Feign + Circuit Breaker
│   ├── event/Transfer*Event    # Kafka events
│   └── test/SagaOrchestratorTest
└── notification-service/       # Port 8085
    ├── consumer/NotificationConsumer # Kafka consumer
    └── event/Transfer*Event
```

---

## Kafka Topics

| Topic | Producer | Consumer |
|---|---|---|
| `transfer.success` | transaction-service | notification-service |
| `transfer.failed` | transaction-service | notification-service |

---

## Liên hệ & Đóng góp

Dự án Mini Project — Session 18 — Rikkei Academy
