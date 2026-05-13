
package com.paynest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PayNestApplication {
    public static void main(String[] args) {
        SpringApplication.run(PayNestApplication.class, args);
    }
}

