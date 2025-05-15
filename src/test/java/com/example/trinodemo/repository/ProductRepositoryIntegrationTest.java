package com.example.trinodemo.repository;

import com.example.trinodemo.config.TrinoTestContainer;
import com.example.trinodemo.model.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TrinoTestContainer.class)
@ActiveProfiles("test")
public class ProductRepositoryIntegrationTest {
    private static final Logger logger = LoggerFactory.getLogger(ProductRepositoryIntegrationTest.class);

    @Autowired
    private ProductRepository productRepository;
    
    @Autowired
    private PostgreSQLContainer<?> postgresContainer;

    @BeforeEach
    public void setUp() {
        // Verify PostgreSQL container is running
        assertThat(postgresContainer.isRunning()).isTrue();
        
        logger.info("Setting up test data");
        // First create the table if it doesn't exist
        productRepository.createProductTable();
        
        // Clear any existing data
        logger.info("Clearing existing test data");
        productRepository.deleteAllProducts();
        
        // Add some test products
        logger.info("Inserting fresh test data");
        productRepository.saveProduct(new Product(1L, "Laptop", 1200.0, "Electronics"));
        productRepository.saveProduct(new Product(2L, "Smartphone", 800.0, "Electronics"));
        productRepository.saveProduct(new Product(3L, "Coffee Maker", 100.0, "Appliances"));
        logger.info("Test data setup complete");
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