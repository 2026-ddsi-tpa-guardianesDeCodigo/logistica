package ar.edu.utn.dds.k3003.infra.logging;

public final class LogFields {
    public static final String TRACE_ID = "trace.id";
    public static final String EVENT = "event.action";
    public static final String OUTCOME = "event.outcome";
    public static final String DURATION = "event.duration_ms";
    public static final String PEER = "peer.service";
    public static final String SCHEMA_VER = "donatrack.schema_version";

    public static final String DONACION = "donatrack.donacion_id";
    public static final String DEPOSITO = "donatrack.deposito_id";
    public static final String PRODUCTO = "donatrack.producto_id";
    public static final String NECESIDAD = "donatrack.necesidad_id";
    public static final String PAQUETE = "donatrack.paquete_id";
    public static final String ASIGNACION = "donatrack.asignacion_id";
    public static final String ALGORITMO = "donatrack.algoritmo";
    public static final String EST_ANT = "donatrack.estado_anterior";
    public static final String EST_NUE = "donatrack.estado_nuevo";
    public static final String MOTIVO = "motivo";
    public static final String CANTIDAD = "cantidad";

    public static final String MSG_SYSTEM = "messaging.system";
    public static final String MSG_DESTINATION = "messaging.destination";
    public static final String MSG_OPERATION = "messaging.operation";
    /** Header AMQP con el correlation id (logging-spec_v1.md §6, punto 4). */
    public static final String MSG_CORRELATION_HEADER = "x-correlation-id";

    private LogFields() {
    }
}
