// src/main/java/com/example/trinodemo/repository/ProductRepository.java
package com.example.trinodemo.repository;

import com.example.trinodemo.model.Product;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class ProductRepository {
    private static final Logger logger = LoggerFactory.getLogger(ProductRepository.class);

    private final JdbcTemplate jdbcTemplate;
    
    private final RowMapper<Product> productRowMapper = (rs, rowNum) -> new Product(
            rs.getLong("id"),
            rs.getString("name"),
            rs.getDouble("price"),
            rs.getString("category")
    );

    public ProductRepository(JdbcTemplate trinoJdbcTemplate) {
        this.jdbcTemplate = trinoJdbcTemplate;
    }

    public void createProductTable() {
        logger.debug("Creating products table if it doesn't exist");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS products (" +
                "id BIGINT, " +
                "name VARCHAR, " +
                "price DOUBLE, " +
                "category VARCHAR)");
        logger.info("Products table created or already exists");
    }

    public void deleteAllProducts() {
        logger.debug("Deleting all products");
        int count = jdbcTemplate.update("DELETE FROM products");
        logger.info("Deleted {} products", count);
    }

    public void saveProduct(Product product) {
        logger.debug("Saving product: {}", product);
        jdbcTemplate.update(
                "INSERT INTO products (id, name, price, category) VALUES (?, ?, ?, ?)",
                product.getId(), product.getName(), product.getPrice(), product.getCategory()
        );
        logger.debug("Product saved successfully");
    }

    public List<Product> findAllProducts() {
        logger.debug("Finding all products");
        List<Product> products = jdbcTemplate.query("SELECT * FROM products", productRowMapper);
        logger.debug("Found {} products", products.size());
        return products;
    }

    public Product findProductById(Long id) {
        logger.debug("Finding product with id: {}", id);
        Product product = jdbcTemplate.queryForObject(
                "SELECT * FROM products WHERE id = ?",
                productRowMapper,
                id
        );
        logger.debug("Found product: {}", product);
        return product;
    }

    public List<Product> findProductsByCategory(String category) {
        logger.debug("Finding products with category: {}", category);
        List<Product> products = jdbcTemplate.query(
                "SELECT * FROM products WHERE category = ?",
                productRowMapper,
                category
        );
        logger.debug("Found {} products with category {}", products.size(), category);
        return products;
    }
    
    public void deleteProduct(Long id) {
        logger.debug("Deleting product with id: {}", id);
        int rowsAffected = jdbcTemplate.update("DELETE FROM products WHERE id = ?", id);
        logger.debug("Deleted product with id {}, rows affected: {}", id, rowsAffected);
    }
}