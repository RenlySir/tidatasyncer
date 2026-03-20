package com.example.sync.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.example.sync")
public class SyncAdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(SyncAdminApplication.class, args);
    }
}
