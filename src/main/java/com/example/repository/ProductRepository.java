package com.example.repository;

import com.example.model.Product;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement; 
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Repository
public class ProductRepository {

    private static final Logger logger = LoggerFactory.getLogger(ProductRepository.class);

    private final String jdbcUrl;
    private final String user;
    private final String password;

    public ProductRepository(
            @Value("${app.trino.jdbc.url}") String jdbcUrl,
            @Value("${app.trino.jdbc.user:test_trino_user}") String user,
            @Value("${app.trino.jdbc.password:#{null}}") String password) {
        this.jdbcUrl = jdbcUrl;
        this.user = user;
        this.password = password;
        logger.info("ProductRepository initialized with Trino URL: {}, User: {}", jdbcUrl, user);
    }

    public List<Product> findAll() {
        List<Product> result = new ArrayList<>();
        String query = "SELECT id, name, category, price, stock_quantity FROM postgresql.public.products";
        logger.debug("Executing Trino query: {} on URL: {}", query, jdbcUrl);

        try (Connection conn = DriverManager.getConnection(jdbcUrl, user, password);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {

            while (rs.next()) {
                Product p = new Product();
                p.setId(rs.getInt("id"));
                p.setName(rs.getString("name"));
                p.setCategory(rs.getString("category"));
                p.setPrice(rs.getDouble("price"));
                p.setStockQuantity(rs.getInt("stock_quantity"));
                result.add(p);
            }
            logger.debug("Query returned {} products.", result.size());

        } catch (SQLException e) {
            logger.error("Failed to retrieve products from Trino using URL: {}. Error: {}", jdbcUrl, e.getMessage(), e);
            throw new RuntimeException("Trino query failed: " + e.getMessage(), e);
        }
        return result;
    }

    public List<Product> findByCategory(String categoryName) {
        List<Product> result = new ArrayList<>();
        String query = "SELECT id, name, category, price, stock_quantity FROM postgresql.public.products WHERE category = ?";
        logger.debug("Executing Trino query: {} with category: {} on URL: {}", query, categoryName, jdbcUrl);

        try (Connection conn = DriverManager.getConnection(jdbcUrl, user, password);
             PreparedStatement pstmt = conn.prepareStatement(query)) {

            pstmt.setString(1, categoryName);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Product p = new Product();
                    p.setId(rs.getInt("id"));
                    p.setName(rs.getString("name"));
                    p.setCategory(rs.getString("category"));
                    p.setPrice(rs.getDouble("price"));
                    p.setStockQuantity(rs.getInt("stock_quantity"));
                    result.add(p);
                }
            }
            logger.debug("Query for category '{}' returned {} products.", categoryName, result.size());

        } catch (SQLException e) {
            logger.error("Failed to retrieve products by category '{}' from Trino. Error: {}", categoryName, e.getMessage(), e);
            throw new RuntimeException("Trino query failed for category: " + e.getMessage(), e);
        }
        return result;
    }
}