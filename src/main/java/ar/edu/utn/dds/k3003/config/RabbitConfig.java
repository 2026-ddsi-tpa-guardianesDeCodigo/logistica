package ar.edu.utn.dds.k3003.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    @Bean
    public Queue colaDonaciones(@Value("${logistica.queue.donaciones}") String nombreCola) {
        return new Queue(nombreCola, true);
    }

    // Un unico bean de MessageConverter: Spring Boot lo usa automaticamente tanto para
    // publicar (RabbitTemplate) como para recibir (@RabbitListener), asi los DTOs viajan
    // como JSON en vez del formato Java-serializado por defecto.
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
