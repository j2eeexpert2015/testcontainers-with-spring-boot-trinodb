package com.example.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.TrinoContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

import com.example.model.Product;

@SpringBootTest
public class ProductRepositoryIT {

    private static final Logger logger = LoggerFactory.getLogger(ProductRepositoryIT.class);

    @Autowired
    private ProductRepository productRepository;

    static Network network = Network.newNetwork();

    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withNetwork(network)
            .withNetworkAliases("mypostgres")
            .withDatabaseName("testdb")
            .withUsername("testuser")
            .withPassword("testpass");

    static final int TRINO_INTERNAL_PORT = 8080;

    static TrinoContainer trino = new TrinoContainer("trinodb/trino:440")
            .withNetwork(network)
            .waitingFor(Wait.forHttp("/v1/info")
                    .forPort(TRINO_INTERNAL_PORT)
                    .forStatusCode(200)
                    .withStartupTimeout(Duration.ofSeconds(90)));


    static Path tempCatalogFile;
    static String trinoBaseJdbcUrl;
    static String trinoUser = "test_trino_user";

    @BeforeAll
    static void beforeAll() throws IOException {
        logger.info("Starting dependent services for integration tests...");
        postgres.start();

        String catalogPropertiesContent = String.format(
                "connector.name=postgresql\n" +
                        "connection-url=jdbc:postgresql://mypostgres:5432/%s\n" +
                        "connection-user=%s\n" +
                        "connection-password=%s\n",
                postgres.getDatabaseName(),
                postgres.getUsername(),
                postgres.getPassword()
        );
        Path tempCatalogDir = Files.createTempDirectory("trino_catalog_test_");
        tempCatalogFile = tempCatalogDir.resolve("postgresql.properties");
        Files.writeString(tempCatalogFile, catalogPropertiesContent, StandardCharsets.UTF_8);

        trino.withCopyFileToContainer(
                        MountableFile.forHostPath(tempCatalogFile.toAbsolutePath().toString()),
                        "/etc/trino/catalog/postgresql.properties"
                )
                .dependsOn(postgres);

        trino.start();

        trinoBaseJdbcUrl = String.format("jdbc:trino://%s:%d",
                trino.getHost(), trino.getMappedPort(TRINO_INTERNAL_PORT));

        waitForPostgresqlCatalogInTrino();
        logger.info("Dependent services started and configured.");
    }

    static void waitForPostgresqlCatalogInTrino() {
        logger.info("Waiting for Trino to recognize the 'postgresql' catalog...");
        Awaitility.await()
                .atMost(60, TimeUnit.SECONDS)
                .pollInterval(2, TimeUnit.SECONDS)
                .ignoreExceptionsInstanceOf(SQLException.class)
                .untilAsserted(() -> {
                    try (Connection conn = DriverManager.getConnection(trinoBaseJdbcUrl, trinoUser, null);
                         Statement stmt = conn.createStatement();
                         ResultSet rs = stmt.executeQuery("SHOW CATALOGS LIKE 'postgresql'")) {
                        assertThat(rs.next()).as("PostgreSQL catalog should be available in Trino").isTrue();
                        logger.info("'postgresql' catalog is now available in Trino.");
                    }
                });
    }

