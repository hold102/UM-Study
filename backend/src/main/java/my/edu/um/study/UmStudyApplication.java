package my.edu.um.study;

import my.edu.um.study.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class UmStudyApplication {
    public static void main(String[] args) {
        SpringApplication.run(UmStudyApplication.class, args);
    }
}
