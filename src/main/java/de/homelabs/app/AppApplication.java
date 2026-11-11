package de.homelabs.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The homelab's micro-frontend app: a backend for frontend that signs people in, keeps
 * their tokens in a server-side session and serves the shell the micro frontends mount
 * into.
 */
@SpringBootApplication
public class AppApplication {

    public static void main(String[] args) {
        SpringApplication.run(AppApplication.class, args);
    }
}
