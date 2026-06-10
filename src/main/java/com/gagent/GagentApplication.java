package com.gagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class GagentApplication {

    public static void main(String[] args) {
        SpringApplication.run(GagentApplication.class, args);
    }

}
