package app.ageguessr;

import org.springframework.boot.SpringApplication;

public class TestAgeguessrApiApplication {

	public static void main(String[] args) {
		SpringApplication.from(AgeguessrApiApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
