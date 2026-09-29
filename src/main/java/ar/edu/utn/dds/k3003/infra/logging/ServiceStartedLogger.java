package ar.edu.utn.dds.k3003.infra.logging;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Emite service.started (logging-spec_v1.md §5.1) cuando la app terminó de arrancar. */
@Component
public class ServiceStartedLogger {
    private static final EventLogger LOG = EventLogger.of(ServiceStartedLogger.class);

    @EventListener(ApplicationReadyEvent.class)
    public void alArrancar() {
        LOG.evento(EventoLog.SERVICE_STARTED, "Servicio iniciado").emitir();
    }
}
