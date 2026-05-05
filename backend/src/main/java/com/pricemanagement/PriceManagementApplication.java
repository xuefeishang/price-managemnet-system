
package com.pricemanagement;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class PriceManagementApplication {

    public static void main(String[] args) {
        SpringApplication.run(PriceManagementApplication.class, args);
        System.out.println("Price Management System Backend Started Successfully!");
    }
}
