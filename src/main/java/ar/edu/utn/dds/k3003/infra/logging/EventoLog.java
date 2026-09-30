package ar.edu.utn.dds.k3003.infra.logging;

public enum EventoLog {
    // --- transversales (idénticos en los 4 componentes) ---
    SERVICE_STARTED("service.started"),
    HTTP_REQUEST_COMPLETED("http.request.completed"),
    HTTP_CLIENT_COMPLETED("http.client.completed"),
    VALIDATION_FAILED("validation.failed"),
    UNHANDLED_EXCEPTION("unhandled.exception"),
    DB_OPERATION_FAILED("db.operation.failed"),

    // --- Logística + Worker (logging-spec_v1.md §5.4) ---
    DONACION_RECIBIDA("donacion.recibida"),
    DEPOSITO_CAPACIDAD_RECHAZADA("deposito.capacidad.rechazada"),
    MENSAJE_PUBLICADO("mensaje.publicado"),
    MENSAJE_CONSUMIDO("mensaje.consumido"),
    MENSAJE_REINTENTADO("mensaje.reintentado"),
    // Propio de este componente (no está en el catálogo de logging-spec_v1.md §5.4): el
    // matchmaking falló y la donación entró completa como stock en vez de perderse.
    MENSAJE_RECUPERADO_A_STOCK("mensaje.recuperado_a_stock"),
    MENSAJE_DESCARTADO("mensaje.descartado"),
    MATCHMAKING_DECIDIDO("matchmaking.decidido"),
    MATCHMAKING_SIN_NECESIDAD("matchmaking.sin_necesidad"),
    PAQUETE_CREADO("paquete.creado"),
    ASIGNACION_CREADA("asignacion.creada"),
    // Aviso a Donadores de que se comprometieron unidades por matchmaking (docs/coherencia-
    // necesidades_v1.md del repo de Donadores). outcome=degraded si Donadores no respondió:
    // la asignación ya quedó persistida, no se revierte por esto.
    NECESIDAD_COMPROMETIDA("necesidad.comprometida"),
    STOCK_SOBRANTE_PERSISTIDO("stock.sobrante.persistido"),
    STOCK_CONSULTADO("stock.consultado"),
    STOCK_CONSUMO_DIRECTO("stock.consumo.directo"),
    ENTREGA_REPORTADA("entrega.reportada"),
    WORKER_REPORTE_RECIBIDO("worker.reporte.recibido"),
    DEPOSITO_ALGORITMO_CAMBIADO("deposito.algoritmo.cambiado");

    private final String action;

    EventoLog(String action) {
        this.action = action;
    }

    public String action() {
        return action;
    }
}
