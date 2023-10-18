package groupB.newbankV5.customercare;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAspectJAutoProxy
@EnableScheduling
public class CustomerCareApplication {

	public static void main(String[] args) {
		SpringApplication.run(CustomerCareApplication.class, args);
	}

}
