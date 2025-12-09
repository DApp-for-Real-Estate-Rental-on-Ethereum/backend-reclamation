package ma.fstt.reclamationservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EntityScan(basePackages = {"ma.fstt.reclamationservice.domain.entity"})
@EnableJpaRepositories(basePackages = {"ma.fstt.reclamationservice.domain.repository"})
public class ReclamationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReclamationServiceApplication.class, args);
    }
}

