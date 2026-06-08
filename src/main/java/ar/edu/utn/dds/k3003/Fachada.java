package ar.edu.utn.dds.k3003;

import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.NecesidadMaterialDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.*;
import ar.edu.utn.dds.k3003.catedra.fachadas.FachadaDonaciones;
import ar.edu.utn.dds.k3003.catedra.fachadas.FachadaDonadoresYEntidades;
import ar.edu.utn.dds.k3003.catedra.fachadas.FachadaLogistica;
import ar.edu.utn.dds.k3003.services.LogisticaService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.NoSuchElementException;

@Component
public class Fachada implements FachadaLogistica {

  private final LogisticaService logisticaService;

  public Fachada(LogisticaService logisticaService) {
    this.logisticaService = logisticaService;
  }

  @Override
  public DepositoDTO agregarDeposito(DepositoDTO depositoDTO) {
    return logisticaService.agregarDeposito(depositoDTO);
  }

  @Override
  public DepositoDTO buscarDepositoPorID(String depositoID) throws NoSuchElementException {
    return logisticaService.buscarDepositoPorID(depositoID);
  }

  @Override
  public AsignacionDTO buscarAsignacionPorPaqueteID(String paqueteID) throws NoSuchElementException {
    return logisticaService.buscarAsignacionPorPaqueteID(paqueteID);
  }

  @Override
  public DepositoDTO gestionarDonacion(String depositoID, String donacionID, String productoID, Integer cantidad) throws NoSuchElementException {
    return logisticaService.gestionarDonacion(depositoID, donacionID, productoID, cantidad);
  }

  @Override
  public void setAlgoritmoMM(String depositoID, TipoAlgoritmoEnum tipoAlgoritmo) {
    logisticaService.setAlgoritmoMM(depositoID, tipoAlgoritmo);
  }

  @Override
  public AsignacionDTO ejecutarMatchmaking(String depositoID, PaqueteDTO paqueteDTO, List<NecesidadMaterialDTO> necesidadesDTO) {
    return logisticaService.ejecutarMatchmaking(depositoID, paqueteDTO, necesidadesDTO);
  }

  @Override
  public void reportarEntrega(PaqueteDTO paqueteDTO) {
    logisticaService.reportarEntrega(paqueteDTO);
  }

  @Override
  public void setFachadaDonadoresYEntidades(FachadaDonadoresYEntidades fachadaDonadoresYEntidades) {
    // se usa DonadoresYEntidadesClient
  }

  @Override
  public void setFachadaDonaciones(FachadaDonaciones fachadaDonaciones) {
    // se usa DonacionesClient
  }

  public AsignacionDTO agregarAsignacion(AsignacionDTO asignacionDTO) {
    return logisticaService.agregarAsignacion(asignacionDTO);
  }

  public DepositoDTO borrarDeposito(String depositoID) {
    return logisticaService.borrarDeposito(depositoID);
  }

  public AsignacionDTO buscarAsignacionPorID(String asignacionID) {
    return logisticaService.buscarAsignacionPorID(asignacionID);
  }

  public PaqueteDTO buscarPaquetePorID(String paqueteID) {
    return logisticaService.buscarPaquetePorID(paqueteID);
  }

  public List<DepositoDTO> obtenerTodosLosDepositos() {
    return logisticaService.obtenerTodosLosDepositos();
  }

  public List<AsignacionDTO> obtenerTodasLasAsignaciones() {
    return logisticaService.obtenerTodasLasAsignaciones();
  }

  public void limpiarBaseDeDatos() {
    logisticaService.limpiarBaseDeDatos();
  }
}