package com.example.trinodemo.repository;

import com.example.trinodemo.model.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.TrinoContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
public class ProductRepositoryIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:14-alpine")
            .withDatabaseName("testdb")
            .withUsername("postgres")
            .withPassword("postgres");

    @Container
    static TrinoContainer trino = new TrinoContainer(DockerImageName.parse("trinodb/trino:352"));
    //Using smaller image
    //static TrinoContainer trino = new TrinoContainer(DockerImageName.parse("trinodb/trino:352-alpine"));

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) throws UnsupportedOperationException, IOException, InterruptedException {
        // Set up the PostgreSQL catalog properties for Trino
        trino.start();
        
        // Create the catalog properties file
        Map<String, String> catalogProperties = new HashMap<>();
        catalogProperties.put("connector.name", "postgresql");
        catalogProperties.put("connection-url", postgres.getJdbcUrl());
        catalogProperties.put("connection-user", postgres.getUsername());
        catalogProperties.put("connection-password", postgres.getPassword());
        
        // Use exec to create the catalog directory and properties file
        trino.execInContainer("mkdir", "-p", "/etc/trino/catalog");
        
        StringBuilder propertiesContent = new StringBuilder();
        for (Map.Entry<String, String> entry : catalogProperties.entrySet()) {
            propertiesContent.append(entry.getKey()).append("=").append(entry.getValue()).append("\n");
        }
        
        try {
            trino.execInContainer(
                "sh", "-c", 
                "echo '" + propertiesContent.toString() + "' > /etc/trino/catalog/postgresql.properties"
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to create catalog properties", e);
        }
        
        // Register the Trino connection properties
        registry.add("trino.url", () -> trino.getHost() + ":" + trino.getMappedPort(8080));
        registry.add("trino.user", () -> "test");
        registry.add("trino.catalog", () -> "postgresql");
        registry.add("trino.schema", () -> "public");
    }
    
    @Autowired
    private ProductRepository productRepository;
    
    @BeforeEach
    public void setUp() {
        // Setup test data
        productRepository.createProductTable();
        productRepository.deleteAllProducts();
        
        productRepository.saveProduct(new Product(1L, "Laptop", 1200.0, "Electronics"));
        productRepository.saveProduct(new Product(2L, "Smartphone", 800.0, "Electronics"));
        productRepository.saveProduct(new Product(3L, "Coffee Maker", 100.0, "Appliances"));
    }
    
    @Test
    public void shouldFindAllProducts() {
        List<Product> products = productRepository.findAllProducts();
        
        assertThat(products).isNotNull();
        assertThat(products).hasSize(3);
    }
    
    @Test
    public void shouldFindProductsByCategory() {
        List<Product> electronicsProducts = productRepository.findProductsByCategory("Electronics");
        
        assertThat(electronicsProducts).isNotNull();
        assertThat(electronicsProducts).hasSize(2);
        assertThat(electronicsProducts.get(0).getCategory()).isEqualTo("Electronics");
        assertThat(electronicsProducts.get(1).getCategory()).isEqualTo("Electronics");
    }

    @Test
    public void shouldFindProductById() {
        Product product = productRepository.findProductById(1L);
        
        assertThat(product).isNotNull();
        assertThat(product.getId()).isEqualTo(1L);
        assertThat(product.getName()).isEqualTo("Laptop");
    }
}