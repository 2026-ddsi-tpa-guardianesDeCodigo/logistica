package ar.edu.utn.dds.k3003.model;

import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.NecesidadMaterialDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.TipoNecesidadMaterialEnum;
import ar.edu.utn.dds.k3003.exceptions.AlgoritmoNoConfiguradoException;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DecisionDeAsignacionTest {

    private final Algoritmo subAtendidos = new PrioridadASubAtendidos();

    @Test
    void sinNecesidades_todoVaAStock() {
        DecisionDeAsignacion.Decision decision = DecisionDeAsignacion.decidir(subAtendidos, "prodX", 10, List.of());

        assertNull(decision.necesidadElegidaID());
        assertEquals(0, decision.cantidadAsignada());
        assertEquals(10, decision.sobrante());
    }

    @Test
    void necesidadExtraordinariaConSobrante_asignaYDejaSobrante() {
        List<NecesidadMaterialDTO> necesidades = List.of(new NecesidadMaterialDTO(
                "nec1", "entidad1", 5, "desc", 5, "prodY", TipoNecesidadMaterialEnum.EXTRAORDINARIA));

        DecisionDeAsignacion.Decision decision = DecisionDeAsignacion.decidir(subAtendidos, "prodY", 10, necesidades);

        assertEquals("nec1", decision.necesidadElegidaID());
        assertEquals(5, decision.cantidadAsignada());
        assertEquals(5, decision.sobrante());
    }

    @Test
    void necesidadExtraordinariaInsuficiente_asignaParcialSinSobrante() {
        List<NecesidadMaterialDTO> necesidades = List.of(new NecesidadMaterialDTO(
                "nec2", "entidad1", 5, "desc", 20, "prodZ", TipoNecesidadMaterialEnum.EXTRAORDINARIA));

        DecisionDeAsignacion.Decision decision = DecisionDeAsignacion.decidir(subAtendidos, "prodZ", 5, necesidades);

        assertEquals("nec2", decision.necesidadElegidaID());
        assertEquals(5, decision.cantidadAsignada());
        assertEquals(0, decision.sobrante());
    }

    @Test
    void necesidadRecurrenteInsuficienteSinAlternativa_todoVaAStock() {
        List<NecesidadMaterialDTO> necesidades = List.of(new NecesidadMaterialDTO(
                "nec3", "entidad1", 5, "desc", 50, "prodW", TipoNecesidadMaterialEnum.RECURRENTE));

        DecisionDeAsignacion.Decision decision = DecisionDeAsignacion.decidir(subAtendidos, "prodW", 10, necesidades);

        assertNull(decision.necesidadElegidaID());
        assertEquals(0, decision.cantidadAsignada());
        assertEquals(10, decision.sobrante());
    }

    @Test
    void necesidadRecurrenteInsuficienteConAlternativa_saltaYAsignaALaOtra() {
        List<NecesidadMaterialDTO> necesidades = List.of(
                new NecesidadMaterialDTO(
                        "necRecurrente", "entidad1", 5, "desc", 50, "prodV", TipoNecesidadMaterialEnum.RECURRENTE),
                new NecesidadMaterialDTO(
                        "necExtra", "entidad2", 5, "desc", 5, "prodV", TipoNecesidadMaterialEnum.EXTRAORDINARIA));

        DecisionDeAsignacion.Decision decision = DecisionDeAsignacion.decidir(subAtendidos, "prodV", 10, necesidades);

        assertEquals("necExtra", decision.necesidadElegidaID());
        assertEquals(5, decision.cantidadAsignada());
        assertEquals(5, decision.sobrante());
    }

    @Test
    void algoritmoNoConfigurado_tiraExcepcion() {
        List<NecesidadMaterialDTO> necesidades = List.of(new NecesidadMaterialDTO(
                "nec1", "entidad1", 5, "desc", 5, "prodY", TipoNecesidadMaterialEnum.EXTRAORDINARIA));

        assertThrows(
                AlgoritmoNoConfiguradoException.class,
                () -> DecisionDeAsignacion.decidir(null, "prodY", 10, necesidades));
    }
}
