package com.example.trinodemo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;

@Configuration
public class TrinoConfig {

    @Value("${trino.url}")
    private String trinoUrl;

    @Value("${trino.user}")
    private String trinoUser;

    @Value("${trino.catalog}")
    private String trinoCatalog;

    @Value("${trino.schema}")
    private String trinoSchema;

    @Bean
    public DataSource trinoDataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("io.trino.jdbc.TrinoDriver");
        
        // Build connection URL with proper Trino JDBC format
        // Catalog and schema are part of the URL path, not query parameters
        String jdbcUrl = String.format("jdbc:trino://%s/%s/%s?user=%s", 
                trinoUrl, trinoCatalog, trinoSchema, trinoUser);
        
        dataSource.setUrl(jdbcUrl);
        return dataSource;
    }

    @Bean
    public JdbcTemplate trinoJdbcTemplate(DataSource trinoDataSource) {
        return new JdbcTemplate(trinoDataSource);
    }
}