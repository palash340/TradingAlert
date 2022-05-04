package com.alert.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
@ComponentScan
public class AppConfig {

    @Bean
    public ExecutorService getExecutorService(){
        return Executors.newFixedThreadPool(5);
    }

}
