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
- Login (e.g., `admin / admin`)
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
### 3.2 How Testcontainers is Used in `ProductRepositoryIT.java`
* **SSL/TLS Handling**: For the scope of these integration tests, SSL/TLS encryption is **not enabled** for connections to the Testcontainers-managed PostgreSQL or Trino instances. The containers are started with default network configurations, and the JDBC URLs used for connections within the test environment do not specify SSL. This simplifies the test setup by focusing on application logic rather than secure transport, which is acceptable for ephemeral test instances running in a trusted local Docker environment. If testing SSL-specific configurations were a requirement, the Testcontainers setup would need to be explicitly augmented with SSL certificate management and JDBC SSL parameters.

* **Role of `static Network network = Network.newNetwork();` in Testcontainers**: 

In the `ProductRepositoryIT.java` integration test class, you will find the following line that declares and initializes a Testcontainers `Network` object:

```java
static Network network = Network.newNetwork();
```
Subsequently, both the PostgreSQLContainer (postgres) and TrinoContainer (trino) are configured to use this same network instance:
```java
static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
        .withNetwork(network) // Attaches the PostgreSQL container to the shared 'network'
        .withNetworkAliases("mypostgres") // Assigns a hostname "mypostgres" usable within this network
        // ... other PostgreSQL configurations ...

static TrinoContainer trino = new TrinoContainer("trinodb/trino:440")
        .withNetwork(network) // Attaches the Trino container to the SAME shared 'network'
        // ... other Trino configurations ...
```

**Why is this shared Network essential for the Testcontainers setup? :**

When Testcontainers starts services like PostgreSQL and Trino, each service runs inside its own isolated Docker container. For these distinct containers to communicate with each other—which is a core requirement here, as the Trino container must connect to and query the PostgreSQL container—they need to be part of the same virtual Docker network.

**Enabling Inter-Container Communication:**
The Network.newNetwork() call creates a new, dedicated Docker bridge network specifically for the duration of your test run. By attaching both the PostgreSQL and Trino containers to this same network object, you are effectively placing them on the same local area network (LAN) within the Docker environment. This allows them to see and reach each other.

**Service Discovery via Network Aliases (Hostnames):**
A critical feature of Docker networking is service discovery. Once containers are on the same network, they can find each other using their service names or, more explicitly in this Testcontainers setup, using network aliases.

The PostgreSQL container is given a network alias: **.withNetworkAliases("mypostgres")**.
This means that from within any other container on that same network (specifically, from the Trino container), the hostname mypostgres will resolve to the internal IP address of the PostgreSQL container.

---

### 3.3 Running the Tests

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

## 👤 About the Instructor

