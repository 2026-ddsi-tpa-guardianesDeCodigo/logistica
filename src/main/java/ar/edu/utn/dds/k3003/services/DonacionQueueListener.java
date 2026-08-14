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
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
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

    private static final Logger log = LoggerFactory.getLogger(DonacionQueueListener.class);

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
    @RabbitListener(queues = "${logistica.queue.donaciones}")
    public void procesar(DonacionMensajeDTO mensaje) {
        try {
            Deposito deposito = logisticaRepository
                    .buscarDepositoPorID(mensaje.depositoID())
                    .orElseThrow(() -> new DepositoNoEncontradoException("No existe un deposito con ese ID"));

            List<NecesidadMaterialDTO> necesidades =
                    donadoresYEntidadesClient.obtenerNecesidadesInsatisfechasDe(mensaje.productoID());

            DecisionDeAsignacion.Decision decision = tiempoMatchmaking.record(() -> DecisionDeAsignacion.decidir(
                    deposito.getAlgoritmoObj(), mensaje.productoID(), mensaje.cantidad(), necesidades));

            logisticaService.persistirResultadoWorker(
                    mensaje.depositoID(),
                    mensaje.donacionID(),
                    mensaje.productoID(),
                    decision.necesidadElegidaID(),
                    decision.cantidadAsignada(),
                    decision.sobrante());
        } catch (Exception e) {
            log.error("Error procesando donacion {} de la cola: {}", mensaje.donacionID(), e.getMessage(), e);
        }
    }
}
