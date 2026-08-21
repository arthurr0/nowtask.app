package app.nowtask;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication(scanBasePackages = "app.nowtask")
public class NowtaskApplication {
    public static void main(String[] args) {
        SpringApplication.run(NowtaskApplication.class, args);
    }
}
