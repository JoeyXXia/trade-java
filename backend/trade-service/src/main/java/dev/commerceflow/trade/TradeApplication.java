package dev.commerceflow.trade;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

// The database profile explicitly creates its DataSource; default mode is health-only.
@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
public class TradeApplication {
    public static void main(String[] args) {
        SpringApplication.run(TradeApplication.class, args);
    }
}
