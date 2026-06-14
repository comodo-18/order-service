# OrderService 🔐

A Spring Boot microservice implementing **JWT authentication** and **order management** with inter-service communication to InventorySync for stock reservation. Part of the Distributed Product Catalog Platform.

[![Live](https://img.shields.io/badge/Live-Render-46E3B7?style=flat-square&logo=render)](https://order-service-11yu.onrender.com/swagger-ui.html)
[![Java](https://img.shields.io/badge/Java-21-orange?style=flat-square&logo=java)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-brightgreen?style=flat-square&logo=springboot)](https://spring.io/projects/spring-boot)

---

## 🚀 Live Demo

**API Docs (Swagger):** https://order-service-11yu.onrender.com/swagger-ui.html

> Note: Free tier — first request may take 50+ seconds to wake up.

---

## 🏗️ Architecture

```
Varenya (Next.js Frontend)
       ↓ JWT
OrderService (this service)
  ├── /api/auth/register  → creates user, issues JWT
  ├── /api/auth/login     → authenticates, issues JWT
  ├── /api/orders         → places order (JWT required)
  └── /api/orders/my      → user's order history (JWT required)
       ↓ REST call
InventorySync → Redisson Lock → Reserve Stock
```

---

## ✨ Key Features

- **JWT authentication** — stateless auth with HMAC-SHA256 signed tokens, BCrypt password hashing, 24-hour expiration
- **Role-based security** — Spring Security filter chain with `ROLE_USER` embedded in JWT claims
- **Inter-service communication** — OrderService calls InventorySync's reserve endpoint to decrement stock on order placement
- **DTO pattern** — request/response objects separate API contract from database schema; sensitive fields (password hash) never leak
- **Global exception handler** — `@RestControllerAdvice` catches validation errors and business exceptions, returns clean JSON instead of Spring's default error page
- **Production-ready security** — credentials via environment variables, JWT secret never committed to source

---

## 🔑 Trade-off Decisions

| Decision | Choice | Why |
|----------|--------|-----|
| Auth | JWT (stateless) | No server-side sessions — fits microservices, horizontally scalable |
| Password storage | BCrypt | Adaptive hashing, industry standard, built into Spring Security |
| Token storage | Client-side | No token table in DB — validation is self-contained via signature |
| Inter-service call | Synchronous REST | Simpler than async for MVP; Kafka event planned for decoupling |
| User ID in orders | Plain `Long` | No JPA `@ManyToOne` join — microservices don't share entity graphs |
| Filter registration | `FilterRegistrationBean(enabled=false)` | Prevents `JwtAuthFilter` double registration as both servlet filter and security filter |

---

## 🔒 Authentication Flow

```
1. User registers:
   POST /api/auth/register { username, email, password }
       ↓
   Password hashed with BCrypt → User saved to DB
       ↓
   JWT generated: HMACSHA256(base64(header) + "." + base64(payload), SECRET_KEY)
       ↓
   Response: { token, username, email, role }

2. User places order:
   POST /api/orders (Authorization: Bearer <token>)
       ↓
   JwtAuthFilter extracts username from token
       ↓
   Token validated (signature + expiration)
       ↓
   SecurityContext set → request proceeds
       ↓
   OrderService calls InventorySync → stock reserved
       ↓
   Order saved as CONFIRMED
```

---

## 📡 API Endpoints

### Public (no token required)

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/auth/register` | Register a new user |
| `POST` | `/api/auth/login` | Login and receive JWT |
| `GET`  | `/actuator/health` | Health check |

### Protected (JWT required)

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/orders` | Place an order (reserves stock via InventorySync) |
| `GET`  | `/api/orders/my` | Get current user's order history |

---

## 📦 Project Structure

```
com.anurag.order_service
├── config/          SecurityConfig, RestTemplateConfig, GlobalExceptionHandler
├── controller/      AuthController, OrderController
├── dto/             RegisterRequest, LoginRequest, JwtResponse, OrderRequest, OrderResponse
├── entity/          User, Order
├── repository/      UserRepository, OrderRepository
├── security/        JwtUtil, JwtAuthFilter
└── service/         AuthService, OrderService
```

---

## 🛠️ Tech Stack

| Layer | Technology |
|-------|------------|
| Framework | Spring Boot 3.5, Java 21 |
| Security | Spring Security 6, jjwt 0.11.5 (HMAC-SHA256) |
| Database | PostgreSQL (Supabase Session Pooler) |
| Password Hashing | BCrypt (via Spring Security) |
| Inter-service | RestTemplate → InventorySync |
| Deployment | Render (Docker with mvnw wrapper) |

---

## ⚙️ Running Locally

**Prerequisites:** Java 17+, running InventorySync on port 8081

```bash
# Clone the repo
git clone https://github.com/comodo-18/order-service.git
cd order-service

# Create .env file
echo "DB_PASSWORD=your_supabase_password" > .env
echo "JWT_SECRET=404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970" >> .env

# Run the app (port 8083)
./mvnw spring-boot:run
```

App runs on `http://localhost:8083`

**Test the full flow:**

```bash
# Register
curl -X POST http://localhost:8083/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"demo","email":"demo@test.com","password":"password123"}'

# Login (copy the token from response)
curl -X POST http://localhost:8083/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"demo","password":"password123"}'

# Place an order (paste your token)
curl -X POST http://localhost:8083/api/orders \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <your-token>" \
  -d '{"productId":1,"quantity":1}'

# View order history
curl -H "Authorization: Bearer <your-token>" \
  http://localhost:8083/api/orders/my
```

---

## 🔧 Environment Variables (Production)

```
SPRING_PROFILES_ACTIVE=prod
DATABASE_URL=jdbc:postgresql://<supabase-pooler-host>:5432/postgres
DB_USER=<supabase-user>
DB_PASSWORD=<password>
JWT_SECRET=<256-bit-hex-encoded-secret>
INVENTORY_SERVICE_URL=https://inventory-sync-8u4t.onrender.com
```

---

## 🔗 Related Projects

- [**CatalogCache**](https://github.com/comodo-18/catalogue-cache) — Redis caching + Kafka producer for cache invalidation events
- [**InventorySync**](https://github.com/comodo-18/inventory-sync) — Redisson distributed locking for oversell prevention
- [**VariantGraph**](https://github.com/comodo-18/variant-graph) — Graph-based product variant management
- [**Varenya**](https://github.com/comodo-18/varenya) — Next.js 15 storefront frontend