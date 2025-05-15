package com.example.trinodemo.service;

import com.example.trinodemo.model.Product;
import com.example.trinodemo.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProductService {

    private static final Logger logger = LoggerFactory.getLogger(ProductService.class);
    
    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public void initializeDatabase() {
        logger.info("Initializing database schema");
        productRepository.createProductTable();
    }

    public void addProduct(Product product) {
        logger.info("Adding product: {}", product);
        productRepository.saveProduct(product);
    }

    public List<Product> getAllProducts() {
        logger.info("Retrieving all products");
        return productRepository.findAllProducts();
    }

    public Product getProductById(Long id) {
        logger.info("Retrieving product with id: {}", id);
        return productRepository.findProductById(id);
    }

    public List<Product> getProductsByCategory(String category) {
        logger.info("Retrieving products with category: {}", category);
        return productRepository.findProductsByCategory(category);
    }
    
    public void deleteProduct(Long id) {
        logger.info("Deleting product with id: {}", id);
        productRepository.deleteProduct(id);
    }
}