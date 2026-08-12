package ar.edu.utn.dds.k3003.services;

import ar.edu.utn.dds.k3003.catedra.dtos.logistica.DepositoDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.OrigenAsignacionEnum;
import ar.edu.utn.dds.k3003.clients.DonacionesClient;
import ar.edu.utn.dds.k3003.clients.DonadoresYEntidadesClient;
import ar.edu.utn.dds.k3003.repositories.LogisticaRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Parte B: esto es lo que antes hacia la mitad "persistir" de asignarOGuardarEnStock, ahora
 * separado para que lo puedan llamar tanto el worker embebido (DonacionQueueListener) como
 * el endpoint que atiende al worker standalone.
 */
@SpringBootTest
class LogisticaServicePersistirResultadoWorkerTest {

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
    }

    @Test
    void cantidadAsignada_creaPaqueteYAsignacionMatchmaking() {
        DepositoDTO resultado =
                logisticaService.persistirResultadoWorker(depositoID, "don1", "prodX", "nec1", 5, 0);

        assertEquals(0, resultado.stockActual().size());
        List<ar.edu.utn.dds.k3003.catedra.dtos.logistica.AsignacionDTO> asignaciones =
                logisticaService.obtenerTodasLasAsignaciones();
        assertEquals(1, asignaciones.size());
        assertEquals("nec1", asignaciones.get(0).necesidadID());
        assertEquals(OrigenAsignacionEnum.MATCHMAKING, asignaciones.get(0).origen());
    }

    @Test
    void sobrante_vaAlStock() {
        DepositoDTO resultado =
                logisticaService.persistirResultadoWorker(depositoID, "don1", "prodX", null, 0, 7);

        assertEquals(1, resultado.stockActual().size());
        assertEquals(7, resultado.stockActual().get(0).cantidad());
        assertEquals(0, logisticaService.obtenerTodasLasAsignaciones().size());
    }

    @Test
    void cantidadAsignadaYSobrante_creaAmbos() {
        DepositoDTO resultado =
                logisticaService.persistirResultadoWorker(depositoID, "don1", "prodX", "nec1", 5, 3);

        assertEquals(1, resultado.stockActual().size());
        assertEquals(3, resultado.stockActual().get(0).cantidad());
        assertEquals(1, logisticaService.obtenerTodasLasAsignaciones().size());
    }

    @Test
    void respetaCapacidadDelDepositoAlGuardarSobrante() {
        DepositoDTO chico =
                logisticaService.agregarDeposito(new DepositoDTO(null, null, "chico", "dir", 5, null));

        logisticaService.persistirResultadoWorker(chico.id(), "don1", "prodX", null, 0, 5);

        assertThrows(
                RuntimeException.class,
                () -> logisticaService.persistirResultadoWorker(chico.id(), "don2", "prodX", null, 0, 1));
    }

    @Test
    void depositoInexistente_tiraExcepcion() {
        assertThrows(
                RuntimeException.class,
                () -> logisticaService.persistirResultadoWorker("id-inexistente", "don1", "prodX", null, 0, 5));
    }
}
