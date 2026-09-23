package dev.yetpk.retrace;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class RetraceApplication {

    public static void main(String[] args) {
        SpringApplication.run(RetraceApplication.class, args);
    }
}
