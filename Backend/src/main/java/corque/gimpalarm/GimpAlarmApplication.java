package corque.gimpalarm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@EnableJpaAuditing
@SpringBootApplication
public class GimpAlarmApplication {

    public static void main(String[] args) {
        SpringApplication.run(GimpAlarmApplication.class, args);
    }

}
