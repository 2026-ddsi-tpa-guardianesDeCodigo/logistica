package ar.edu.utn.dds.k3003.eventos;

import ar.edu.utn.dds.k3003.model.Asignacion;
import ar.edu.utn.dds.k3003.model.Deposito;
import ar.edu.utn.dds.k3003.model.Paquete;
import ar.edu.utn.dds.k3003.repositories.JpaPaquetesRepo;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/** Arma el sobre y el snapshot de cada entidad del catálogo de eventos de Logística. */
@Component
public class ConstructorDeEventos {
    static final String ORIGEN = "logistica";

    private final JpaPaquetesRepo paquetesRepo;
    // El stock no tiene @Version propia, así que su versión se genera acá: microsegundos desde la
    // época, forzados a crecer de a uno como mínimo. Sirve entre reinicios (el reloj sigue
    // avanzando) y entre eventos del mismo instante.
    private final AtomicLong ultimaVersionDeStock = new AtomicLong();

    public ConstructorDeEventos(JpaPaquetesRepo paquetesRepo) {
        this.paquetesRepo = paquetesRepo;
    }

    public Optional<EventoDominio> construir(Object entidad, boolean eliminada) {
        return switch (entidad) {
            case Deposito d -> Optional.of(deposito(d, eliminada));
            case Asignacion a -> Optional.of(asignacion(a, eliminada));
            default -> Optional.empty();
        };
    }

    private EventoDominio deposito(Deposito d, boolean eliminada) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", String.valueOf(d.getId()));
        payload.put("nombre", d.getNombre());
        payload.put("capacidad", d.getCapacidadMaxima());
        payload.put("algoritmo", d.getAlgoritmo() == null ? null : d.getAlgoritmo().name());
        return sobre("DepositoActualizado", "deposito", String.valueOf(d.getId()), d.getVersion(), eliminada, payload);
    }

    private EventoDominio asignacion(Asignacion a, boolean eliminada) {
        // El producto, la cantidad y la donación viven en el paquete asignado, no en la asignación.
        Optional<Paquete> paquete = buscarPaquete(a.getPaqueteID());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", String.valueOf(a.getId()));
        payload.put("paqueteId", a.getPaqueteID());
        payload.put("depositoId", a.getDepositoID());
        payload.put("donacionId", paquete.map(Paquete::getDonacionID).orElse(null));
        payload.put("necesidadId", a.getNecesidadID());
        payload.put("productoId", paquete.map(Paquete::getProducto).orElse(null));
        payload.put("cantidad", paquete.map(Paquete::getCantidad).orElse(null));
        payload.put("estado", a.getEstado() == null ? null : a.getEstado().name());
        payload.put("origen", a.getOrigen() == null ? null : a.getOrigen().name());
        payload.put("creadaEn", instante(a.getFecha()));
        payload.put("completadaEn", a.getCompletadaEn() == null ? null : a.getCompletadaEn().toString());
        return sobre("AsignacionActualizada", "asignacion", String.valueOf(a.getId()), a.getVersion(), eliminada, payload);
    }

    /**
     * Snapshot del stock de un producto en un depósito, sumado al momento de publicar. Es
     * synchronized para que el orden de las versiones coincida con el orden de las lecturas: dos
     * eventos del mismo par nunca se cruzan con la versión más nueva sobre el dato más viejo.
     */
    public synchronized EventoDominio stock(Long depositoId, String producto) {
        int cantidad = paquetesRepo.findByDeposito_Id(depositoId).stream()
                .filter(p -> producto != null && producto.equals(p.getProducto()))
                .mapToInt(p -> p.getCantidad() == null ? 0 : p.getCantidad())
                .sum();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("depositoId", String.valueOf(depositoId));
        payload.put("productoId", producto);
        payload.put("cantidad", cantidad);
        long ahora = Instant.now().toEpochMilli() * 1000;
        long version = ultimaVersionDeStock.updateAndGet(previa -> Math.max(previa + 1, ahora));
        return sobre("StockActualizado", "stock", depositoId + ":" + producto, version, false, payload);
    }

    // El matchmaking síncrono heredado (ejecutarMatchmaking) guarda como paqueteID lo que mande el
    // cliente, sin garantía de que exista ni sea numérico: sin paquete el snapshot sale sin esos datos.
    private Optional<Paquete> buscarPaquete(String paqueteID) {
        if (paqueteID == null) {
            return Optional.empty();
        }
        try {
            return paquetesRepo.findById(Long.parseLong(paqueteID));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static String instante(LocalDateTime fecha) {
        return fecha == null ? null : fecha.atZone(ZoneId.systemDefault()).toInstant().toString();
    }

    private EventoDominio sobre(String tipo, String entidad, String id, Long version, boolean eliminada,
                                Map<String, Object> payload) {
        // Un borrado no incrementa @Version (la fila deja de existir), pero el consumidor solo aplica
        // versiones mayores a la que tiene: se publica version + 1 para que la baja no sea ignorada.
        long v = (version == null ? 0L : version) + (eliminada ? 1 : 0);
        return new EventoDominio(UUID.randomUUID().toString(), tipo, ORIGEN, entidad, id, v,
                Instant.now().toString(), MDC.get("trace.id"), eliminada, payload);
    }
}
