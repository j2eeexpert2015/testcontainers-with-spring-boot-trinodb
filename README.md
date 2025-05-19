# Spring Boot, Trino, PostgreSQL & Testcontainers Integration Demo

This project demonstrates how to integrate a Spring Boot application with TrinoDB to query data residing in a PostgreSQL database. It showcases:

1. A local development environment using Docker Compose to run PostgreSQL, Trino, and SQLPad.
2. A Spring Boot application with a repository layer that uses Trino to fetch data.
3. Integration testing using Testcontainers to provide ephemeral PostgreSQL and Trino instances.

---

## Project Structure

The project follows a standard Maven layout. Key files and directories include:

- `docker-compose.yml`: Orchestrates PostgreSQL, Trino, and SQLPad containers for local development.
- `pom.xml`: Maven project configuration, dependencies, and build settings.
- `README.md`: This documentation file.
- `src/main/java/com/example/`: Main application source code:
  - `TrinoApplication.java`: Spring Boot main application class.
  - `controller/ProductController.java`: REST controller to expose product data.
  - `model/Product.java`: Domain model for products.
  - `repository/ProductRepository.java`: Repository class using JDBC to fetch data via Trino.
- `src/main/resources/`: Application resources:
  - `application.properties`: Spring Boot configuration for normal run.
  - `schema.sql`: SQL script for schema creation.
  - `data.sql`: SQL script for initial data population.
- `src/test/java/com/example/repository/`: Integration tests:
  - `ProductRepositoryIT.java`: Integration tests using Testcontainers.
- `trino/catalog/postgresql.properties`: Catalog config file for Trino to connect to PostgreSQL via Docker Compose.

---

## Prerequisites

- Java 17+
- Apache Maven 3.6+
- Docker Desktop

---

## Part 1: Local Development & Demonstration with Docker Compose

### 1.1 Configure Trino Catalog

Ensure `./trino/catalog/postgresql.properties` contains:

```properties
connector.name=postgresql
connection-url=jdbc:postgresql://postgres:5432/testdb
connection-user=admin
connection-password=admin
```

> `postgres` is the container service name used in `docker-compose.yml`.

---

### 1.2 Run Services with Docker Compose

From the project root directory:

```bash
docker-compose up -d
```

This starts:

- **PostgreSQL**: `localhost:5432` (`testdb`, user: `admin`, password: `admin`)
- **Trino**: Trino UI at `http://localhost:8080`
- **SQLPad**: SQL UI at `http://localhost:3000`

---

### 1.3 Verify Setup

#### Trino UI:
- Open `http://localhost:8080`
- Navigate to **Catalogs** → You should see `postgresql`.

#### SQLPad:
- Open `http://localhost:3000`
- Login (e.g., `admin@example.com / admin`)
- Create a new connection:
  - **Driver**: Trino (or PrestoSQL)
  - **Host**: `trino`
  - **Port**: `8080`
  - **Catalog**: `postgresql`
  - **Schema**: `public`

#### Sample SQL Query in SQLPad:
```sql
SELECT * FROM postgresql.public.products;
```

---

## Part 2: Running the Spring Boot Application

### 2.1 Application Configuration

`src/main/resources/application.properties`:

```properties
server.port=8081

spring.datasource.url=jdbc:postgresql://localhost:5432/testdb
spring.datasource.username=admin
spring.datasource.password=admin
spring.datasource.driver-class-name=org.postgresql.Driver
spring.sql.init.mode=always

app.trino.jdbc.url=jdbc:trino://localhost:8080
app.trino.jdbc.user=app_user
# app.trino.jdbc.password=  # Uncomment if needed
```

> Spring Boot will run on port 8081 to avoid conflict with Trino.

---

### 2.2 Initial Data Scripts

#### `src/main/resources/schema.sql`

```sql
DROP TABLE IF EXISTS products CASCADE;

CREATE TABLE products (
    id INT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    category VARCHAR(255),
    price DOUBLE PRECISION,
    stock_quantity INT
);
```

#### `src/main/resources/data.sql`

```sql
INSERT INTO products (id, name, category, price, stock_quantity) VALUES
(1, 'Laptop Pro', 'Electronics', 1200.50, 50),
(2, 'Coffee Mug', 'Kitchen', 15.75, 200),
(3, 'Gaming Mouse', 'Electronics', 75.00, 150),
(4, 'Desk Lamp', 'Office', 45.99, 75),
(5, 'Notebook Basic', 'Office', 5.25, 500);
```

---

### 2.3 Build & Run

Ensure Docker Compose services are running:

```bash
docker-compose up -d
```

Build the Spring Boot application:

```bash
mvn clean package
```

Run the JAR:

```bash
java -jar target/your-project-artifact-id-0.0.1-SNAPSHOT.jar
```

Or run from IDE → `TrinoApplication.java`.

Access the endpoint:

```text
http://localhost:8081/products
```

---

## Part 3: Integration Testing with Testcontainers

### 3.1 Testcontainers Usage

In `ProductRepositoryIT.java`:

- `@SpringBootTest`: Initializes Spring context.
- `PostgreSQLContainer`, `TrinoContainer`: Start Trino & PostgreSQL dynamically.
- `Network`: Shared network for Trino ↔ PostgreSQL communication.
- **Dynamic Catalog**: Trino’s `postgresql.properties` is generated and mounted at runtime.
- `@DynamicPropertySource`: Injects dynamic properties like Trino JDBC URL into the test context.
- `@BeforeEach setupData()`: Creates schema + inserts test data.
- `@TestPropertySource(properties = "spring.sql.init.mode=never")`: Prevents schema/data.sql execution during tests.
- **Awaitility**: Polls until catalog/table becomes available in Trino.

---

### 3.2 Running the Tests

From project root:

```bash
mvn test
```

Or run directly from IDE (e.g., IntelliJ or Eclipse).

Test lifecycle:

1. Start fresh PostgreSQL and Trino containers.
2. Create catalog file dynamically.
3. Inject Trino connection details into Spring context.
4. Set up isolated test data.
5. Execute repository queries.
6. Auto-cleanup after test class completes.

---

## Key Technologies

- **Spring Boot**: Java framework for building REST APIs.
- **Trino**: Distributed SQL engine for federated querying.
- **PostgreSQL**: Relational DB used as data source.
- **Testcontainers**: Lightweight Docker containers for integration testing.
- **Docker & Docker Compose**: Containerization and orchestration.
- **SQLPad**: Web-based SQL client.
- **Awaitility**: Wait-for-condition test library.
- **Maven**: Build and dependency management.

---

## Cleanup

To stop and remove all containers, networks, and volumes:

```bash
docker-compose down -v
```

> `-v` ensures volumes like `pgdata_compose` and `sqlpad_data_compose` are removed.  
> **Note**: Testcontainers handles its cleanup automatically.
