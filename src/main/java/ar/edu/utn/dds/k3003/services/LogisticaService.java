package ar.edu.utn.dds.k3003.services;

import ar.edu.utn.dds.k3003.clients.DonacionesClient;
import ar.edu.utn.dds.k3003.clients.DonadoresYEntidadesClient;
import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.NecesidadMaterialDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.TipoNecesidadMaterialEnum;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.*;
import ar.edu.utn.dds.k3003.exceptions.*;
import ar.edu.utn.dds.k3003.model.*;
import ar.edu.utn.dds.k3003.repositories.*;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
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
    private final JpaAsignacionesRepo asignacionesRepository;
    private final DonacionesClient donacionesClient;
    private final DonadoresYEntidadesClient donadoresYEntidadesClient;
    private final LogisticaDataMapper logisticaDataMapper = new LogisticaDataMapper();

    // --- Métricas ---
    private final Counter depositosCreados;
    private final Counter depositosEliminados;
    private final Counter donacionesGestionadas;
    private final Counter matchmakingsEjecutados;
    private final Counter entregasReportadas;
    private final Counter erroresNoEncontrado;
    private final Counter erroresNegocio;
    private final Timer tiempoMatchmaking;

    public LogisticaService(
            JpaDepositosRepo depositosRepository,
            JpaAsignacionesRepo asignacionesRepo,
            DonacionesClient donacionesClient,
            DonadoresYEntidadesClient donadoresYEntidadesClient,
            MeterRegistry meterRegistry) {
        this.depositosRepository = depositosRepository;
        this.asignacionesRepository = asignacionesRepo;
        this.donacionesClient = donacionesClient;
        this.donadoresYEntidadesClient = donadoresYEntidadesClient;


        this.depositosCreados = Counter.builder("logistica.depositos.creados")
                .description("Cantidad de depósitos creados")
                .tag("componente", "logistica")
                .register(meterRegistry);

        this.depositosEliminados = Counter.builder("logistica.depositos.eliminados")
                .description("Cantidad de depósitos eliminados")
                .tag("componente", "logistica")
                .register(meterRegistry);

        this.donacionesGestionadas = Counter.builder("logistica.donaciones.gestionadas")
                .description("Cantidad de donaciones gestionadas")
                .tag("componente", "logistica")
                .register(meterRegistry);

        this.matchmakingsEjecutados = Counter.builder("logistica.matchmaking.ejecutados")
                .description("Cantidad de matchmakings ejecutados exitosamente")
                .tag("componente", "logistica")
                .register(meterRegistry);

        this.entregasReportadas = Counter.builder("logistica.entregas.reportadas")
                .description("Cantidad de entregas reportadas")
                .tag("componente", "logistica")
                .register(meterRegistry);

        this.erroresNoEncontrado = Counter.builder("logistica.errores.not_found")
                .description("Errores por entidad no encontrada")
                .tag("componente", "logistica")
                .register(meterRegistry);

        this.erroresNegocio = Counter.builder("logistica.errores.negocio")
                .description("Errores de reglas de negocio (algoritmo no configurado, donación parcial, etc)")
                .tag("componente", "logistica")
                .register(meterRegistry);

        this.tiempoMatchmaking = Timer.builder("logistica.matchmaking.tiempo")
                .description("Tiempo de ejecución del matchmaking")
                .tag("componente", "logistica")
                .register(meterRegistry);
    }

    public DepositoDTO agregarDeposito(DepositoDTO depositoDTO) {
        if (depositoDTO == null) throw new RuntimeException("El DTO no puede ser nulo");
        if (depositoDTO.id() != null && depositosRepository.findById(depositoDTO.id()).isPresent())
            throw new DepositoYaExistenteException("Ya existe un deposito con ese ID");
        val nuevoDeposito = logisticaDataMapper.toDeposito(depositoDTO);
        val depositoGuardado = depositosRepository.save(nuevoDeposito);
        depositosCreados.increment();
        return logisticaDataMapper.toDepositoDTO(depositoGuardado);
    }

    public DepositoDTO buscarDepositoPorID(String depositoID) throws NoSuchElementException {
        val deposito = depositosRepository.findById(depositoID)
                .orElseThrow(() -> {
                    erroresNoEncontrado.increment();
                    return new DepositoNoEncontradoException("No existe un deposito con ese ID");
                });
        return logisticaDataMapper.toDepositoDTO(deposito);
    }

    public AsignacionDTO buscarAsignacionPorPaqueteID(String paqueteID) throws NoSuchElementException {
        Asignacion asignacion = asignacionesRepository.findByPaqueteID(paqueteID)
                .orElseThrow(() -> {
                    erroresNoEncontrado.increment();
                    return new AsignacionNoEncontrada("No existe un paquete con ese ID");
                });
        return logisticaDataMapper.toAsignacionDTO(asignacion);
    }

    public DepositoDTO gestionarDonacion(String depositoID, String donacionID, String productoID, Integer cantidad) {
        val deposito = depositosRepository.findById(depositoID)
                .orElseThrow(() -> {
                    erroresNoEncontrado.increment();
                    return new DepositoNoEncontradoException("No existe un deposito con ese ID");
                });
        deposito.verificarCantidad(cantidad);
        val necesidadesMaterialesDTO = donadoresYEntidadesClient.obtenerNecesidadesInsatisfechasDe(productoID);
        if (necesidadesMaterialesDTO.isEmpty()) {
            erroresNegocio.increment();
            throw new NoHayNecesidades("No hay necesidades materiales insatisfechas");
        }
        val paquete = new Paquete(donacionID, productoID, cantidad);
        deposito.agregarPaquete(paquete);
        depositosRepository.save(deposito);
        this.ejecutarMatchmaking(deposito.getId(), logisticaDataMapper.toPaqueteDTO(paquete), necesidadesMaterialesDTO);
        donacionesGestionadas.increment();
        return logisticaDataMapper.toDepositoDTO(deposito);
    }

    public void setAlgoritmoMM(String depositoID, TipoAlgoritmoEnum tipoAlgoritmo) {
        Deposito deposito = depositosRepository.findById(depositoID)
                .orElseThrow(() -> {
                    erroresNoEncontrado.increment();
                    return new DepositoNoEncontradoException("No existe un deposito con ese ID");
                });
        Algoritmo algoritmo = switch (tipoAlgoritmo) {
            case SUB_ATENDIDOS -> new PrioridadASubAtendidos();
            case PRIORIDAD_POR_SCORE -> new PrioridadPorScore();
        };
        deposito.setAlgoritmo(tipoAlgoritmo);
        deposito.setAlgoritmoObj(algoritmo);
        depositosRepository.save(deposito);
    }

