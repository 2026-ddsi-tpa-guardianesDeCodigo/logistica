package ar.edu.utn.dds.k3003.services;

import ar.edu.utn.dds.k3003.catedra.dtos.logistica.AsignacionDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.DepositoDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.PaqueteDTO;
import ar.edu.utn.dds.k3003.clients.DonacionesClient;
import ar.edu.utn.dds.k3003.clients.DonadoresYEntidadesClient;
import ar.edu.utn.dds.k3003.repositories.LogisticaRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ABM (E5, A8) faltante de Logística: editar depósito, stock por depósito y listado de entregas.
 */
@SpringBootTest
class LogisticaServiceAbmTest {

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
                new DepositoDTO(null, null, "deposito-test", "direccion vieja", 100, null));
        depositoID = deposito.id();
    }

    // ---------------------------------------------------------------- editarDeposito

    @Test
    @DisplayName("Editar depósito: solo toca los campos presentes")
    void editarDeposito_soloCamposPresentes() {
        DepositoDTO resultado = logisticaService.editarDeposito(depositoID,
                new DepositoDTO(null, null, "deposito-nuevo", null, null, null));

        assertEquals("deposito-nuevo", resultado.nombre());
        assertEquals("direccion vieja", resultado.direccion(), "no se tocó, no vino en el body");
        assertEquals(100, resultado.capacidadMaxima());
    }

    @Test
    @DisplayName("La capacidad no puede bajar del stock que ya está ocupando el depósito")
    void editarDeposito_capacidadMenorAlStockOcupado_rechazada() {
        logisticaService.persistirResultadoWorker(depositoID, "don1", "prodX", null, 0, 60);

        assertThrows(IllegalArgumentException.class, () -> logisticaService.editarDeposito(depositoID,
                new DepositoDTO(null, null, null, null, 50, null)));
    }

    @Test
    @DisplayName("Editar un depósito inexistente")
    void editarDeposito_inexistente() {
        assertThrows(RuntimeException.class, () -> logisticaService.editarDeposito("id-inexistente",
                new DepositoDTO(null, null, "x", null, null, null)));
    }

    // ---------------------------------------------------------------- consultarStockDeDeposito

    @Test
    @DisplayName("Stock de un depósito puntual, distinto del GET /stock global por producto")
    void consultarStockDeDeposito_soloElDeEsteDeposito() {
        DepositoDTO otroDeposito = logisticaService.agregarDeposito(
                new DepositoDTO(null, null, "otro-deposito", "otra direccion", 100, null));

        logisticaService.persistirResultadoWorker(depositoID, "don1", "prodX", null, 0, 10);
        logisticaService.persistirResultadoWorker(otroDeposito.id(), "don2", "prodX", null, 0, 20);

        List<PaqueteDTO> stock = logisticaService.consultarStockDeDeposito(depositoID);

        assertEquals(1, stock.size());
        assertEquals(10, stock.get(0).cantidad());
    }

    @Test
    @DisplayName("Stock de un depósito inexistente")
    void consultarStockDeDeposito_inexistente() {
        assertThrows(RuntimeException.class, () -> logisticaService.consultarStockDeDeposito("id-inexistente"));
    }

    // ---------------------------------------------------------------- listarEntregas

    @Test
    @DisplayName("Solo las asignaciones COMPLETADA cuentan como entregas")
    void listarEntregas_soloCompletadas() {
        logisticaService.persistirResultadoWorker(depositoID, "don1", "prodX", "nec1", 5, 0);
        AsignacionDTO asignacion = logisticaService.obtenerTodasLasAsignaciones().get(0);

        assertTrue(logisticaService.listarEntregas().isEmpty(), "todavía está ASIGNADA, no es una entrega");

        logisticaRepository.actualizarEstadoAsignacion(asignacion.id(),
                ar.edu.utn.dds.k3003.catedra.dtos.logistica.EstadoAsginacionEnum.COMPLETADA);

        List<AsignacionDTO> entregas = logisticaService.listarEntregas();
        assertEquals(1, entregas.size());
        assertEquals(asignacion.id(), entregas.get(0).id());
    }
}
