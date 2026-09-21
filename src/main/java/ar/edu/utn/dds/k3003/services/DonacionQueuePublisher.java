package ar.edu.utn.dds.k3003.services;

import ar.edu.utn.dds.k3003.infra.logging.EventLogger;
import ar.edu.utn.dds.k3003.infra.logging.EventoLog;
import ar.edu.utn.dds.k3003.infra.logging.LogFields;
import ar.edu.utn.dds.k3003.model.DonacionMensajeDTO;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class DonacionQueuePublisher {

    private static final EventLogger LOG = EventLogger.of(DonacionQueuePublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final String nombreCola;

    public DonacionQueuePublisher(
            RabbitTemplate rabbitTemplate, @Value("${logistica.queue.donaciones}") String nombreCola) {
        this.rabbitTemplate = rabbitTemplate;
        this.nombreCola = nombreCola;
    }

    public void publicar(DonacionMensajeDTO mensaje) {
        // El correlation id viaja como header AMQP para que el listener (otro thread, sin request
        // HTTP) lo restaure en el MDC: sin esto la mitad asincrónica del flujo perdería la traza.
        String correlationId = MDC.get(LogFields.TRACE_ID);
        rabbitTemplate.convertAndSend(nombreCola, mensaje, m -> {
            if (correlationId != null) {
                m.getMessageProperties().setHeader(LogFields.MSG_CORRELATION_HEADER, correlationId);
            }
            return m;
        });

        LOG.evento(EventoLog.MENSAJE_PUBLICADO, "Mensaje publicado en la cola")
                .id(LogFields.DONACION, mensaje.donacionID())
                .dato(LogFields.MSG_SYSTEM, "rabbitmq")
                .dato(LogFields.MSG_DESTINATION, nombreCola)
                .dato(LogFields.MSG_OPERATION, "publish")
                .emitir();
    }
}
