package dsva;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import dsva.service.ComputationService;
import dsva.controller.NodeController;
import dsva.model.NodeInfo;
import java.util.Scanner;

@SpringBootApplication
@EnableScheduling
public class Application {

	@Bean
	public CommandLineRunner commandLineRunner(ComputationService computeService, NodeController nodeController) {
		return args -> {
			new Thread(() -> {
				Scanner scanner = new Scanner(System.in);
				System.out.println("CLI Ready. Commands: join <port>, start <amount>, leave, kill");
				while (scanner.hasNext()) {
					String cmd = scanner.next();
					try {
						if (cmd.equals("join")) {
							int port = scanner.nextInt();
							nodeController.join(new NodeInfo("localhost", port));
						} else if (cmd.equals("start")) {
							int amount = scanner.nextInt();
							computeService.initiateWork(amount);
						} else if (cmd.equals("leave")) {
							nodeController.leave();
						} else if (cmd.equals("kill")) {
							nodeController.kill();
						}  else if (cmd.equals("revive")) {
							nodeController.revive();
						}
					} catch (Exception e) {
						System.out.println("Error in CLI: " + e.getMessage());
					}
				}
			}).start();
		};
	}

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}

}
