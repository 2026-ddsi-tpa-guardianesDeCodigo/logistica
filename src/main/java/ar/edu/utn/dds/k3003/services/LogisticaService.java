package ar.edu.utn.dds.k3003.services;

import ar.edu.utn.dds.k3003.clients.DonacionesClient;
import ar.edu.utn.dds.k3003.clients.DonadoresYEntidadesClient;
import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.NecesidadMaterialDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.TipoNecesidadMaterialEnum;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.*;
import ar.edu.utn.dds.k3003.exceptions.*;
import ar.edu.utn.dds.k3003.model.*;
import ar.edu.utn.dds.k3003.repositories.*;
import lombok.val;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import static ar.edu.utn.dds.k3003.catedra.dtos.donaciones.EstadoDonacionEnum.ACEPTADA;
import static ar.edu.utn.dds.k3003.catedra.dtos.logistica.EstadoAsginacionEnum.ASIGNADA;
import static ar.edu.utn.dds.k3003.catedra.dtos.logistica.EstadoAsginacionEnum.COMPLETADA;

@Service
public class LogisticaService {

    private final JpaDepositosRepo depositosRepository;
    private final JpaAsignacionesRepo asignacionesActivasRepository;
    private final JpaAsignacionesRepo historialAsignacionesRepository;
    private final DonacionesClient donacionesClient;
    private final DonadoresYEntidadesClient donadoresYEntidadesClient;
    private final LogisticaDataMapper logisticaDataMapper = new LogisticaDataMapper();

    public LogisticaService(
            JpaDepositosRepo depositosRepository,
            JpaAsignacionesRepo asignacionesRepo,
            DonacionesClient donacionesClient,
            DonadoresYEntidadesClient donadoresYEntidadesClient) {
        this.depositosRepository = depositosRepository;
        this.asignacionesActivasRepository = asignacionesRepo;
        this.historialAsignacionesRepository = asignacionesRepo;
        this.donacionesClient = donacionesClient;
        this.donadoresYEntidadesClient = donadoresYEntidadesClient;
    }

    public DepositoDTO agregarDeposito(DepositoDTO depositoDTO) {
        if (depositoDTO == null) throw new RuntimeException("El DTO no puede ser nulo");
        if (depositoDTO.id() != null && depositosRepository.findById(depositoDTO.id()).isPresent())
            throw new DepositoYaExistenteException("Ya existe un deposito con ese ID");
        val nuevoDeposito = logisticaDataMapper.toDeposito(depositoDTO);
        val depositoGuardado = depositosRepository.save(nuevoDeposito);
        return logisticaDataMapper.toDepositoDTO(depositoGuardado);
    }

    public DepositoDTO buscarDepositoPorID(String depositoID) throws NoSuchElementException {
        val deposito = depositosRepository.findById(depositoID)
                .orElseThrow(() -> new DepositoNoEncontradoException("No existe un deposito con ese ID"));
        return logisticaDataMapper.toDepositoDTO(deposito);
    }

    public AsignacionDTO buscarAsignacionPorPaqueteID(String paqueteID) throws NoSuchElementException {
        Asignacion asignacion = asignacionesActivasRepository.findByPaqueteID(paqueteID)
                .orElseThrow(() -> new AsignacionNoEncontrada("No existe un paquete con ese ID"));
        return logisticaDataMapper.toAsignacionDTO(asignacion);
    }

    public DepositoDTO gestionarDonacion(String depositoID, String donacionID, String productoID, Integer cantidad) {
        val depositoDTO = buscarDepositoPorID(depositoID);
        val deposito = logisticaDataMapper.toDeposito(depositoDTO);
        deposito.verificarCantidad(cantidad);
        val necesidadesMaterialesDTO = donadoresYEntidadesClient.obtenerNecesidadesInsatisfechasDe(productoID);
        if (necesidadesMaterialesDTO.isEmpty()) throw new NoHayNecesidades("No hay necesidades materiales insatisfechas");
        val paquete = new Paquete(donacionID, productoID, cantidad);
        deposito.agregarPaquete(paquete);
        depositosRepository.save(deposito);
        this.ejecutarMatchmaking(deposito.getId(), logisticaDataMapper.toPaqueteDTO(paquete), necesidadesMaterialesDTO);
        return logisticaDataMapper.toDepositoDTO(deposito);
    }

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