    @AfterAll
    static void afterAll() {
        logger.info("Stopping dependent services...");
        try { if (trino != null) trino.stop(); } catch (Exception e) { logger.warn("Error stopping Trino", e); }
        try { if (postgres != null) postgres.stop(); } catch (Exception e) { logger.warn("Error stopping PostgreSQL", e); }
        try { if (network != null) network.close(); } catch (Exception e) { logger.warn("Error closing Docker network", e); }
        try {
            if (tempCatalogFile != null) {
                Files.deleteIfExists(tempCatalogFile);
                Files.deleteIfExists(tempCatalogFile.getParent());
            }
        } catch (IOException e) { logger.warn("Error deleting temp catalog file", e); }
        logger.info("Dependent services stopped and cleaned up.");
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("app.trino.jdbc.url", () -> trinoBaseJdbcUrl);
        registry.add("app.trino.jdbc.user", () -> trinoUser);
        registry.add("app.trino.jdbc.password", () -> null);

        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @BeforeEach
    void setupData() throws SQLException {
        logger.debug("Populating PostgreSQL with test data for products table...");
        try (Connection pgConn = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement pgStmt = pgConn.createStatement()) {
            pgStmt.execute("DROP TABLE IF EXISTS products");
            pgStmt.execute("CREATE TABLE products (id INT PRIMARY KEY, name VARCHAR(255), category VARCHAR(255), price DOUBLE PRECISION, stock_quantity INT)");
            pgStmt.execute("INSERT INTO products (id, name, category, price, stock_quantity) VALUES (1, 'Laptop Pro', 'Electronics', 1200.50, 50)");
            pgStmt.execute("INSERT INTO products (id, name, category, price, stock_quantity) VALUES (2, 'Coffee Mug', 'Kitchen', 15.75, 200)");
            pgStmt.execute("INSERT INTO products (id, name, category, price, stock_quantity) VALUES (3, 'Gaming Mouse', 'Electronics', 75.00, 150)");
            pgStmt.execute("INSERT INTO products (id, name, category, price, stock_quantity) VALUES (4, 'Desk Lamp', 'Office', 45.99, 75)");
            pgStmt.execute("INSERT INTO products (id, name, category, price, stock_quantity) VALUES (5, 'Notebook Basic', 'Office', 5.25, 500)");
        }
        waitForTableVisibleInTrino("public", "products");
        logger.debug("Test data populated and table 'products' visible in Trino.");
    }

    void waitForTableVisibleInTrino(String schemaName, String tableName) {
        logger.info("Waiting for table 'postgresql.{}.{}' to become visible in Trino...", schemaName, tableName);
        String formattedQuery = String.format(Locale.ROOT, "SHOW TABLES FROM postgresql.%s LIKE '%s'",
                schemaName, tableName.toLowerCase(Locale.ROOT));
        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .ignoreExceptionsInstanceOf(SQLException.class)
                .untilAsserted(() -> {
                    try (Connection conn = DriverManager.getConnection(trinoBaseJdbcUrl, trinoUser, null);
                         Statement stmt = conn.createStatement();
                         ResultSet rs = stmt.executeQuery(formattedQuery)) {
                        assertThat(rs.next())
                                .as("Table 'postgresql.%s.%s' should be visible in Trino", schemaName, tableName)
                                .isTrue();
                        logger.info("Table 'postgresql.{}.{}' is now visible in Trino.", schemaName, tableName);
                    }
                });
    }

    @Test
    @DisplayName("Repository: Should fetch all products")
    void testRepositoryFetchAllProducts() {
        List<Product> products = productRepository.findAll();
        assertThat(products).hasSize(5)
                .extracting(Product::getName, Product::getCategory)
                .containsExactlyInAnyOrder(
                        tuple("Laptop Pro", "Electronics"),
                        tuple("Coffee Mug", "Kitchen"),
                        tuple("Gaming Mouse", "Electronics"),
                        tuple("Desk Lamp", "Office"),
                        tuple("Notebook Basic", "Office")
                );
    }

    @Test
    @DisplayName("Repository: Should fetch products by existing category")
    void testRepositoryFetchProductsByExistingCategory() {
        List<Product> electronicsProducts = productRepository.findByCategory("Electronics");
        assertThat(electronicsProducts).hasSize(2)
                .extracting(Product::getName)
                .containsExactlyInAnyOrder("Laptop Pro", "Gaming Mouse");
    }

    @Test
    @DisplayName("Repository: Should return empty list for a non-existent category")
    void testRepositoryFetchProductsByNonExistentCategory() {
        List<Product> books = productRepository.findByCategory("Books");
        assertThat(books).isEmpty();
    }

}