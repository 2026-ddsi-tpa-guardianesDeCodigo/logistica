package ar.edu.utn.dds.k3003.services;

import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.NecesidadMaterialDTO;
import ar.edu.utn.dds.k3003.clients.DonadoresYEntidadesClient;
import ar.edu.utn.dds.k3003.exceptions.DepositoNoEncontradoException;
import ar.edu.utn.dds.k3003.model.DecisionDeAsignacion;
import ar.edu.utn.dds.k3003.model.Deposito;
import ar.edu.utn.dds.k3003.model.DonacionMensajeDTO;
import ar.edu.utn.dds.k3003.repositories.LogisticaRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import ar.edu.utn.dds.k3003.infra.logging.EventLogger;
import ar.edu.utn.dds.k3003.infra.logging.EventoLog;
import ar.edu.utn.dds.k3003.infra.logging.LogFields;
import ar.edu.utn.dds.k3003.infra.logging.Outcome;
import java.util.List;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * Worker embebido en la misma instancia que atiende pedidos web (TIP 2 de la consigna).
 * Al recibir un mensaje, consulta las necesidades, calcula la asignacion con la misma
 * DecisionDeAsignacion que usaria un Worker externo, y persiste el resultado.
 * A diferencia de un Worker standalone, esta corriendo en el mismo proceso que Logistica,
 * asi que no necesita HTTP para leer el algoritmo del deposito ni para persistir: usa
 * el repositorio y el service directamente.
 */
@Component
public class DonacionQueueListener {

    private static final EventLogger LOG = EventLogger.of(DonacionQueueListener.class);

    private final LogisticaRepository logisticaRepository;
    private final DonadoresYEntidadesClient donadoresYEntidadesClient;
    private final LogisticaService logisticaService;
    private final Timer tiempoMatchmaking;

    public DonacionQueueListener(
            LogisticaRepository logisticaRepository,
            DonadoresYEntidadesClient donadoresYEntidadesClient,
            LogisticaService logisticaService,
            MeterRegistry meterRegistry) {
        this.logisticaRepository = logisticaRepository;
        this.donadoresYEntidadesClient = donadoresYEntidadesClient;
        this.logisticaService = logisticaService;
        // Mismo nombre+tag que si se registrara en otro lado: Micrometer no duplica el
        // meter, devuelve la instancia ya registrada. Este es el unico lugar del proceso
        // principal donde realmente corre el algoritmo de matchmaking (DecisionDeAsignacion),
        // a diferencia de persistirResultadoWorker, que solo persiste una decision ya tomada.
        this.tiempoMatchmaking = Timer.builder("logistica.matchmaking.tiempo")
                .description("Tiempo de ejecución del matchmaking")
                .tag("componente", "logistica")
                .register(meterRegistry);
    }

    // Si esto tira sin capturar, Spring AMQP no hace ack y reintenta el mensaje indefinidamente
    // (poison message) - se loguea y se descarta en su lugar, igual que hace WorkerApp con sus
    // propios errores.
    /** Sin correlation id (se genera uno). Mantiene la firma original para quien lo invoque directo. */
    public void procesar(DonacionMensajeDTO mensaje) {
        procesar(mensaje, null);
    }

    @RabbitListener(queues = "${logistica.queue.donaciones}")
    public void procesar(
            DonacionMensajeDTO mensaje,
            @Header(name = LogFields.MSG_CORRELATION_HEADER, required = false) String correlationId) {
        // Este thread no atiende un request HTTP: se restaura el correlation id que viajó en el
        // header del mensaje (o se genera uno si el publicador no lo mandó).
        MDC.put(LogFields.TRACE_ID, correlationId != null ? correlationId : UUID.randomUUID().toString());
        try {
            LOG.evento(EventoLog.MENSAJE_CONSUMIDO, "Mensaje consumido de la cola")
                    .id(LogFields.DONACION, mensaje.donacionID())
                    .dato(LogFields.MSG_SYSTEM, "rabbitmq")
                    .dato(LogFields.MSG_OPERATION, "receive")
                    .dato("tipo_worker", "embebido")
                    .emitir();

            Deposito deposito = logisticaRepository
                    .buscarDepositoPorID(mensaje.depositoID())
                    .orElseThrow(() -> new DepositoNoEncontradoException("No existe un deposito con ese ID"));

            List<NecesidadMaterialDTO> necesidades =
                    donadoresYEntidadesClient.obtenerNecesidadesInsatisfechasDe(mensaje.productoID());

            DecisionDeAsignacion.Decision decision = tiempoMatchmaking.record(() -> DecisionDeAsignacion.decidir(
                    deposito.getAlgoritmoObj(), mensaje.productoID(), mensaje.cantidad(), necesidades));

            if (decision.necesidadElegidaID() == null) {
                LOG.evento(EventoLog.MATCHMAKING_SIN_NECESIDAD, "Sin necesidad para la donación: va a stock")
                        .id(LogFields.PRODUCTO, mensaje.productoID())
                        .id(LogFields.DONACION, mensaje.donacionID())
                        .emitir();
            } else {
                LOG.evento(EventoLog.MATCHMAKING_DECIDIDO, "Matchmaking decidido")
                        .dato(LogFields.ALGORITMO, deposito.getAlgoritmo())
                        .id(LogFields.NECESIDAD, decision.necesidadElegidaID())
                        .id(LogFields.DONACION, mensaje.donacionID())
                        .dato(LogFields.CANTIDAD, decision.cantidadAsignada())
                        .dato("parcial", LogisticaService.esParcial(necesidades, decision))
                        .emitir();
            }

            logisticaService.persistirResultadoWorker(
                    mensaje.depositoID(),
                    mensaje.donacionID(),
                    mensaje.productoID(),
                    decision.necesidadElegidaID(),
                    decision.cantidadAsignada(),
                    decision.sobrante());
        } catch (Exception e) {
            LOG.evento(EventoLog.MENSAJE_DESCARTADO, "Mensaje descartado: falló su procesamiento")
                    .id(LogFields.DONACION, mensaje.donacionID())
                    .dato(LogFields.MSG_SYSTEM, "rabbitmq")
                    .dato("intentos", 1)
                    .error(e).emitir();
        } finally {
            MDC.remove(LogFields.TRACE_ID);
        }
    }
}