<<<<<<< HEAD
    public AsignacionDTO ejecutarMatchmaking(String depositoID, PaqueteDTO paqueteDTO, List<NecesidadMaterialDTO> necesidadesDTO) {
        return tiempoMatchmaking.record(() -> {
            Deposito deposito = depositosRepository.findById(depositoID)
                    .orElseThrow(() -> {
                        erroresNoEncontrado.increment();
                        return new DepositoNoEncontradoException("No existe un deposito con ese ID");
                    });
            Algoritmo algoritmoDelDeposito = deposito.getAlgoritmoObj();
            if (algoritmoDelDeposito == null) {
                erroresNegocio.increment();
                throw new AlgoritmoNoConfiguradoException("El depósito no tiene algoritmo configurado");
            }
            List<NecesidadMaterial> necesidadesDeEntidades = necesidadesDTO.stream()
                    .map(logisticaDataMapper::toNecesidadDeEntidad).toList();
            val paquete = logisticaDataMapper.toPaquete(paqueteDTO);
            val necesidadElegida = algoritmoDelDeposito.correr(paquete, necesidadesDeEntidades);
            int indice = necesidadesDeEntidades.indexOf(necesidadElegida);
            val necesidadElegidaDTO = necesidadesDTO.get(indice);
            if (necesidadElegidaDTO.tipo() == TipoNecesidadMaterialEnum.RECURRENTE
                    && paquete.getCantidad() < necesidadElegidaDTO.cantidadObjetivo()) {
                erroresNegocio.increment();
                throw new DonacionParcialNoPermitida("Las necesidades recurrentes no admiten donaciones parciales");
            }
            val asignacion = new Asignacion(null, paquete.getId(), necesidadElegidaDTO.id(), LocalDateTime.now(), ASIGNADA);
            val asignacionGuardada = asignacionesRepository.save(asignacion);
            matchmakingsEjecutados.increment();
            return logisticaDataMapper.toAsignacionDTO(asignacionGuardada);
        });
=======
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
>>>>>>> 4a29d62ac740d8d93de3824087d43ad053fabd17
    }

    public void reportarEntrega(PaqueteDTO paqueteDTO) {
        AsignacionDTO asignacionDTO = this.buscarAsignacionPorPaqueteID(paqueteDTO.id());
        donadoresYEntidadesClient.satisfacerNecesidad(asignacionDTO.necesidadID(), paqueteDTO.cantidad());
        donacionesClient.cambiarEstadoDeDonacion(paqueteDTO.donacionID(), ACEPTADA);
        asignacionesRepository.updateEstado(asignacionDTO.id(), COMPLETADA);
        entregasReportadas.increment();
    }

    public AsignacionDTO agregarAsignacion(AsignacionDTO asignacionDTO) {
        if (asignacionDTO.id() != null && asignacionesRepository.findById(asignacionDTO.id()).isPresent())
            throw new AsignacionYaExistenteException("Ya existe una asignacion con ese ID");
        val nuevaAsignacion = logisticaDataMapper.toAsignacion(asignacionDTO);
        val asignacionGuardada = asignacionesRepository.save(nuevaAsignacion);
        return logisticaDataMapper.toAsignacionDTO(asignacionGuardada);
    }

    public DepositoDTO borrarDeposito(String depositoID) {
        var deposito = depositosRepository.findById(depositoID)
                .orElseThrow(() -> {
                    erroresNoEncontrado.increment();
                    return new DepositoNoEncontradoException("No existe un deposito con ese ID");
                });
        depositosRepository.deleteById(depositoID);
        depositosEliminados.increment();
        return logisticaDataMapper.toDepositoDTO(deposito);
    }

    public AsignacionDTO buscarAsignacionPorID(String asignacionID) {
        val asignacion = asignacionesRepository.findById(asignacionID)
                .orElseThrow(() -> {
                    erroresNoEncontrado.increment();
                    return new AsignacionNoEncontrada("No existe una asignacion con ese ID");
                });
        return logisticaDataMapper.toAsignacionDTO(asignacion);
    }

    public PaqueteDTO buscarPaquetePorID(String paqueteID) {
        return depositosRepository.findAll().stream()
                .flatMap(deposito -> deposito.getStockActual().stream())
                .filter(paquete -> paquete.getId().equals(paqueteID))
                .map(logisticaDataMapper::toPaqueteDTO)
                .findFirst()
                .orElseThrow(() -> {
                    erroresNoEncontrado.increment();
                    return new RuntimeException("No existe un paquete con ese ID");
                });
    }

    public List<DepositoDTO> obtenerTodosLosDepositos() {
        return depositosRepository.findAll().stream()
                .map(logisticaDataMapper::toDepositoDTO).toList();
    }

    public List<AsignacionDTO> obtenerTodasLasAsignaciones() {
        return asignacionesRepository.findAll().stream()
                .map(logisticaDataMapper::toAsignacionDTO).toList();
    }

    public void limpiarBaseDeDatos() {
        asignacionesRepository.deleteAll();
        depositosRepository.deleteAll();
    }
}
