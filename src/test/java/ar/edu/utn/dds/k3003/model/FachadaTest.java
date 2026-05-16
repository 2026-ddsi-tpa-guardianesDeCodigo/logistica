package ar.edu.utn.dds.k3003.model;

import ar.edu.utn.dds.k3003.Fachada;
import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.EstadoDonacionEnum;
import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.NecesidadMaterialDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.TipoNecesidadMaterialEnum;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.*;
import ar.edu.utn.dds.k3003.catedra.fachadas.FachadaDonaciones;
import ar.edu.utn.dds.k3003.catedra.fachadas.FachadaDonadoresYEntidades;
import ar.edu.utn.dds.k3003.exceptions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class FachadaTest {

  private Fachada fachada;

  @Mock
  private FachadaDonadoresYEntidades fachadaDonadoresYEntidades;

  @Mock
  private FachadaDonaciones fachadaDonaciones;

  private DepositoDTO depositoTest;
  private NecesidadMaterialDTO necesidadTest;
  private PaqueteDTO paqueteTest;
  private AsignacionDTO asignacionTest;

  @BeforeEach
  void setUp() {
    fachada = new Fachada();
    fachada.setFachadaDonadoresYEntidades(fachadaDonadoresYEntidades);
    fachada.setFachadaDonaciones(fachadaDonaciones);

    // Inicializar datos de prueba
    depositoTest = new DepositoDTO(null, null, "deposito_test", "direccion_test", 500, null);
    necesidadTest = new NecesidadMaterialDTO(
        "necesidad_test",
        "entidad_test",
        3,
        "descripcion_test",
        10,
        "producto_test",
        TipoNecesidadMaterialEnum.EXTRAORDINARIA
    );
    paqueteTest = new PaqueteDTO("paquete_test", "donacion_test", "producto_test", 10);
    asignacionTest = new AsignacionDTO("asignacion_test", "paquete_test", "necesidad_test", LocalDateTime.now(), EstadoAsginacionEnum.ASIGNADA);
  }

  // ==================== TESTS PARA agregarDeposito ====================
  @Test
  void testAgregarDepositoConDatosValidos() {
    DepositoDTO resultado = fachada.agregarDeposito(depositoTest);

    assertNotNull(resultado);
    assertNotNull(resultado.id());
    assertEquals(depositoTest.nombre(), resultado.nombre());
    assertEquals(depositoTest.direccion(), resultado.direccion());
    assertEquals(depositoTest.capacidadMaxima(), resultado.capacidadMaxima());
  }

  @Test
  void testAgregarDepositoConIDExistenteLanzaExcepcion() {
    DepositoDTO primero = fachada.agregarDeposito(depositoTest);
    DepositoDTO duplicado = new DepositoDTO(primero.id(), null, "otro_nombre", "otra_dir", 100, null);

    assertThrows(DepositoYaExistenteException.class, () -> fachada.agregarDeposito(duplicado));
  }

  @Test
  void testAgregarDepositoNuloLanzaExcepcion() {
    assertThrows(RuntimeException.class, () -> fachada.agregarDeposito(null));
  }

  // ==================== TESTS PARA buscarDepositoPorID ====================
  @Test
  void testBuscarDepositoPorIDExistente() {
    DepositoDTO agregado = fachada.agregarDeposito(depositoTest);
    DepositoDTO encontrado = fachada.buscarDepositoPorID(agregado.id());

    assertNotNull(encontrado);
    assertEquals(agregado.id(), encontrado.id());
    assertEquals(agregado.nombre(), encontrado.nombre());
  }

  @Test
  void testBuscarDepositoPorIDNoExistenteLanzaExcepcion() {
    assertThrows(DepositoNoEncontradoException.class, () -> fachada.buscarDepositoPorID("inexistente"));
  }

  // ==================== TESTS PARA borrarDeposito ====================
  @Test
  void testBorrarDepositoExistente() {
    DepositoDTO agregado = fachada.agregarDeposito(depositoTest);
    DepositoDTO borrado = fachada.borrarDeposito(agregado.id());

    assertNotNull(borrado);
    assertEquals(agregado.id(), borrado.id());

    // Verificar que realmente fue borrado
    assertThrows(DepositoNoEncontradoException.class, () -> fachada.buscarDepositoPorID(agregado.id()));
  }

  // ==================== TESTS PARA obtenerTodosLosDepositos ====================
  @Test
  void testObtenerTodosLosDepositosVacio() {
    List<DepositoDTO> depositos = fachada.obtenerTodosLosDepositos();

    assertNotNull(depositos);
    assertTrue(depositos.isEmpty());
  }

  @Test
  void testObtenerTodosLosDepositosConMultiplesDepositos() {
    fachada.agregarDeposito(depositoTest);
    DepositoDTO deposito2 = new DepositoDTO(null, null, "deposito2", "dir2", 300, null);
    fachada.agregarDeposito(deposito2);

    List<DepositoDTO> depositos = fachada.obtenerTodosLosDepositos();

    assertNotNull(depositos);
    assertEquals(2, depositos.size());
  }

  // ==================== TESTS PARA agregarAsignacion ====================
  @Test
  void testAgregarAsignacionConDatosValidos() {
    AsignacionDTO resultado = fachada.agregarAsignacion(asignacionTest);

    assertNotNull(resultado);
    assertNotNull(resultado.id());
    assertEquals(asignacionTest.paqueteID(), resultado.paqueteID());
    assertEquals(asignacionTest.necesidadID(), resultado.necesidadID());
  }

  @Test
  void testAgregarAsignacionConIDExistenteLanzaExcepcion() {
    AsignacionDTO primera = fachada.agregarAsignacion(asignacionTest);
    AsignacionDTO duplicada = new AsignacionDTO(primera.id(), "otro_paq", "otra_nec", LocalDateTime.now(), EstadoAsginacionEnum.ASIGNADA);

    assertThrows(AsignacionYaExistenteException.class, () -> fachada.agregarAsignacion(duplicada));
  }

  // ==================== TESTS PARA buscarAsignacionPorID ====================
  @Test
  void testBuscarAsignacionPorIDExistente() {
    AsignacionDTO agregada = fachada.agregarAsignacion(asignacionTest);
    AsignacionDTO encontrada = fachada.buscarAsignacionPorID(agregada.id());

    assertNotNull(encontrada);
    assertEquals(agregada.id(), encontrada.id());
    assertEquals(agregada.paqueteID(), encontrada.paqueteID());
  }

  @Test
  void testBuscarAsignacionPorIDNoExistenteLanzaExcepcion() {
    assertThrows(AsignacionNoEncontrada.class, () -> fachada.buscarAsignacionPorID("inexistente"));
  }

  // ==================== TESTS PARA buscarAsignacionPorPaqueteID ====================
  @Test
  void testBuscarAsignacionPorPaqueteIDExistente() {
    fachada.agregarAsignacion(asignacionTest);
    AsignacionDTO encontrada = fachada.buscarAsignacionPorPaqueteID(asignacionTest.paqueteID());

    assertNotNull(encontrada);
    assertEquals(asignacionTest.paqueteID(), encontrada.paqueteID());
  }

  @Test
  void testBuscarAsignacionPorPaqueteIDNoExistenteLanzaExcepcion() {
    assertThrows(AsignacionNoEncontrada.class, () -> fachada.buscarAsignacionPorPaqueteID("inexistente"));
  }

  // ==================== TESTS PARA buscarPaquetePorID ====================
  @Test
  void testBuscarPaquetePorIDNoExistenteLanzaExcepcion() {
    assertThrows(RuntimeException.class, () -> fachada.buscarPaquetePorID("inexistente"));
  }

  // ==================== TESTS PARA setAlgoritmoMM ====================
  @Test
  void testSetAlgoritmoMMConDepositoValido() {
    DepositoDTO agregado = fachada.agregarDeposito(depositoTest);

    // No debe lanzar excepción
    assertDoesNotThrow(() -> fachada.setAlgoritmoMM(agregado.id(), TipoAlgoritmoEnum.SUB_ATENDIDOS));
  }

  @Test
  void testSetAlgoritmoMMConDepositoInexistenteLanzaExcepcion() {
    assertThrows(DepositoNoEncontradoException.class, 
        () -> fachada.setAlgoritmoMM("inexistente", TipoAlgoritmoEnum.SUB_ATENDIDOS));
  }

  @Test
  void testSetAlgoritmoMMConDiferentesAlgoritmos() {
    DepositoDTO agregado = fachada.agregarDeposito(depositoTest);

    assertDoesNotThrow(() -> fachada.setAlgoritmoMM(agregado.id(), TipoAlgoritmoEnum.SUB_ATENDIDOS));
    assertDoesNotThrow(() -> fachada.setAlgoritmoMM(agregado.id(), TipoAlgoritmoEnum.PRIORIDAD_POR_SCORE));
  }

  // ==================== TESTS PARA ejecutarMatchmaking ====================
  @Test
  void testEjecutarMatchmakingConDatosValidos() {
    DepositoDTO depositoAgregado = fachada.agregarDeposito(depositoTest);
    fachada.setAlgoritmoMM(depositoAgregado.id(), TipoAlgoritmoEnum.SUB_ATENDIDOS);

    List<NecesidadMaterialDTO> necesidades = List.of(necesidadTest);

    AsignacionDTO resultado = fachada.ejecutarMatchmaking(depositoAgregado.id(), paqueteTest, necesidades);

    assertNotNull(resultado);
    assertEquals(paqueteTest.id(), resultado.paqueteID());
  }

  @Test
  void testEjecutarMatchmakingSinAlgoritmoConfiguradoLanzaExcepcion() {
    DepositoDTO depositoAgregado = fachada.agregarDeposito(depositoTest);

    List<NecesidadMaterialDTO> necesidades = List.of(necesidadTest);

    assertThrows(AlgoritmoNoConfiguradoException.class, 
        () -> fachada.ejecutarMatchmaking(depositoAgregado.id(), paqueteTest, necesidades));
  }

  @Test
  void testEjecutarMatchmakingConDepositoInexistenteLanzaExcepcion() {
    List<NecesidadMaterialDTO> necesidades = List.of(necesidadTest);

    assertThrows(DepositoNoEncontradoException.class, 
        () -> fachada.ejecutarMatchmaking("inexistente", paqueteTest, necesidades));
  }

  // ==================== TESTS PARA reportarEntrega ====================
  @Test
  void testReportarEntregaSinAsignacionLanzaExcepcion() {
    PaqueteDTO paqueteInexistente = new PaqueteDTO("paq_inexistente", "don_inexistente", "prod", 5);

    assertThrows(AsignacionNoEncontrada.class, () -> fachada.reportarEntrega(paqueteInexistente));
  }

  // ==================== TESTS PARA gestionarDonacion ====================
  @Test
  void testGestionarDonacionConDatosValidos() {
    DepositoDTO depositoAgregado = fachada.agregarDeposito(depositoTest);
    fachada.setAlgoritmoMM(depositoAgregado.id(), TipoAlgoritmoEnum.SUB_ATENDIDOS);

    when(fachadaDonadoresYEntidades.obtenerNecesidadesInsatisfechasDe("producto_test"))
        .thenReturn(List.of(necesidadTest));

    DepositoDTO resultado = fachada.gestionarDonacion(
        depositoAgregado.id(), "donacion_test", "producto_test", 10);

    assertNotNull(resultado);
    assertEquals(depositoAgregado.id(), resultado.id());
  }

  @Test
  void testGestionarDonacionConDepositoInexistenteLanzaExcepcion() {
    assertThrows(DepositoNoEncontradoException.class, 
        () -> fachada.gestionarDonacion("inexistente", "donacion_test", "producto_test", 10));
  }

  @Test
  void testGestionarDonacionSinNecesidadesLanzaExcepcion() {
    DepositoDTO depositoAgregado = fachada.agregarDeposito(depositoTest);
    fachada.setAlgoritmoMM(depositoAgregado.id(), TipoAlgoritmoEnum.SUB_ATENDIDOS);

    when(fachadaDonadoresYEntidades.obtenerNecesidadesInsatisfechasDe("producto_inexistente"))
        .thenReturn(List.of());

    assertThrows(NoHayNecesidades.class, 
        () -> fachada.gestionarDonacion(depositoAgregado.id(), "donacion_test", "producto_inexistente", 10));
  }

}
