package ar.edu.utn.dds.k3003.eventos;

import java.util.Map;

/**
 * Sobre común de los eventos del modelo de lectura (contrato en donadores/docs/contrato-eventos-e6_v1.md).
 * El payload es siempre un snapshot completo de la entidad, nunca un delta.
 */
public record EventoDominio(
        String eventId,
        String tipo,
        String origen,
        String entidad,
        String entidadId,
        long version,
        String ocurridoEn,
        String correlationId,
        boolean eliminado,
        Map<String, Object> payload) {

    /** {origen}.{entidad}.{accion}; la acción es actualizada o eliminada. */
    public String routingKey() {
        return origen + "." + entidad + "." + (eliminado ? "eliminada" : "actualizada");
    }
}
