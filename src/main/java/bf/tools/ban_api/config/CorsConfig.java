package bf.tools.ban_api.config; // <- ATENÇÃO: troque para <seuPacoteBase>.config

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig {
  @Bean
  public WebMvcConfigurer corsConfigurer() {
    return new WebMvcConfigurer() {
      @Override
      public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
            .allowedOriginPatterns(
                "http://localhost:*",
                "http://127.0.0.1:*",
                "https://*.v0.dev",
                "https://*.lovable.dev",
                "https://*.onrender.com",
                "https://*.vercel.app"    
            )
            .allowedMethods("GET","POST","PUT","PATCH","DELETE","OPTIONS")
            .allowedHeaders("*")
            .allowCredentials(false)   // true se usar cookies
            .maxAge(3600);
      }
    };
  }
}