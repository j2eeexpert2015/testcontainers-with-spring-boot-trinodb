# Spring Boot, Trino, PostgreSQL & Testcontainers Integration Demo

This project demonstrates how to integrate a Spring Boot application with TrinoDB to query data residing in a PostgreSQL database. It showcases:
1.  A local development environment using Docker Compose to run PostgreSQL, Trino, and SQLPad.
2.  A Spring Boot application with a repository layer that uses Trino to fetch data.
3.  Integration testing using Testcontainers to provide ephemeral PostgreSQL and Trino instances.

## Project Structure

The project follows a standard Maven layout. Key files and directories include:

* `docker-compose.yml`: For local development, orchestrates PostgreSQL, Trino, and SQLPad containers.
* `pom.xml`: Maven project configuration, dependencies, and build settings.
* `README.md`: This documentation file.
* `src/main/java/com/example/`: Contains the main application source code.
    * `TrinoApplication.java`: The Spring Boot main application class.
    * `controller/ProductController.java`: REST controller to expose product data.
    * `model/Product.java`: The domain model for products.
    * `repository/ProductRepository.java`: The repository class that interacts with Trino using JDBC to fetch product data from PostgreSQL.
* `src/main/resources/`: Contains application resources.
    * `application.properties`: Configuration for the Spring Boot application for a "normal run" (e.g., database connections, Trino connection details, server port).
    * `schema.sql`: SQL script for database schema creation (e.g., `CREATE TABLE`). Automatically executed by Spring Boot on startup if `spring.sql.init.mode=always`.
    * `data.sql`: SQL script for initial data population (e.g., `INSERT` statements). Automatically executed by Spring Boot after `schema.sql` if `spring.sql.init.mode=always`.
* `src/test/java/com/example/repository/`: Contains integration tests.
    * `ProductRepositoryIT.java`: Integration tests for the `ProductRepository`, using Testcontainers to manage PostgreSQL and Trino instances dynamically.
* `trino/catalog/postgresql.properties`: Trino catalog configuration file specifically for the `docker-compose` setup, telling Trino how to connect to the PostgreSQL container managed by Docker Compose.

## Prerequisites

* Java 17+ (as specified in `pom.xml`)
* Apache Maven 3.6+
* Docker Desktop (or Docker Engine + Docker Compose CLI plugin)

## Part 1: Local Development & Demonstration with Docker Compose

This setup allows you to run PostgreSQL, Trino, and SQLPad locally using Docker Compose. This is useful for manual querying, understanding the Trino-PostgreSQL connection, and demonstrating the setup before running automated tests or the full application.

### 1.1 Configure Trino Catalog for Docker Compose

The Trino service in `docker-compose.yml` mounts a local directory (`./trino/catalog`) into the Trino container (`/etc/trino/catalog`). You need to create the catalog properties file that tells this Trino instance how to connect to the PostgreSQL instance (also managed by Docker Compose).

Create or ensure the file `./trino/catalog/postgresql.properties` has the following content:

