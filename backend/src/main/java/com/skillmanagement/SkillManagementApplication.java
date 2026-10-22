package com.skillmanagement;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SkillManagementApplication {

    public static void main(String[] args) {
        SpringApplication.run(SkillManagementApplication.class, args);
    }
}