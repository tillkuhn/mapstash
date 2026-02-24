package com.mapstash;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class MapStashApplication {

  static void main(String[] args) {
    SpringApplication.run(MapStashApplication.class, args);
  }
}
