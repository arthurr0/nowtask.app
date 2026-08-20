package app.nowtask;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "app.nowtask")
public class NowtaskApplication {
    public static void main(String[] args) {
        SpringApplication.run(NowtaskApplication.class, args);
    }
}