    public AsignacionDTO ejecutarMatchmaking(
            String depositoID,
            PaqueteDTO paqueteDTO,
            List<NecesidadMaterialDTO> necesidadesDTO
    ) {
        Deposito deposito = depositosRepository.findById(depositoID)
                .orElseThrow(() -> new DepositoNoEncontradoException("No existe un deposito con ese ID"));

        Algoritmo algoritmoDelDeposito;

        if (deposito.getAlgoritmo() == null) {
            throw new AlgoritmoNoConfiguradoException("El depósito no tiene algoritmo configurado");
        }

        switch (deposito.getAlgoritmo()) {
            case PRIORIDAD_POR_SCORE -> algoritmoDelDeposito = new PrioridadPorScore();
            case SUB_ATENDIDOS -> algoritmoDelDeposito = new PrioridadASubAtendidos();
            default -> throw new AlgoritmoNoConfiguradoException("El depósito no tiene algoritmo configurado");
        }

        List<NecesidadMaterial> necesidadesDeEntidades = necesidadesDTO.stream()
                .map(logisticaDataMapper::toNecesidadDeEntidad)
                .toList();

        val paquete = logisticaDataMapper.toPaquete(paqueteDTO);
        val necesidadElegida = algoritmoDelDeposito.correr(paquete, necesidadesDeEntidades);

        int indice = necesidadesDeEntidades.indexOf(necesidadElegida);
        val necesidadElegidaDTO = necesidadesDTO.get(indice);

        if (necesidadElegidaDTO.tipo() == TipoNecesidadMaterialEnum.RECURRENTE
                && paquete.getCantidad() < necesidadElegidaDTO.cantidadObjetivo()) {
            throw new DonacionParcialNoPermitida("Las necesidades recurrentes no admiten donaciones parciales");
        }

        val asignacion = new Asignacion(
                UUID.randomUUID().toString(),
                paquete.getId(),
                necesidadElegidaDTO.id(),
                LocalDateTime.now(),
                ASIGNADA
        );

        val asignacionGuardada = asignacionesActivasRepository.save(asignacion);

        historialAsignacionesRepository.save(
                new Asignacion(
                        asignacionGuardada.getId(),
                        paquete.getId(),
                        necesidadElegida.getId(),
                        LocalDateTime.now(),
                        ASIGNADA
                )
        );

        return logisticaDataMapper.toAsignacionDTO(asignacionGuardada);
    }

    public void reportarEntrega(PaqueteDTO paqueteDTO) {
        AsignacionDTO asignacionDTO = this.buscarAsignacionPorPaqueteID(paqueteDTO.id());
        donadoresYEntidadesClient.satisfacerNecesidad(asignacionDTO.necesidadID(), paqueteDTO.cantidad());
        donacionesClient.cambiarEstadoDeDonacion(paqueteDTO.donacionID(), ACEPTADA);
        asignacionesActivasRepository.updateEstado(asignacionDTO.id(), COMPLETADA);
        historialAsignacionesRepository.save(new Asignacion(asignacionDTO.id(), paqueteDTO.id(), asignacionDTO.necesidadID(), asignacionDTO.fecha(), COMPLETADA));
    }

    public AsignacionDTO agregarAsignacion(AsignacionDTO asignacionDTO) {
        if (asignacionesActivasRepository.findById(asignacionDTO.id()).isPresent())
            throw new AsignacionYaExistenteException("Ya existe una asignacion con ese ID");
        val nuevaAsignacion = logisticaDataMapper.toAsignacion(asignacionDTO);
        val asignacionGuardada = asignacionesActivasRepository.save(nuevaAsignacion);
        historialAsignacionesRepository.save(new Asignacion(asignacionGuardada.getId(), asignacionGuardada.getPaqueteID(), asignacionGuardada.getNecesidadID(), asignacionDTO.fecha(), asignacionGuardada.getEstado()));
        return logisticaDataMapper.toAsignacionDTO(asignacionGuardada);
    }

    public DepositoDTO borrarDeposito(String depositoID) {
        var deposito = depositosRepository.findById(depositoID)
                .orElseThrow(() -> new DepositoNoEncontradoException("No existe un deposito con ese ID"));
        depositosRepository.deleteById(depositoID);
        return logisticaDataMapper.toDepositoDTO(deposito);
    }

    public AsignacionDTO buscarAsignacionPorID(String asignacionID) {
        val asignacion = asignacionesActivasRepository.findById(asignacionID)
                .orElseThrow(() -> new AsignacionNoEncontrada("No existe una asignacion con ese ID"));
        return logisticaDataMapper.toAsignacionDTO(asignacion);
    }

    public PaqueteDTO buscarPaquetePorID(String paqueteID) {
        return depositosRepository.findAll().stream()
                .flatMap(deposito -> deposito.getStockActual().stream())
                .filter(paquete -> paquete.getId().equals(paqueteID))
                .map(logisticaDataMapper::toPaqueteDTO)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No existe un paquete con ese ID"));
    }

    public List<DepositoDTO> obtenerTodosLosDepositos() {
        return depositosRepository.findAll().stream()
                .map(logisticaDataMapper::toDepositoDTO).toList();
    }

    public List<AsignacionDTO> obtenerTodasLasAsignaciones() {
        return asignacionesActivasRepository.findAll().stream()
                .map(logisticaDataMapper::toAsignacionDTO).toList();
    }

    public void limpiarBaseDeDatos() {
        asignacionesActivasRepository.deleteAll();
        depositosRepository.deleteAll();
    }
}