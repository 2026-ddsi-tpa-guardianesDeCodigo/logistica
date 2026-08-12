package ar.edu.utn.dds.k3003.services;

import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.NecesidadMaterialDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.TipoNecesidadMaterialEnum;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.DepositoDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.TipoAlgoritmoEnum;
import ar.edu.utn.dds.k3003.clients.DonacionesClient;
import ar.edu.utn.dds.k3003.clients.DonadoresYEntidadesClient;
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
class LogisticaServiceStockTest {

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
    void sinNecesidades_vaTodoAlStock() {
        when(donadoresYEntidadesClient.obtenerNecesidadesInsatisfechasDe("prodX"))
                .thenReturn(List.of());

        DepositoDTO resultado = logisticaService.gestionarDonacion(depositoID, "don1", "prodX", 10);

        assertEquals(1, resultado.stockActual().size());
        assertEquals(10, resultado.stockActual().get(0).cantidad());
        assertEquals(0, logisticaService.obtenerTodasLasAsignaciones().size());
    }

    @Test
    void necesidadExtraordinariaConSobrante_asignaYGuardaSobranteEnStock() {
        when(donadoresYEntidadesClient.obtenerNecesidadesInsatisfechasDe("prodY"))
                .thenReturn(List.of(new NecesidadMaterialDTO(
                        "nec1", "entidad1", 5, "desc", 5, "prodY", TipoNecesidadMaterialEnum.EXTRAORDINARIA)));

        DepositoDTO resultado = logisticaService.gestionarDonacion(depositoID, "don2", "prodY", 10);

        assertEquals(1, resultado.stockActual().size());
        assertEquals(5, resultado.stockActual().get(0).cantidad());
        assertEquals("don2", resultado.stockActual().get(0).donacionID());
        assertEquals(1, logisticaService.obtenerTodasLasAsignaciones().size());
        assertEquals("nec1", logisticaService.obtenerTodasLasAsignaciones().get(0).necesidadID());
    }

    @Test
    void necesidadExtraordinariaInsuficiente_asignaParcialSinSobrante() {
        when(donadoresYEntidadesClient.obtenerNecesidadesInsatisfechasDe("prodZ"))
                .thenReturn(List.of(new NecesidadMaterialDTO(
                        "nec2", "entidad1", 5, "desc", 20, "prodZ", TipoNecesidadMaterialEnum.EXTRAORDINARIA)));

        DepositoDTO resultado = logisticaService.gestionarDonacion(depositoID, "don3", "prodZ", 5);

        assertEquals(0, resultado.stockActual().size());
        assertEquals(1, logisticaService.obtenerTodasLasAsignaciones().size());
    }

    @Test
    void necesidadRecurrenteInsuficienteSinAlternativa_vaTodoAlStock() {
        when(donadoresYEntidadesClient.obtenerNecesidadesInsatisfechasDe("prodW"))
                .thenReturn(List.of(new NecesidadMaterialDTO(
                        "nec3", "entidad1", 5, "desc", 50, "prodW", TipoNecesidadMaterialEnum.RECURRENTE)));

        DepositoDTO resultado = logisticaService.gestionarDonacion(depositoID, "don4", "prodW", 10);

        assertEquals(1, resultado.stockActual().size());
        assertEquals(10, resultado.stockActual().get(0).cantidad());
        assertEquals(0, logisticaService.obtenerTodasLasAsignaciones().size());
    }

    @Test
    void necesidadRecurrenteInsuficienteConAlternativa_saltaYAsignaALaOtra() {
        when(donadoresYEntidadesClient.obtenerNecesidadesInsatisfechasDe("prodV"))
                .thenReturn(List.of(
                        new NecesidadMaterialDTO(
                                "necRecurrente", "entidad1", 5, "desc", 50, "prodV",
                                TipoNecesidadMaterialEnum.RECURRENTE),
                        new NecesidadMaterialDTO(
                                "necExtra", "entidad2", 5, "desc", 5, "prodV",
                                TipoNecesidadMaterialEnum.EXTRAORDINARIA)));

        DepositoDTO resultado = logisticaService.gestionarDonacion(depositoID, "don5", "prodV", 10);

        assertEquals(1, logisticaService.obtenerTodasLasAsignaciones().size());
        assertEquals("necExtra", logisticaService.obtenerTodasLasAsignaciones().get(0).necesidadID());
        assertEquals(1, resultado.stockActual().size());
        assertEquals(5, resultado.stockActual().get(0).cantidad());
    }

    @Test
    void dosPaquetesConMismoDonacionID_sePermiten() {
        when(donadoresYEntidadesClient.obtenerNecesidadesInsatisfechasDe("prodU"))
                .thenReturn(List.of(new NecesidadMaterialDTO(
                        "nec4", "entidad1", 5, "desc", 5, "prodU", TipoNecesidadMaterialEnum.EXTRAORDINARIA)));

        logisticaService.gestionarDonacion(depositoID, "donRepetida", "prodU", 10);

        DepositoDTO deposito = logisticaService.buscarDepositoPorID(depositoID);
        assertEquals(1, deposito.stockActual().size());
        assertEquals("donRepetida", deposito.stockActual().get(0).donacionID());
        assertEquals(1, logisticaService.obtenerTodasLasAsignaciones().size());
    }

    @Test
    void respetaCapacidadMaximaDelDeposito() {
        when(donadoresYEntidadesClient.obtenerNecesidadesInsatisfechasDe("lleno"))
                .thenReturn(List.of());
        for (int i = 0; i < 10; i++) {
            logisticaService.gestionarDonacion(depositoID, "d" + i, "lleno", 10);
        }

        assertThrows(RuntimeException.class,
                () -> logisticaService.gestionarDonacion(depositoID, "dExtra", "lleno", 1));
    }
}
