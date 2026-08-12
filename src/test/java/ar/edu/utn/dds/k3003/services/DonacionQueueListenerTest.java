package ar.edu.utn.dds.k3003.services;

import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.NecesidadMaterialDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.TipoNecesidadMaterialEnum;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.DepositoDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.TipoAlgoritmoEnum;
import ar.edu.utn.dds.k3003.clients.DonacionesClient;
import ar.edu.utn.dds.k3003.clients.DonadoresYEntidadesClient;
import ar.edu.utn.dds.k3003.model.DonacionMensajeDTO;
import ar.edu.utn.dds.k3003.repositories.LogisticaRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/**
 * El listener es solo un metodo publico normal (@RabbitListener no exige un broker vivo para
 * poder invocarlo directo), asi que se puede probar sin levantar RabbitMQ.
 */
@SpringBootTest
class DonacionQueueListenerTest {

    @Autowired
    private DonacionQueueListener listener;

    @Autowired
    private LogisticaService logisticaService;

    @Autowired
    private LogisticaRepository logisticaRepository;

    @MockitoBean
    private DonadoresYEntidadesClient donadoresYEntidadesClient;

    @MockitoBean
    private DonacionesClient donacionesClient;

    private String depositoID;

    @BeforeEach
    void setUp() {
        logisticaRepository.limpiarAsignaciones();
        logisticaRepository.limpiarDepositos();
        DepositoDTO deposito = logisticaService.agregarDeposito(
                new DepositoDTO(null, null, "deposito-test", "direccion", 100, null));
        depositoID = deposito.id();
        logisticaService.setAlgoritmoMM(depositoID, TipoAlgoritmoEnum.SUB_ATENDIDOS);
    }

    @Test
    void procesarMensaje_decideYPersisteLaAsignacion() {
        when(donadoresYEntidadesClient.obtenerNecesidadesInsatisfechasDe("prodY"))
                .thenReturn(List.of(new NecesidadMaterialDTO(
                        "nec1", "entidad1", 5, "desc", 5, "prodY", TipoNecesidadMaterialEnum.EXTRAORDINARIA)));

        listener.procesar(new DonacionMensajeDTO(depositoID, "don1", "prodY", 10));

        List<ar.edu.utn.dds.k3003.catedra.dtos.logistica.AsignacionDTO> asignaciones =
                logisticaService.obtenerTodasLasAsignaciones();
        assertEquals(1, asignaciones.size());
        assertEquals("nec1", asignaciones.get(0).necesidadID());

        DepositoDTO deposito = logisticaService.buscarDepositoPorID(depositoID);
        assertEquals(1, deposito.stockActual().size());
        assertEquals(5, deposito.stockActual().get(0).cantidad());
    }

    @Test
    void procesarMensaje_sinNecesidades_vaTodoAStock() {
        when(donadoresYEntidadesClient.obtenerNecesidadesInsatisfechasDe("prodZ")).thenReturn(List.of());

        listener.procesar(new DonacionMensajeDTO(depositoID, "don2", "prodZ", 10));

        assertEquals(0, logisticaService.obtenerTodasLasAsignaciones().size());
        DepositoDTO deposito = logisticaService.buscarDepositoPorID(depositoID);
        assertEquals(1, deposito.stockActual().size());
        assertEquals(10, deposito.stockActual().get(0).cantidad());
    }

    // Sin este try/catch en el listener, un mensaje "envenenado" queda reintentandose para
    // siempre porque Spring AMQP no hace ack cuando el @RabbitListener tira una excepcion.
    @Test
    void procesarMensaje_depositoInexistente_seLoguaYNoPropagaLaExcepcion() {
        assertDoesNotThrow(() ->
                listener.procesar(new DonacionMensajeDTO("id-inexistente", "don3", "prodX", 5)));
    }

    @Test
    void procesarMensaje_errorConsultandoNecesidades_seLoguaYNoPropagaLaExcepcion() {
        when(donadoresYEntidadesClient.obtenerNecesidadesInsatisfechasDe("prodError"))
                .thenThrow(new RuntimeException("Donadores y Entidades no responde"));

        assertDoesNotThrow(() ->
                listener.procesar(new DonacionMensajeDTO(depositoID, "don4", "prodError", 5)));

        assertEquals(0, logisticaService.obtenerTodasLasAsignaciones().size());
    }
}
