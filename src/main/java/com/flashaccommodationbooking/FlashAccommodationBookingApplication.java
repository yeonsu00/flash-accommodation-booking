package com.flashaccommodationbooking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@EnableJpaAuditing
@SpringBootApplication
public class FlashAccommodationBookingApplication {

    public static void main(String[] args) {
        SpringApplication.run(FlashAccommodationBookingApplication.class, args);
    }

}
