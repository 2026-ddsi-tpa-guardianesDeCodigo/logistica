package ar.edu.utn.dds.k3003;

import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.NecesidadMaterialDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.TipoNecesidadMaterialEnum;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.*;
import ar.edu.utn.dds.k3003.catedra.fachadas.FachadaDonaciones;
import ar.edu.utn.dds.k3003.catedra.fachadas.FachadaDonadoresYEntidades;
import ar.edu.utn.dds.k3003.catedra.fachadas.FachadaLogistica;
import ar.edu.utn.dds.k3003.exceptions.*;
import ar.edu.utn.dds.k3003.model.*;
import ar.edu.utn.dds.k3003.repositories.*;
import lombok.val;

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
  private FachadaDonaciones fachadaDonaciones;
  private FachadaDonadoresYEntidades  fachadaDonadoresYEntidades;

  public Fachada() {

    this.depositosRepository = new InMemoryDepositosRepo();
    this.asignacionesActivasRepository = new InMemoryAsignacionesRepo();
    this.historialAsignacionesRepository = new InMemoryAsignacionesRepo();
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

    val paquete = new Paquete (donacionID, productoID, cantidad);

    deposito.agregarPaquete(paquete);
    depositosRepository.save(deposito);


    val asignacionDTO = this.ejecutarMatchmaking(deposito.getId(), logisticaDataMapper.toPaqueteDTO(paquete), necesidadesMaterialesDTO );

    asignacionesActivasRepository.save(logisticaDataMapper.toAsignacion(asignacionDTO));
    historialAsignacionesRepository.save(logisticaDataMapper.toAsignacion(asignacionDTO));

    return logisticaDataMapper.toDepositoDTO(deposito);
  }

  @Override
  public void setAlgoritmoMM(String depositoID, TipoAlgoritmoEnum tipoAlgoritmo) {
    Deposito deposito = depositosRepository.findById(depositoID)
            .orElseThrow(() -> new DepositoNoEncontradoException("No existe un deposito con ese ID"));

    Algoritmo algoritmo = switch (tipoAlgoritmo) {
      case SUB_ATENDIDOS -> new PrioridadASubAtendidos();
      case PRIORIDAD_POR_SCORE -> new PrioridadPorScore();
    };
    deposito.setAlgoritmoObj(algoritmo);
    depositosRepository.save(deposito);
  }

  @Override
  public AsignacionDTO ejecutarMatchmaking(String depositoID, PaqueteDTO paqueteDTO, List<NecesidadMaterialDTO> necesidadesDTO) {

    Deposito deposito = depositosRepository.findById(depositoID)
            .orElseThrow(() -> new DepositoNoEncontradoException("No existe un deposito con ese ID"));

    Algoritmo algoritmoDelDeposito = deposito.getAlgoritmoObj();
    if (algoritmoDelDeposito == null) {
      throw new AlgoritmoNoConfiguradoException("El depósito no tiene algoritmo configurado");
    }

    List<NecesidadMaterial> necesidadesDeEntidades = necesidadesDTO.stream().
            map(logisticaDataMapper::toNecesidadDeEntidad).
            toList();

    val paquete = logisticaDataMapper.toPaquete(paqueteDTO);

    val necesidadElegida = algoritmoDelDeposito.correr(paquete, necesidadesDeEntidades);

    val necesidadElegidaDTO = necesidadesDTO.stream()
            .filter(n -> n.id().equals(necesidadElegida.getId()))
            .findFirst()
            .orElseThrow();

    if (necesidadElegidaDTO.tipo() == TipoNecesidadMaterialEnum.RECURRENTE
            && paquete.getCantidad() < necesidadElegidaDTO.cantidadObjetivo()) {
      throw new DonacionParcialNoPermitida("Las necesidades recurrentes no admiten donaciones parciales");
    }

    val asignacion = new Asignacion(LocalDateTime.now().toString(), paquete.getId(), necesidadElegida.getId(), LocalDateTime.now(), ASIGNADA);

    return logisticaDataMapper.toAsignacionDTO(asignacion);
  }

  @Override
  public void reportarEntrega(PaqueteDTO paqueteDTO) {

    AsignacionDTO asignacionDTO = this.buscarAsignacionPorPaqueteID(paqueteDTO.id());

    fachadaDonadoresYEntidades.satisfacerNecesidad(asignacionDTO.necesidadID(), paqueteDTO.cantidad());

    fachadaDonaciones.cambiarEstadoDeDonacion(paqueteDTO.donacionID(), ACEPTADA);

    asignacionesActivasRepository.updateEstado(asignacionDTO.id(),COMPLETADA);

    var asignacionHistorial = new Asignacion(asignacionDTO.id(), paqueteDTO.id(), asignacionDTO.necesidadID(), asignacionDTO.fecha(), COMPLETADA);

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
      throw new AsignacionYaExistenteException("Ya existe una asignacion con ese ID");
    }
    val nuevaAsignacion = logisticaDataMapper.toAsignacion(asignacionDTO);

    val asignacionGuardada = asignacionesActivasRepository.save(nuevaAsignacion);

    val asignacionHistorial = new Asignacion(asignacionGuardada.getId(), asignacionGuardada.getPaqueteID(), asignacionGuardada.getNecesidadID(), asignacionDTO.fecha(), asignacionGuardada.getEstado());

    historialAsignacionesRepository.save(asignacionHistorial);

    return logisticaDataMapper.toAsignacionDTO(asignacionGuardada);
  }

  public DepositoDTO borrarDeposito(String depositoID){
    var deposito = depositosRepository.deleteById(depositoID);
    return logisticaDataMapper.toDepositoDTO(deposito);
  }

  public AsignacionDTO  buscarAsignacionPorID (String asignacionID){

    val asignacion = asignacionesActivasRepository.findById(asignacionID).
            orElseThrow(() -> new AsignacionNoEncontrada("No existe una asignacion con ese ID")) ;

    return logisticaDataMapper.toAsignacionDTO(asignacion);
  }

}

