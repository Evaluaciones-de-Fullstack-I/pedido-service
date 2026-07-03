package cl.duoc.pedidos.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced; // 👈 Importación clave para conectar con Eureka
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {
    
    @Bean
    @LoadBalanced // 👈 Habilita el mapeo dinámico de nombres en lugar de usar localhost
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }
}