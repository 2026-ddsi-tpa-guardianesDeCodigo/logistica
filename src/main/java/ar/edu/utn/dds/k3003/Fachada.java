package ar.edu.utn.dds.k3003;

import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.EstadoDonacionEnum;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.AsignacionDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.DepositoDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.NecesidadDeEntidadDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.PaqueteDTO;
import ar.edu.utn.dds.k3003.catedra.fachadas.FachadaDonaciones;
import ar.edu.utn.dds.k3003.catedra.fachadas.FachadaDonadoresYEntidades;
import ar.edu.utn.dds.k3003.catedra.fachadas.FachadaLogistica;
import ar.edu.utn.dds.k3003.exceptions.*;
import ar.edu.utn.dds.k3003.model.*;
import ar.edu.utn.dds.k3003.repositories.*;
import lombok.val;
import org.springframework.cglib.core.Local;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import static ar.edu.utn.dds.k3003.catedra.dtos.donaciones.EstadoDonacionEnum.ACEPTADA;
import static ar.edu.utn.dds.k3003.catedra.dtos.logistica.EstadoAsginacionEnum.ASIGNADA;
import static ar.edu.utn.dds.k3003.catedra.dtos.logistica.EstadoAsginacionEnum.COMPLETADA;

public class Fachada implements FachadaLogistica {

  private DepositosRepository depositosRepository;
  private AsignacionesRepository asignacionesActivasRepository;
  private AsignacionesRepository historialAsignacionesRepository;
  private LogisticaDataMapper logisticaDataMapper = new LogisticaDataMapper();
  private Algoritmo algoritmo;
  private FachadaDonaciones fachadaDonaciones;
  private FachadaDonadoresYEntidades  fachadaDonadoresYEntidades;

  public Fachada() {

    this.depositosRepository = new InMemoryDepositosRepo();
    this.asignacionesActivasRepository = new InMemoryAsignacionesRepo();
    this.historialAsignacionesRepository = new InMemoryAsignacionesRepo();
    this.setAlgoritmoMM();
  }

  @Override
  public DepositoDTO agregarDeposito (DepositoDTO depositoDTO){

    if(depositosRepository.findById(depositoDTO.id()).isPresent()){
      throw new DepositoYaExistenteException("Ya existe un deposito con ese ID");
    }

    val nuevoDeposito = logisticaDataMapper.toDeposito(depositoDTO);

    val depositoGuardado = depositosRepository.save(nuevoDeposito);

    return logisticaDataMapper.toDepositoDTO(depositoGuardado);
  }

  @Override
  public DepositoDTO buscarDepositoPorID(String depositoID) throws NoSuchElementException {

    val deposito = depositosRepository.findById(depositoID).
                   orElseThrow(() -> new DepositoNoEncontradoException("No existe un deposito con ese ID")) ;

    return logisticaDataMapper.toDepositoDTO(deposito);
  }

  @Override
  public AsignacionDTO buscarAsignacionPorPaqueteID(String paqueteID) throws NoSuchElementException {

    Asignacion asignacion  = asignacionesActivasRepository.findByPaqueteId(paqueteID).
            orElseThrow(() -> new AsignacionNoEncontrada("No existe un paquete con ese ID"));

    return logisticaDataMapper.toAsignacionDTO(asignacion);
  }

