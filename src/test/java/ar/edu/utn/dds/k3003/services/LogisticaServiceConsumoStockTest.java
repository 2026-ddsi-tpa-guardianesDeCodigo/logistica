package ar.edu.utn.dds.k3003.services;

import ar.edu.utn.dds.k3003.catedra.dtos.logistica.DepositoDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.OrigenAsignacionEnum;
import ar.edu.utn.dds.k3003.clients.DonacionesClient;
import ar.edu.utn.dds.k3003.clients.DonadoresYEntidadesClient;
import ar.edu.utn.dds.k3003.model.ConsumoStockResponseDTO;
import ar.edu.utn.dds.k3003.model.StockDisponibleDTO;
import ar.edu.utn.dds.k3003.repositories.LogisticaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class LogisticaServiceConsumoStockTest {

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

    // Desde Parte B, gestionarDonacion solo encola: para simular stock ya existente en los
    // tests, hay que persistirlo directo via persistirResultadoWorker (lo que antes hacia
    // gestionarDonacion de forma sincronica cuando no habia necesidades).
    private void agregarAlStock(String donacionID, Integer cantidad) {
        logisticaService.persistirResultadoWorker(depositoID, donacionID, "prodStock", null, 0, cantidad);
    }

    @Test
    void consultarStockDisponible_sumaLosPaquetesEnStock() {
        agregarAlStock("don1", 10);

        StockDisponibleDTO stock = logisticaService.consultarStockDisponible("prodStock");

        assertEquals("prodStock", stock.productoID());
        assertEquals(10, stock.cantidadDisponible());
    }

    @Test
    void stockSuficiente_consumeSoloLoNecesarioYDejaElResto() {
        agregarAlStock("don1", 10);

        ConsumoStockResponseDTO respuesta = logisticaService.consumirStock("prodStock", "nec1", 6);

        assertEquals(6, respuesta.cantidadAsignada());
        assertEquals(1, respuesta.asignaciones().size());
        assertEquals(OrigenAsignacionEnum.SOLICITUD_DIRECTA, respuesta.asignaciones().get(0).origen());
        assertEquals("nec1", respuesta.asignaciones().get(0).necesidadID());
        assertEquals(4, logisticaService.consultarStockDisponible("prodStock").cantidadDisponible());
    }

    @Test
    void stockParcial_asignaSoloLoDisponible() {
        agregarAlStock("don1", 5);

        ConsumoStockResponseDTO respuesta = logisticaService.consumirStock("prodStock", "nec2", 10);

        assertEquals(5, respuesta.cantidadAsignada());
        assertEquals(1, respuesta.asignaciones().size());
        assertEquals(0, logisticaService.consultarStockDisponible("prodStock").cantidadDisponible());
    }

    @Test
    void sinStock_noEsError_devuelveCero() {
        ConsumoStockResponseDTO respuesta = logisticaService.consumirStock("prodStock", "nec3", 5);

        assertEquals(0, respuesta.cantidadAsignada());
        assertEquals(0, respuesta.asignaciones().size());
    }

    @Test
    void stockRepartidoEnVariosPaquetes_consumeFifoYCreaUnaAsignacionPorPaquete() {
        agregarAlStock("don1", 5);
        agregarAlStock("don2", 5);

        ConsumoStockResponseDTO respuesta = logisticaService.consumirStock("prodStock", "nec4", 8);

        assertEquals(8, respuesta.cantidadAsignada());
        assertEquals(2, respuesta.asignaciones().size());
        assertEquals(2, logisticaService.consultarStockDisponible("prodStock").cantidadDisponible());
    }

    @Test
    void cantidadInvalida_lanzaExcepcion() {
        assertThrows(RuntimeException.class, () -> logisticaService.consumirStock("prodStock", "nec5", 0));
    }
}
