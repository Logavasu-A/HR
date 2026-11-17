package app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

// The code lives in app/, modules/ and shared/, so Spring is told to scan all three.
@SpringBootApplication(scanBasePackages = { "app", "modules", "shared" })
@EntityScan(basePackages = { "modules", "shared" })
@EnableJpaRepositories(basePackages = { "modules", "shared" })
public class HrmsApplication {

    public static void main(String[] args) {
        SpringApplication.run(HrmsApplication.class, args);
    }
}
