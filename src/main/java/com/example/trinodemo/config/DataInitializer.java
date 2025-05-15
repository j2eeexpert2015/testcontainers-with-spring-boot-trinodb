package com.example.trinodemo.config;

import com.example.trinodemo.model.Product;
import com.example.trinodemo.service.ProductService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
public class DataInitializer {
    
    private static final Logger logger = LoggerFactory.getLogger(DataInitializer.class);
    
    @Bean
    @Profile("prod") // Only run in production
    public CommandLineRunner initDatabase(ProductService productService) {
        return args -> {
            logger.info("Initializing database with sample data");
            
            // Create table
            productService.initializeDatabase();
            
            // Check if we already have products
            if (productService.getAllProducts().isEmpty()) {
                logger.info("Adding sample products");
                productService.addProduct(new Product(1L, "Dell XPS 13", 1299.99, "Laptops"));
                productService.addProduct(new Product(2L, "MacBook Pro", 1799.99, "Laptops"));
                productService.addProduct(new Product(3L, "iPhone 13", 899.99, "Smartphones"));
                productService.addProduct(new Product(4L, "Samsung Galaxy S21", 799.99, "Smartphones"));
                productService.addProduct(new Product(5L, "Sony WH-1000XM4", 349.99, "Headphones"));
                logger.info("Sample data initialized successfully");
            } else {
                logger.info("Database already contains products, skipping initialization");
            }
        };
    }
}