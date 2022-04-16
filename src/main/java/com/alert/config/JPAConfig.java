package com.alert.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
public class JPAConfig {

    public JdbcTemplate getJDBCTemplate(@Autowired DataSource dataSource){
        return new JdbcTemplate(dataSource);
    }
}
