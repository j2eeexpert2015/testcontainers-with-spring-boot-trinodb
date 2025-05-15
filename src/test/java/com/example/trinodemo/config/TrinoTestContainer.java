package com.example.trinodemo.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;

@TestConfiguration
public class TrinoTestContainer {
    private static final Logger logger = LoggerFactory.getLogger(TrinoTestContainer.class);

    private static final int TRINO_PORT = 8080;
    private static final String TRINO_IMAGE = "trinodb/trino:latest";
    private static final String POSTGRES_IMAGE = "postgres:14-alpine";
    private static final Network NETWORK = Network.newNetwork();

    @Bean(initMethod = "start", destroyMethod = "stop")
    public PostgreSQLContainer<?> postgresContainer() {
        logger.info("Starting PostgreSQL container");
        return new PostgreSQLContainer<>(DockerImageName.parse(POSTGRES_IMAGE))
                .withNetwork(NETWORK)
                .withNetworkAliases("postgres")
                .withDatabaseName("testdb")
                .withUsername("postgres")
                .withPassword("postgres");
    }

    @Bean(initMethod = "start", destroyMethod = "stop")
    public GenericContainer<?> trinoContainer(PostgreSQLContainer<?> postgresContainer) {
        logger.info("Starting Trino container with PostgreSQL connector");
        // Create catalog properties file for PostgreSQL
        try {
            Path tempDir = Files.createTempDirectory("trino-catalogs");
            Path catalogDir = Files.createDirectory(Paths.get(tempDir.toString(), "catalog"));
            Path postgresProperties = Files.createFile(Paths.get(catalogDir.toString(), "postgresql.properties"));
            
            String properties = 
                "connector.name=postgresql\n" +
                "connection-url=jdbc:postgresql://postgres:5432/testdb\n" +
                "connection-user=postgres\n" +
                "connection-password=postgres";
            
            Files.writeString(postgresProperties, properties);
            logger.debug("Created PostgreSQL catalog properties file at: {}", postgresProperties);
            
            return new GenericContainer<>(DockerImageName.parse(TRINO_IMAGE))
                    .withNetwork(NETWORK)
                    .withExposedPorts(TRINO_PORT)
                    .withCopyFileToContainer(
                        MountableFile.forHostPath(postgresProperties),
                        "/etc/trino/catalog/postgresql.properties"
                    )
                    // Add wait strategy for Trino to be ready
                    .waitingFor(Wait.forLogMessage(".*SERVER STARTED.*\\n", 1))
                    .withStartupTimeout(Duration.ofMinutes(2));
        } catch (Exception e) {
            logger.error("Failed to create Trino catalog properties", e);
            throw new RuntimeException("Failed to create Trino catalog properties", e);
        }
    }

    @Bean
    @Primary
    public DataSource trinoTestDataSource(GenericContainer<?> trinoContainer) {
        logger.info("Configuring Trino test datasource");
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("io.trino.jdbc.TrinoDriver");

        // Note: Trino JDBC doesn't accept 'schema' as a connection parameter
        // It should be passed as part of the URL path instead
        String jdbcUrl = String.format("jdbc:trino://%s:%d/postgresql/public?user=test",
                trinoContainer.getHost(),
                trinoContainer.getMappedPort(TRINO_PORT));

        logger.info("Trino test JDBC URL: {}", jdbcUrl);
        dataSource.setUrl(jdbcUrl);
        
        // Verify Trino is ready by making a simple query
        waitForTrinoToBeReady(dataSource);
        
        return dataSource;
    }
    
    private void waitForTrinoToBeReady(DataSource dataSource) {
        logger.info("Waiting for Trino server to be fully initialized...");
        int maxRetries = 10;
        int retryIntervalMs = 5000;
        int retry = 0;
        
        while (retry < maxRetries) {
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT 1")) {
                
                if (rs.next()) {
                    logger.info("Trino server is ready!");
                    return;
                }
            } catch (SQLException e) {
                logger.warn("Trino not ready yet (attempt {}/{}): {}", 
                            retry + 1, maxRetries, e.getMessage());
                try {
                    Thread.sleep(retryIntervalMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted while waiting for Trino", ie);
                }
                retry++;
            }
        }
        
        throw new RuntimeException("Trino server failed to initialize after " + maxRetries + " attempts");
    }

    @Bean
    @Primary
    public JdbcTemplate trinoTestJdbcTemplate(DataSource trinoTestDataSource) {
        logger.info("Creating JdbcTemplate with Trino test DataSource");
        return new JdbcTemplate(trinoTestDataSource);
    }
}