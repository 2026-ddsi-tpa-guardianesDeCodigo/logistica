package ar.edu.utn.dds.k3003.services;

import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.EstadoDonacionEnum;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.EstadoAsginacionEnum;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.OrigenAsignacionEnum;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.PaqueteDTO;
import ar.edu.utn.dds.k3003.clients.DonacionesClient;
import ar.edu.utn.dds.k3003.clients.DonadoresYEntidadesClient;
import ar.edu.utn.dds.k3003.exceptions.AsignacionNoEncontrada;
import ar.edu.utn.dds.k3003.exceptions.EntregaYaReportadaException;
import ar.edu.utn.dds.k3003.model.Asignacion;
import ar.edu.utn.dds.k3003.repositories.LogisticaRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Cubre el bug encontrado probando /entregas dos veces seguidas contra Render: reportar la
 * misma entrega otra vez terminaba en un 500 (satisfacerNecesidad se ejecutaba de nuevo sobre
 * una necesidad ya satisfecha, y cambiarEstadoDeDonacion fallaba con un 400 de Donaciones que
 * nadie capturaba). Ahora reportarEntrega corta antes si la asignacion ya esta COMPLETADA.
 */
@SpringBootTest
class LogisticaServiceReportarEntregaTest {

    @Autowired
    private LogisticaService logisticaService;

    @Autowired
    private LogisticaRepository logisticaRepository;

    @MockitoBean
    private DonadoresYEntidadesClient donadoresYEntidadesClient;

    @MockitoBean
    private DonacionesClient donacionesClient;

    private String paqueteID;

    @BeforeEach
    void setUp() {
        logisticaRepository.limpiarAsignaciones();
        logisticaRepository.limpiarDepositos();
        Asignacion asignacion = new Asignacion(
                "paqueteQA", "necQA", LocalDateTime.now(),
                EstadoAsginacionEnum.ASIGNADA, OrigenAsignacionEnum.MATCHMAKING);
        paqueteID = logisticaRepository.guardarAsignacion(asignacion).getPaqueteID();
    }

    @Test
    void primerReporte_completaLaAsignacionYNotificaADonacionesYDonadores() {
        logisticaService.reportarEntrega(new PaqueteDTO(paqueteID, "donQA", "prodQA", 5));

        verify(donadoresYEntidadesClient).satisfacerNecesidad("necQA", 5);
        verify(donacionesClient).cambiarEstadoDeDonacion("donQA", EstadoDonacionEnum.ACEPTADA);
        assertEquals(
                EstadoAsginacionEnum.COMPLETADA,
                logisticaService.buscarAsignacionPorPaqueteID(paqueteID).estado());
    }

    @Test
    void reportarLaMismaEntregaDosVeces_laSegundaNoTocaNadaYTiraExcepcionDeNegocio() {
        PaqueteDTO paqueteDTO = new PaqueteDTO(paqueteID, "donQA", "prodQA", 5);

        logisticaService.reportarEntrega(paqueteDTO);
        assertThrows(EntregaYaReportadaException.class, () -> logisticaService.reportarEntrega(paqueteDTO));

        // la segunda llamada no debe haber vuelto a notificar a nadie
        verify(donadoresYEntidadesClient, times(1)).satisfacerNecesidad("necQA", 5);
        verify(donacionesClient, times(1)).cambiarEstadoDeDonacion("donQA", EstadoDonacionEnum.ACEPTADA);
    }

    @Test
    void paqueteSinAsignacion_tiraAsignacionNoEncontrada() {
        assertThrows(
                AsignacionNoEncontrada.class,
                () -> logisticaService.reportarEntrega(new PaqueteDTO("paquete-inexistente", "donQA", "prodQA", 5)));
    }
}
