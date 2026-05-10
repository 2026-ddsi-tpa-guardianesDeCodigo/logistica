package ar.edu.utn.dds.k3003.model;

import ar.edu.utn.dds.k3003.Fachada;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.AsignacionDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.DepositoDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.NecesidadDeEntidadDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.PaqueteDTO;
import ar.edu.utn.dds.k3003.catedra.fachadas.FachadaDonaciones;
import ar.edu.utn.dds.k3003.catedra.fachadas.FachadaDonadoresYEntidades;
import ar.edu.utn.dds.k3003.exceptions.AsignacionNoEncontrada;
import ar.edu.utn.dds.k3003.exceptions.DepositoYaExistenteException;
import ar.edu.utn.dds.k3003.exceptions.NoHayNecesidades;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

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
  void buscarAsignacionPorPaqueteIDRetornaAsignacion() {
    NecesidadDeEntidadDTO necesidad = new NecesidadDeEntidadDTO(null, "entidad1", 5, "desc1", 10, "producto1");
    PaqueteDTO paquete = new PaqueteDTO("paquete1", "donacion1", "producto1", 5);
    
    AsignacionDTO asignacion = fachada.ejecutarMatchmaking(paquete, List.of(necesidad));
    
    AsignacionDTO encontrada = fachada.buscarAsignacionPorPaqueteID(paquete.id());
    
    assertEquals(asignacion.paqueteID(), encontrada.paqueteID());
  }

  @Test
  void buscarAsignacionPorPaqueteIDNoEncontradaLanzaExcepcion() {
    assertThrows(AsignacionNoEncontrada.class, () -> {
      fachada.buscarAsignacionPorPaqueteID("inexistente");
    });
  }

  @Test
  void setAlgoritmoMMAsignaAlgoritmo() {
    fachada.setAlgoritmoMM();
    
    DepositoDTO deposito = new DepositoDTO(null, "deposito1", "dir1", 1000, null);
    DepositoDTO depositoResultado = fachada.agregarDeposito(deposito);
    
    assertNotNull(depositoResultado.id());
  }

  @Test
  void agregarDepositoRetornaDepositoConPropiedades() {
    DepositoDTO deposito = new DepositoDTO(null, "almacen_norte", "calle principal 123", 5000, null);
    
    DepositoDTO resultado = fachada.agregarDeposito(deposito);
    
    assertNotNull(resultado.id());
    assertEquals("almacen_norte", resultado.nombre());
    assertEquals("calle principal 123", resultado.direccion());
    assertEquals(5000, resultado.capacidadMaxima());
  }

  @Test
  void agregarMultiplesDepositosIndependientes() {
    DepositoDTO deposito1 = new DepositoDTO(null, "almacen_norte", "calle principal 123", 5000, null);
    DepositoDTO deposito2 = new DepositoDTO(null, "almacen_sur", "avenida 456", 3000, null);
    
    DepositoDTO resultado1 = fachada.agregarDeposito(deposito1);
    DepositoDTO resultado2 = fachada.agregarDeposito(deposito2);
    
    assertNotEquals(resultado1.id(), resultado2.id());
    assertEquals("almacen_norte", fachada.buscarDepositoPorID(resultado1.id()).nombre());
    assertEquals("almacen_sur", fachada.buscarDepositoPorID(resultado2.id()).nombre());
  }

  @Test
  void agregarDepositoConCapacidadGrandePreservaValor() {
    DepositoDTO deposito = new DepositoDTO(null, "almacen_principal", "centro importante", 50000, null);
    
    DepositoDTO resultado = fachada.agregarDeposito(deposito);
    
    assertEquals(50000, resultado.capacidadMaxima());
    DepositoDTO recuperado = fachada.buscarDepositoPorID(resultado.id());
    assertEquals(50000, recuperado.capacidadMaxima());
  }

  @Test
  void gestionarDonacionSinNecesidadesLanzaExcepcion() {
    DepositoDTO deposito = new DepositoDTO(null, "deposito1", "dir1", 1000, null);
    DepositoDTO guardado = fachada.agregarDeposito(deposito);
    
    when(fachadaDonadoresYEntidades.obtenerNecesidadesInsatisfechasDe("producto1"))
        .thenReturn(List.of());
    
    assertThrows(NoHayNecesidades.class, () -> {
      fachada.gestionarDonacion(guardado.id(), "donacion1", "producto1", 5);
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
