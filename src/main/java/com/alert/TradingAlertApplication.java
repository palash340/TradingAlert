package com.alert;

import lombok.extern.log4j.Log4j2;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableJpaRepositories
@Log4j2
public class TradingAlertApplication {

	public static void main(String[] args) {
		SpringApplication.run(TradingAlertApplication.class, args);
	}

	@Bean
	public CommandLineRunner commandLineRunner(ApplicationContext applicationContext){
		return args -> {
			log.info("DATABASE: {}", applicationContext.getEnvironment().getProperty("spring.datasource.url") );
			log.info("USER: {}", applicationContext.getEnvironment().getProperty("spring.datasource.username") );
			log.info("Connection timeout: {}", applicationContext.getEnvironment().getProperty("spring.datasource.hikari.connection-timeout") );
			log.info("Pool name: {}", applicationContext.getEnvironment().getProperty("spring.datasource.hikari.poolName") );
			log.info("Idle time: {}", applicationContext.getEnvironment().getProperty("spring.datasource.hikari.minimum-idle") );
			log.info("Pool Max Size: {}", applicationContext.getEnvironment().getProperty("spring.datasource.hikari.maximum-pool-size") );
		};
	}
}
