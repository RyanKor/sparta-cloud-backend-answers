package com.sparta.subscription_system;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SubscriptionSystemApplication {

	public static void main(String[] args) {
		SpringApplication.run(SubscriptionSystemApplication.class, args);
	}

}
