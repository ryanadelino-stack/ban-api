package bf.tools.ban_api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = { "bf.tools" }) // <- chave do conserto
public class BanApiApplicationTests {
  public static void main(String[] args) {
    SpringApplication.run(BanApiApplication.class, args);
  }
}
