# EnerSight Backend

Backend service for EnerSight built with Spring Boot, PostgreSQL, and
Flyway.

---

## 🧰 Tech Stack

-   Java 17
-   Spring Boot
-   Spring Data JPA
-   Flyway
-   PostgreSQL (Docker)
-   Maven Wrapper

---

## 🚀 Running the Project

### 🟢 Option 1 --- Local

Start DB:

``` bash
docker compose -f docker/docker-compose.yaml up -d
```

Run app:

``` bash
./mvnw spring-boot:run
```

### 🟢 Option 2 --- Docker (Recommended)

Start DB:

``` bash
docker compose -f docker/docker-compose.yaml up -d
```

Run app:

``` bash
./mvnw spring-boot:run
```

---

### 🔄 Reset DB

``` bash
docker compose -f docker/docker-compose.yaml down -v
docker compose -f docker/docker-compose.yaml up -d
```

---

## 🧬 Database

Resource    | Value
----------- | ---------------
DB          | enersight_app
App User    | app_user
Flyway User | flyway_user

---

## 🧪 Tests

``` bash
./mvnw test
```

---

## 📦 Build

``` bash
./mvnw clean package
```

Run:

``` bash
java -jar target/*.jar
```

---

## ⚠️ Common Issues

-   init.sql not executed → check volume path
-   role does not exist → reset volume
-   port conflict → change 5432

---

<center>EnerSight -- CoderHood -- 2026 </center>