[![Ayan Dutta - Instructor](https://img-c.udemycdn.com/user/200_H/5007784_d6b8.jpg)](https://www.udemy.com/user/ayandutta/)

Hi, I’m **Ayan Dutta**, a Software Architect, Instructor, and Content Creator.  
I create practical, hands-on courses on **Java, Spring Boot, Debugging, Git, Python**, and more.

---

## 🌐 Connect With Me

- 💬 **Slack Group:** [Join Here](https://join.slack.com/t/learningfromexp/shared_invite/zt-1fnksxgd0-_jOdmIq2voEeMtoindhWrA)
- 📢 After joining, go to the `#integration-testing-with-testcontainers-java-spring-boot` channel
- 📧 **Email:** j2eeexpert2015@gmail.com
- 🔗 **YouTube:** [LearningFromExperience](https://www.youtube.com/@learningfromexperience)
- 📝 **Medium Blog:** [@mrayandutta](https://medium.com/@mrayandutta)
- 💼 **LinkedIn:** [Ayan Dutta](https://www.linkedin.com/in/ayan-dutta-a41091b/)

---

## 📺 Subscribe on YouTube

[![YouTube](https://img.shields.io/badge/Watch%20on%20YouTube-FF0000?style=for-the-badge&logo=youtube&logoColor=white)](https://www.youtube.com/@learningfromexperience)

---

## 📚 Explore My Udemy Courses

## 🧪 Java and Spring Boot Integration Testing

<table>
  <tr>
    <td>
      <a href="https://www.udemy.com/course/testcontainers-integration-testing-java-spring-boot">
        <img src="https://img-c.udemycdn.com/course/480x270/6525217_8d18_2.jpg" width="260">
        <br/><b>Integration Testing with Testcontainers: Java & Spring Boot</b>
      </a>
    </td>
  </tr>
</table>

---

## 🧩 Java Debugging Courses

<table>
  <tr>
    <td>
      <a href="https://www.udemy.com/course/eclipse-debugging-techniques-and-tricks">
        <img src="https://img-c.udemycdn.com/course/480x270/417118_3afa_4.jpg" width="260">
        <br/><b>Eclipse Debugging Techniques And Tricks</b>
      </a>
    </td>
    <td>
      <a href="https://www.udemy.com/course/java-debugging-with-intellij-idea">
        <img src="https://img-c.udemycdn.com/course/480x270/2608314_47e4.jpg" width="260">
        <br/><b>Java Debugging With IntelliJ IDEA</b>
      </a>
    </td>
    <td>
      <a href="https://www.udemy.com/course/java-debugging-with-visual-studio-code-the-ultimate-guide">
        <img src="https://img-c.udemycdn.com/course/480x270/5029852_d692_3.jpg" width="260">
        <br/><b>Java Debugging using VS Code</b>
      </a>
    </td>
  </tr>
</table>

---

## 💡 Java Productivity & Design Patterns

<table>
  <tr>
    <td>
      <a href="https://www.udemy.com/course/intellij-idea-tips-tricks-boost-your-java-productivity">
        <img src="https://img-c.udemycdn.com/course/480x270/6180669_7726.jpg" width="260">
        <br/><b>IntelliJ IDEA Tips & Tricks</b>
      </a>
    </td>
    <td>
      <a href="https://www.udemy.com/course/design-patterns-in-javacreational">
        <img src="https://img-c.udemycdn.com/course/480x270/779796_5770_2.jpg" width="260">
        <br/><b>Creational Design Patterns in Java</b>
      </a>
    </td>
  </tr>
</table>

---


## 🧪 Python Debugging Courses

<table>
  <tr>
    <td>
      <a href="https://www.udemy.com/course/learn-python-debugging-with-pycharm-ide">
        <img src="https://img-c.udemycdn.com/course/480x270/4840890_12a3_2.jpg" width="260">
        <br/><b>Python Debugging With PyCharm IDE</b>
      </a>
    </td>
    <td>
      <a href="https://www.udemy.com/course/python-debugging-with-visual-studio-code">
        <img src="https://img-c.udemycdn.com/course/480x270/5029842_d36f.jpg" width="260">
        <br/><b>Python Debugging with VS Code</b>
      </a>
    </td>
    <td>
      <a href="https://www.udemy.com/course/get-started-with-python-debugging-in-visual-studio-code">
        <img src="https://img-c.udemycdn.com/course/480x270/6412275_a17d.jpg" width="260">
        <br/><b>Python Debugging in VS Code (Free)</b>
      </a>
    </td>
  </tr>
</table>

---

## 🛠 Git & GitHub Courses

<table>
  <tr>
    <td>
      <a href="https://www.udemy.com/course/getting-started-with-github-desktop">
        <img src="https://img-c.udemycdn.com/course/480x270/6112307_3b4e_2.jpg" width="260">
        <br/><b>Complete GitHub Desktop Guide</b>
      </a>
    </td>
    <td>
      <a href="https://www.udemy.com/course/learn-to-use-git-and-github-with-eclipse-a-complete-guide">
        <img src="https://img-c.udemycdn.com/course/480x270/3369428_995b.jpg" width="260">
        <br/><b>Git & GitHub with Eclipse</b>
      </a>
    </td>
  </tr>
</table>

---

