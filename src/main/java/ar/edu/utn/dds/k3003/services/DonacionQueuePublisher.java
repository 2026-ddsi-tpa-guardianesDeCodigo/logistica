package ar.edu.utn.dds.k3003.services;

import ar.edu.utn.dds.k3003.model.DonacionMensajeDTO;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class DonacionQueuePublisher {

    private final RabbitTemplate rabbitTemplate;
    private final String nombreCola;

    public DonacionQueuePublisher(
            RabbitTemplate rabbitTemplate, @Value("${logistica.queue.donaciones}") String nombreCola) {
        this.rabbitTemplate = rabbitTemplate;
        this.nombreCola = nombreCola;
    }

    public void publicar(DonacionMensajeDTO mensaje) {
        rabbitTemplate.convertAndSend(nombreCola, mensaje);
    }
}
