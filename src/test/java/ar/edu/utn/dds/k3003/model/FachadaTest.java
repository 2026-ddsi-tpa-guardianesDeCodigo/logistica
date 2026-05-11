package ar.edu.utn.dds.k3003.model;

import ar.edu.utn.dds.k3003.Fachada;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.AsignacionDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.PaqueteDTO;
import ar.edu.utn.dds.k3003.catedra.fachadas.FachadaDonaciones;
import ar.edu.utn.dds.k3003.catedra.fachadas.FachadaDonadoresYEntidades;
import ar.edu.utn.dds.k3003.exceptions.AsignacionNoEncontrada;
import ar.edu.utn.dds.k3003.exceptions.DepositoYaExistenteException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
public class FachadaTest {

  private Fachada fachada;

  @Mock
  private FachadaDonadoresYEntidades fachadaDonadoresYEntidades;

  @Mock
  private FachadaDonaciones fachadaDonaciones;

  @BeforeEach
  void setUp() {
    fachada = new Fachada();
    fachada.setFachadaDonadoresYEntidades(fachadaDonadoresYEntidades);
    fachada.setFachadaDonaciones(fachadaDonaciones);
  }

  @Test
  void agregarAsignacionRetornaAsignacionConId() {
    AsignacionDTO asignacion = new AsignacionDTO("1", "paq1", "nec1", null, null);
    
    AsignacionDTO resultado = fachada.agregarAsignacion(asignacion);
    
    assertNotNull(resultado.id());
    assertEquals("paq1", resultado.paqueteID());
  }

  @Test
  void agregarAsignacionDuplicadaLanzaExcepcion() {
    AsignacionDTO asignacion = new AsignacionDTO("1", "paq1", "nec1", null, null);
    fachada.agregarAsignacion(asignacion);
    
    assertThrows(DepositoYaExistenteException.class, () -> {
      fachada.agregarAsignacion(asignacion);
    });
  }


  @Test
  void buscarAsignacionPorPaqueteIDNoEncontradaLanzaExcepcion() {
    assertThrows(AsignacionNoEncontrada.class, () -> {
      fachada.buscarAsignacionPorPaqueteID("inexistente");
    });
  }



  @Test
  void reportarEntregaSinAsignacionLanzaExcepcion() {
    PaqueteDTO paquete = new PaqueteDTO("paquete_inexistente", "donacion1", "producto1", 5);
    
    assertThrows(AsignacionNoEncontrada.class, () -> {
      fachada.reportarEntrega(paquete);
    });
  }

}
