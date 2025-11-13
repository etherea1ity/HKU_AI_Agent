package com.hku.hkuaiagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

@SpringBootApplication(exclude = {
        // Keep DataSource optional for local development; remove the exclusion when enabling PgVector
        DataSourceAutoConfiguration.class
})
public class HkuAiAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(HkuAiAgentApplication.class, args);
    }

}

