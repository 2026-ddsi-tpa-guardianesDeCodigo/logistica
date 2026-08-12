package ar.edu.utn.dds.k3003.services;

import ar.edu.utn.dds.k3003.catedra.dtos.logistica.DepositoDTO;
import ar.edu.utn.dds.k3003.clients.DonacionesClient;
import ar.edu.utn.dds.k3003.clients.DonadoresYEntidadesClient;
import ar.edu.utn.dds.k3003.model.Deposito;
import ar.edu.utn.dds.k3003.model.DonacionMensajeDTO;
import ar.edu.utn.dds.k3003.model.Paquete;
import ar.edu.utn.dds.k3003.repositories.LogisticaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Parte B: gestionarDonacion ya no decide ni persiste la asignacion, solo verifica que haya
 * lugar en el deposito (contra lo que YA esta persistido) y encola la donacion.
 */
@SpringBootTest
class LogisticaServiceGestionarDonacionTest {

    @Autowired
    private LogisticaService logisticaService;

    @Autowired
    private LogisticaRepository logisticaRepository;

    @MockitoBean
    private DonadoresYEntidadesClient donadoresYEntidadesClient;

    @MockitoBean
    private DonacionesClient donacionesClient;

    @MockitoBean
    private DonacionQueuePublisher donacionQueuePublisher;

    private String depositoID;

    @BeforeEach
    void setUp() {
        logisticaRepository.limpiarAsignaciones();
        logisticaRepository.limpiarDepositos();
        DepositoDTO deposito = logisticaService.agregarDeposito(
                new DepositoDTO(null, null, "deposito-test", "direccion", 10, null));
        depositoID = deposito.id();
    }

    @Test
    void hayLugar_publicaElMensajeYNoTocaElStockTodavia() {
        logisticaService.gestionarDonacion(depositoID, "don1", "prodX", 5);

        verify(donacionQueuePublisher).publicar(new DonacionMensajeDTO(depositoID, "don1", "prodX", 5));
        DepositoDTO deposito = logisticaService.buscarDepositoPorID(depositoID);
        assertEquals(0, deposito.stockActual().size());
    }

    @Test
    void sinLugar_tiraExcepcionYNuncaPublica() {
        // simula que un worker ya proceso otra donacion y dejo stock persistido
        Deposito deposito = logisticaRepository.buscarDepositoPorID(depositoID).orElseThrow();
        deposito.agregarPaquete(new Paquete("donPrevia", "prodX", 8));
        logisticaRepository.guardarDeposito(deposito);

        assertThrows(
                RuntimeException.class, () -> logisticaService.gestionarDonacion(depositoID, "don2", "prodX", 5));

        verify(donacionQueuePublisher, never()).publicar(any());
    }

    @Test
    void depositoInexistente_tiraExcepcion() {
        assertThrows(
                RuntimeException.class,
                () -> logisticaService.gestionarDonacion("id-inexistente", "don1", "prodX", 5));
    }
}