```properties
# ./trino/catalog/postgresql.properties
connector.name=postgresql
# "postgres" is the service name of the PostgreSQL container in docker-compose.yml
connection-url=jdbc:postgresql://postgres:5432/testdb
# These credentials must match POSTGRES_USER and POSTGRES_PASSWORD in docker-compose.yml for the postgres service
connection-user=admin
connection-password=admin

1.2 Run Services with Docker Compose
From the project root directory, run:

Bash

docker-compose up -d
This will start:

PostgreSQL (postgres service): Accessible on your host machine at localhost:5432. The database name is testdb with user admin and password admin (as configured in docker-compose.yml). Data is persisted in a Docker named volume (pgdata_compose).
Trino (trino service): The Trino coordinator UI is accessible at http://localhost:8080. It uses the ./trino/catalog/postgresql.properties file to establish a connection to the PostgreSQL service.
SQLPad (sqlpad service): A web-based SQL editor accessible at http://localhost:3000.
1.3 Verify Setup (Docker Compose)
Access Trino UI: Open http://localhost:8080 in your web browser.
Allow a minute for Trino to initialize.
In the UI, navigate to the "Catalogs" section. You should see postgresql listed, indicating Trino has successfully loaded the catalog configuration.
Access SQLPad: Open http://localhost:3000.
Log in (e.g., admin@example.com / admin, as configured or per SQLPad defaults).
Add a New Connection in SQLPad to query Trino:
Connection Name: e.g., Trino on Docker Compose
Driver: Select Trino (or PrestoSQL if Trino is not listed directly).
Host / Server Address: trino (This is the service name of the Trino container, resolvable within the Docker Compose default network).
Port: 8080
User: any_user (The default Trino setup here isn't secured).
Password: (Leave blank).
Database / Catalog (optional, can specify in query): postgresql
Schema (optional, can specify in query): public
Test the connection and save it.
Query Data via SQLPad: Once connected, you can execute SQL queries against Trino. If the products table (created by the Spring Boot app's schema.sql and data.sql when it runs, or if you manually created it) exists in the public schema of the testdb database in PostgreSQL, you can query it through Trino:
SQL

-- Example query in SQLPad:
SELECT * FROM postgresql.public.products;
Part 2: Running the Spring Boot Application (Normal Run)
The Spring Boot application contains a ProductRepository that uses Trino to fetch data. For a "normal run" (e.g., local development against the services started by docker-compose), the application is configured via src/main/resources/application.properties.

2.1 Application Configuration (application.properties)
The src/main/resources/application.properties file configures the Spring Boot application, including how it connects to Trino and how it initializes the PostgreSQL database (the one managed by docker-compose in this scenario).

Properties

# src/main/resources/application.properties

# Spring Boot application server port
server.port=8081

# === DataSource Configuration for Spring Boot (for schema/data.sql execution) ===
# URL for the PostgreSQL database (points to the 'postgres' service from docker-compose.yml)
spring.datasource.url=jdbc:postgresql://localhost:5432/testdb
# Username for the PostgreSQL database (must match POSTGRES_USER in docker-compose.yml)
spring.datasource.username=admin
# Password for the PostgreSQL database (must match POSTGRES_PASSWORD in docker-compose.yml)
spring.datasource.password=admin
# JDBC Driver for PostgreSQL
spring.datasource.driver-class-name=org.postgresql.Driver

# === Spring JDBC Initialization ===
# Controls if schema.sql and data.sql scripts are run.
# 'always': Run scripts against any DataSource.
spring.sql.init.mode=always

# === Trino Connection for ProductRepository ===
# URL for the Trino coordinator (points to the 'trino' service from docker-compose.yml)
app.trino.jdbc.url=jdbc:trino://localhost:8080
# User for the application to connect to the Trino coordinator
app.trino.jdbc.user=app_user
# Password for 'app.trino.jdbc.user' (if Trino security requires it). Omit if Trino is unsecured.
# app.trino.jdbc.password=
server.port=8081: The Spring Boot application will run on this port to avoid conflicts (e.g., with Trino which also uses 8080).
spring.datasource.*: These properties configure Spring Boot's own DataSource. When spring.sql.init.mode=always, Spring Boot will use this DataSource to connect directly to the PostgreSQL container (running from docker-compose) and execute schema.sql and data.sql upon application startup.
app.trino.jdbc.*: These properties are used by the ProductRepository (via @Value injection) to configure its JDBC connection to the Trino instance (also running from docker-compose).
2.2 Initial Data (schema.sql & data.sql)
To ensure the PostgreSQL database (managed by docker-compose) has the necessary table structure and initial data when the Spring Boot application starts, create the following files:

src/main/resources/schema.sql: Contains Data Definition Language (DDL) statements.
SQL

DROP TABLE IF EXISTS products CASCADE;

CREATE TABLE products (
    id INT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    category VARCHAR(255),
    price DOUBLE PRECISION,
    stock_quantity INT
);
src/main/resources/data.sql: Contains Data Manipulation Language (DML) statements.
SQL

INSERT INTO products (id, name, category, price, stock_quantity) VALUES
(1, 'Laptop Pro', 'Electronics', 1200.50, 50),
(2, 'Coffee Mug', 'Kitchen', 15.75, 200),
(3, 'Gaming Mouse', 'Electronics', 75.00, 150),
(4, 'Desk Lamp', 'Office', 45.99, 75),
(5, 'Notebook Basic', 'Office', 5.25, 500);
When the Spring Boot application starts (and spring.sql.init.mode=always), it will execute these scripts against the PostgreSQL database specified by spring.datasource.url.

2.3 Build and Run the Application
Ensure Docker Compose services are running: If not already running, start them:

Bash

docker-compose up -d
Build the Spring Boot application using Maven:

Bash

mvn clean package
Run the application:
Execute the JAR file (replace your-project-artifact-id-0.0.1-SNAPSHOT.jar with the actual name of your JAR file found in the target/ directory, which is based on the <artifactId> in your pom.xml):

Bash

java -jar target/your-project-artifact-id-0.0.1-SNAPSHOT.jar
Alternatively, you can run the application directly from your IDE by executing the main method in TrinoApplication.java.

Access the application's endpoint:
The ProductController exposes an endpoint at /products. Open your web browser or use a tool like curl to access it:
http://localhost:8081/products
This request will trigger the ProductRepository to query data from PostgreSQL via the Trino instance running in Docker Compose.

Part 3: Running Integration Tests with Testcontainers
The ProductRepositoryIT.java class demonstrates how to use Testcontainers to perform integration testing. Testcontainers will dynamically start and manage ephemeral (temporary) instances of PostgreSQL and Trino in Docker containers specifically for the test run.

3.1 How Testcontainers is Used in ProductRepositoryIT.java
@SpringBootTest: Initializes a Spring Boot application context for the test.
PostgreSQLContainer: A Testcontainers object that manages a PostgreSQL Docker container.
TrinoContainer: A Testcontainers object that manages a Trino Docker container.
Network: A Testcontainers Network object is created to allow the Trino container to communicate with the PostgreSQL container using a defined network alias (e.g., mypostgres). This is crucial because Trino needs to connect to PostgreSQL.
Dynamic Catalog Configuration: Before the Trino container is started, its postgresql.properties catalog file is generated dynamically by the test. This generated file contains the correct JDBC URL (using the PostgreSQL container's network alias) and credentials for the Testcontainers-managed PostgreSQL instance. This file is then copied into the Trino container.
@DynamicPropertySource: This Spring Test annotation is used to add properties to the Spring Environment dynamically before the application context is fully built. In this test, it sets the app.trino.jdbc.url (and related user/password) to point to the Trino Testcontainer's dynamically mapped host and port. This ensures that the @Autowired ProductRepository bean used in the test connects to the correct Trino instance (the one managed by Testcontainers).
@BeforeEach setupData(): This JUnit 5 lifecycle method runs before each test method. It drops and recreates the products table and inserts a fresh set of test data directly into the Testcontainers-managed PostgreSQL instance. This ensures each test method runs against a known and isolated dataset.
@TestPropertySource(properties = "spring.sql.init.mode=never"): (Highly Recommended for ProductRepositoryIT.java) Add this class-level annotation to ProductRepositoryIT.java. This prevents the main schema.sql and data.sql (from src/main/resources) from being executed by Spring Boot during the test context initialization, giving the test's @BeforeEach setupData() method full control over the database state for the test.
Awaitility: A Java library used for more robust polling to wait for conditions to be met (e.g., waiting for Trino to recognize a catalog or table) rather than relying on fixed Thread.sleep() calls.
3.2 Run the Integration Tests
You can run the integration tests using Maven from the project root:

Bash

mvn test
Alternatively, you can run the ProductRepositoryIT.java class or individual test methods directly from your IDE (e.g., IntelliJ IDEA, Eclipse).

Important: Docker must be running on your machine for Testcontainers to work, as it needs to start and manage Docker containers.

The integration tests will:

Start fresh PostgreSQL and Trino containers before the test class runs.
Configure the Trino container to use the test PostgreSQL container as a data source.
Configure the Spring ProductRepository bean (within the test's application context) to connect to the test Trino container.
For each test method:
Set up clean data in the test PostgreSQL database (@BeforeEach).
Execute the test logic, which involves calls to the productRepository.
Shut down the PostgreSQL and Trino containers after all tests in the class have completed.
Key Technologies
Spring Boot: Framework for building the Java application.
Trino (TrinoDB): Distributed SQL query engine, used here to query data in PostgreSQL.
PostgreSQL: Relational database used as the underlying data store.
Testcontainers: Java library for providing lightweight, disposable instances of services (like databases and query engines) in Docker containers, specifically for automated integration testing.
Docker & Docker Compose: For containerization technology and orchestrating multi-container local development environments.
SQLPad: A web-based SQL editor that can connect to various data sources, including Trino.
Awaitility: A Java library used to test asynchronous systems, helpful for polling conditions until they are met.
Maven: Build automation tool used for managing project dependencies and the build lifecycle.
Cleanup (Docker Compose)
To stop and remove the containers, associated networks, and named volumes (pgdata_compose, sqlpad_data_compose) created by docker-compose up:

Bash

docker-compose down -v
The -v flag is important for removing the volumes, ensuring a clean state if you run docker-compose up again. Testcontainers manages its own containers and typically cleans them up automatically after tests.


Sources










Deep Research

Canvas

Video

