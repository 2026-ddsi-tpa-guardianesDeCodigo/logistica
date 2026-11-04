package ar.edu.utn.dds.k3003.eventos;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * El MessageConverter JSON ya lo define config.RabbitConfig (un solo bean para todo el servicio);
 * acá solo se agrega el exchange de eventos.
 */
@Configuration
public class RabbitEventosConfig {

    // Se declara también del lado publicador (la declaración es idempotente) para poder publicar
    // aunque el Servicio de Consultas, dueño de la cola y su binding, todavía no haya arrancado.
    @Bean
    public TopicExchange exchangeEventos(@Value("${eventos.exchange:donatrack.eventos}") String nombre) {
        return new TopicExchange(nombre, true, false);
    }
}
