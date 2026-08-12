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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

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
        when(donadoresYEntidadesClient.obtenerNecesidadesInsatisfechasDe("prodStock")).thenReturn(List.of());
    }

    @Test
    void consultarStockDisponible_sumaLosPaquetesEnStock() {
        logisticaService.gestionarDonacion(depositoID, "don1", "prodStock", 10);

        StockDisponibleDTO stock = logisticaService.consultarStockDisponible("prodStock");

        assertEquals("prodStock", stock.productoID());
        assertEquals(10, stock.cantidadDisponible());
    }

    @Test
    void stockSuficiente_consumeSoloLoNecesarioYDejaElResto() {
        logisticaService.gestionarDonacion(depositoID, "don1", "prodStock", 10);

        ConsumoStockResponseDTO respuesta = logisticaService.consumirStock("prodStock", "nec1", 6);

        assertEquals(6, respuesta.cantidadAsignada());
        assertEquals(1, respuesta.asignaciones().size());
        assertEquals(OrigenAsignacionEnum.SOLICITUD_DIRECTA, respuesta.asignaciones().get(0).origen());
        assertEquals("nec1", respuesta.asignaciones().get(0).necesidadID());
        assertEquals(4, logisticaService.consultarStockDisponible("prodStock").cantidadDisponible());
    }

    @Test
    void stockParcial_asignaSoloLoDisponible() {
        logisticaService.gestionarDonacion(depositoID, "don1", "prodStock", 5);

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
        logisticaService.gestionarDonacion(depositoID, "don1", "prodStock", 5);
        logisticaService.gestionarDonacion(depositoID, "don2", "prodStock", 5);

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