  @Override
  public DepositoDTO gestionarDonacion(String depositoID, String donacionID, String productoID, Integer cantidad) throws NoSuchElementException {

    val depositoDTO = buscarDepositoPorID(depositoID);
    val deposito = logisticaDataMapper.toDeposito(depositoDTO);

    deposito.verificarCantidad(cantidad);

    val necesidadesMaterialesDTO = fachadaDonadoresYEntidades.obtenerNecesidadesInsatisfechasDe(productoID);

    if(necesidadesMaterialesDTO.isEmpty()){
      throw new NoHayNecesidades("No hay necesidades materiales insatisfechas");
    }

    val paquete = new Paquete ("DonacionN", productoID, cantidad);

    deposito.agregarPaquete(paquete);

    val necesidadesInsatisfechasDTO = necesidadesMaterialesDTO.stream()
            .map(material -> new NecesidadDeEntidadDTO(
                    material.id(),
                    material.entidadID(),
                    material.nivelDeUrgencia(),
                    material.descripcion(),
                    material.cantidadObjetivo(),
                    material.productoSolicitadoID()
            ))
            .toList();

    val asignacionDTO = this.ejecutarMatchmaking(logisticaDataMapper.toPaqueteDTO(paquete),necesidadesInsatisfechasDTO);

    fachadaDonadoresYEntidades.satisfacerNecesidad(asignacionDTO.necesidadID(), cantidad);

    return depositoDTO;
  }


  @Override
  public void setAlgoritmoMM() {
    algoritmo = new PrioridadASubAtendidos();

  }

  @Override
  public AsignacionDTO ejecutarMatchmaking(PaqueteDTO paqueteDTO, List<NecesidadDeEntidadDTO> necesidadesDeEntidadesDTO) {

    List<NecesidadDeEntidad> necesidadesDeEntidades = necesidadesDeEntidadesDTO.stream().map(logisticaDataMapper::toNecesidadDeEntidad).toList();

    val paquete = logisticaDataMapper.toPaquete(paqueteDTO);

    val necesidadDeEntidad = algoritmo.correr(paquete, necesidadesDeEntidades);

    val asignacion = new Asignacion(paquete.getId(),necesidadDeEntidad.getId(), LocalDateTime.now(),ASIGNADA);

    val asignacionHistorial = new Asignacion(asignacion.getId(), asignacion.getNecesidadID(), asignacion.getFecha(), asignacion.getEstado());

    asignacionHistorial.setId(asignacion.getId());

    asignacionesActivasRepository.save(asignacion);

    historialAsignacionesRepository.save(asignacionHistorial);

    return logisticaDataMapper.toAsignacionDTO(asignacion);
  }

  @Override
  public void reportarEntrega(PaqueteDTO paqueteDTO) {

    fachadaDonaciones.cambiarEstadoDeDonacion(paqueteDTO.donacionID(), ACEPTADA);

    AsignacionDTO asignacionDTO = this.buscarAsignacionPorPaqueteID(paqueteDTO.id());

    asignacionesActivasRepository.updateEstado(asignacionDTO.id(),COMPLETADA);

    var asignacionHistorial = new Asignacion(asignacionDTO.id(), asignacionDTO.necesidadID(), asignacionDTO.fecha(), COMPLETADA);

    asignacionHistorial.setId(asignacionDTO.id());

    historialAsignacionesRepository.save(asignacionHistorial);

  }

  @Override
  public void setFachadaDonadoresYEntidades(FachadaDonadoresYEntidades fachadaDonadoresYEntidades) {
    this.fachadaDonadoresYEntidades = fachadaDonadoresYEntidades;
  }

  @Override
  public void setFachadaDonaciones(FachadaDonaciones fachadaDonaciones) {
    this.fachadaDonaciones = fachadaDonaciones;
  }

  public AsignacionDTO agregarAsignacion (AsignacionDTO asignacionDTO ){

    if(asignacionesActivasRepository.findById(asignacionDTO .id()).isPresent()){
      throw new DepositoYaExistenteException("Ya existe una asignacion con ese ID");
    }
    val nuevaAsignacion = logisticaDataMapper.toAsignacion(asignacionDTO);

    val asignacionGuardada = asignacionesActivasRepository.save(nuevaAsignacion);

    val asignacionHistorial = new Asignacion(asignacionGuardada.getPaqueteID(), asignacionGuardada.getNecesidadID(), asignacionDTO.fecha(), asignacionGuardada.getEstado());

    asignacionHistorial.setId(asignacionGuardada.getId());

    historialAsignacionesRepository.save(asignacionHistorial);

    return logisticaDataMapper.toAsignacionDTO(asignacionGuardada);
  }

}

