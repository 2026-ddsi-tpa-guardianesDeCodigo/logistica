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
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import static ar.edu.utn.dds.k3003.catedra.dtos.donaciones.EstadoDonacionEnum.ACEPTADA;
import static ar.edu.utn.dds.k3003.catedra.dtos.logistica.EstadoAsginacionEnum.ASIGNADA;
import static ar.edu.utn.dds.k3003.catedra.dtos.logistica.EstadoAsginacionEnum.COMPLETADA;

@Service
public class LogisticaService {

    private final LogisticaRepository logisticaRepository;
    private final DonacionesClient donacionesClient;
    private final DonadoresYEntidadesClient donadoresYEntidadesClient;
    private final LogisticaDataMapper logisticaDataMapper = new LogisticaDataMapper();

    // --- MÃ©tricas ---
    private final Counter depositosCreados;
    private final Counter depositosEliminados;
    private final Counter donacionesGestionadas;
    private final Counter matchmakingsEjecutados;
    private final Counter entregasReportadas;
    private final Counter erroresNoEncontrado;
    private final Counter erroresNegocio;
    private final Timer tiempoMatchmaking;

    public LogisticaService(
            LogisticaRepository logisticaRepository,
            DonacionesClient donacionesClient,
            DonadoresYEntidadesClient donadoresYEntidadesClient,
            MeterRegistry meterRegistry) {
        this.logisticaRepository = logisticaRepository;
        this.donacionesClient = donacionesClient;
        this.donadoresYEntidadesClient = donadoresYEntidadesClient;

        this.depositosCreados = Counter.builder("logistica.depositos.creados")
                .description("Cantidad de depÃ³sitos creados")
                .tag("componente", "logistica")
                .register(meterRegistry);

        this.depositosEliminados = Counter.builder("logistica.depositos.eliminados")
                .description("Cantidad de depÃ³sitos eliminados")
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
                .description("Errores de reglas de negocio (algoritmo no configurado, donaciÃ³n parcial, etc)")
                .tag("componente", "logistica")
                .register(meterRegistry);

        this.tiempoMatchmaking = Timer.builder("logistica.matchmaking.tiempo")
                .description("Tiempo de ejecuciÃ³n del matchmaking")
                .tag("componente", "logistica")
                .register(meterRegistry);
    }

    public DepositoDTO agregarDeposito(DepositoDTO depositoDTO) {

        if (depositoDTO.capacidadMaxima() == null)
            throw new IllegalArgumentException("La capacidad maximma es obligatoria");

        if (depositoDTO.id() != null && logisticaRepository.buscarDepositoPorID(depositoDTO.id()).isPresent())
            throw new DepositoYaExistenteException("Ya existe un deposito con ese ID");

        val nuevoDeposito = logisticaDataMapper.toDeposito(depositoDTO);
        if (depositoDTO.algoritmo() != null) {
            nuevoDeposito.reconstruirAlgoritmo();
        }
        val depositoGuardado = logisticaRepository.guardarDeposito(nuevoDeposito);
        depositosCreados.increment();
        return logisticaDataMapper.toDepositoDTO(depositoGuardado);
    }

    public DepositoDTO buscarDepositoPorID(String depositoID) throws NoSuchElementException {
        val deposito = logisticaRepository.buscarDepositoPorID(depositoID)
                .orElseThrow(() -> {
                    erroresNoEncontrado.increment();
                    return new DepositoNoEncontradoException("No existe un deposito con ese ID");
                });
        return logisticaDataMapper.toDepositoDTO(deposito);
    }

    public AsignacionDTO buscarAsignacionPorPaqueteID(String paqueteID) throws NoSuchElementException {
        Asignacion asignacion = logisticaRepository.buscarAsignacionPorPaqueteID(paqueteID)
                .orElseThrow(() -> {
                    erroresNoEncontrado.increment();
                    return new AsignacionNoEncontrada("No existe un paquete con ese ID");
                });
        return logisticaDataMapper.toAsignacionDTO(asignacion);
    }

    public DepositoDTO gestionarDonacion(String depositoID, String donacionID, String productoID, Integer cantidad) {
        val deposito = logisticaRepository.buscarDepositoPorID(depositoID)
                .orElseThrow(() -> {
                    erroresNoEncontrado.increment();
                    return new DepositoNoEncontradoException("No existe un deposito con ese ID");
                });
        deposito.verificarCantidadValida(cantidad);

        val necesidadesMaterialesDTO = donadoresYEntidadesClient.obtenerNecesidadesInsatisfechasDe(productoID);

        val depositoActualizado = this.asignarOGuardarEnStock(
                deposito, donacionID, productoID, cantidad, necesidadesMaterialesDTO);

        donacionesGestionadas.increment();
        return logisticaDataMapper.toDepositoDTO(depositoActualizado);
    }

    private Deposito asignarOGuardarEnStock(
            Deposito deposito,
            String donacionID,
            String productoID,
            Integer cantidad,
            List<NecesidadMaterialDTO> necesidadesDisponibles) {

        if (necesidadesDisponibles.isEmpty()) {
            return guardarEnStock(deposito, donacionID, productoID, cantidad);
        }

        Algoritmo algoritmoDelDeposito = deposito.getAlgoritmoObj();
        if (algoritmoDelDeposito == null) {
            erroresNegocio.increment();
            throw new AlgoritmoNoConfiguradoException("El depósito no tiene algoritmo configurado");
        }

        List<NecesidadMaterial> necesidadesDeEntidades = necesidadesDisponibles.stream()
                .map(logisticaDataMapper::toNecesidadDeEntidad).toList();
        val paqueteCandidato = new Paquete(donacionID, productoID, cantidad);
        val necesidadElegidaModelo = algoritmoDelDeposito.correr(paqueteCandidato, necesidadesDeEntidades);
        int indice = necesidadesDeEntidades.indexOf(necesidadElegidaModelo);
        val necesidadElegida = necesidadesDisponibles.get(indice);

        boolean noAlcanza = cantidad < necesidadElegida.cantidadObjetivo();
        if (noAlcanza && necesidadElegida.tipo() == TipoNecesidadMaterialEnum.RECURRENTE) {
            val restantes = new ArrayList<>(necesidadesDisponibles);
            restantes.remove(indice);
            return asignarOGuardarEnStock(deposito, donacionID, productoID, cantidad, restantes);
        }

        int cantidadAAsignar = Math.min(cantidad, necesidadElegida.cantidadObjetivo());
        int sobrante = cantidad - cantidadAAsignar;

        val paqueteAsignado = logisticaRepository.guardarPaquete(
                new Paquete(donacionID, productoID, cantidadAAsignar));
        val asignacion = new Asignacion(
                paqueteAsignado.getId().toString(), necesidadElegida.id(), LocalDateTime.now(), ASIGNADA);
        logisticaRepository.guardarAsignacion(asignacion);
        matchmakingsEjecutados.increment();

        if (sobrante > 0) {
            return guardarEnStock(deposito, donacionID, productoID, sobrante);
        }
        return logisticaRepository.guardarDeposito(deposito);
    }

    private Deposito guardarEnStock(Deposito deposito, String donacionID, String productoID, Integer cantidad) {
        deposito.verificarCantidad(cantidad);
        deposito.agregarPaquete(new Paquete(donacionID, productoID, cantidad));
        return logisticaRepository.guardarDeposito(deposito);
    }

    public void setAlgoritmoMM(String depositoID, TipoAlgoritmoEnum tipoAlgoritmo) {
        Deposito deposito = logisticaRepository.buscarDepositoPorID(depositoID)
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
        logisticaRepository.guardarDeposito(deposito);
    }

    public AsignacionDTO ejecutarMatchmaking(String depositoID, PaqueteDTO paqueteDTO, List<NecesidadMaterialDTO> necesidadesDTO) {
        return tiempoMatchmaking.record(() -> {
            Deposito deposito = logisticaRepository.buscarDepositoPorID(depositoID)
                    .orElseThrow(() -> {
                        erroresNoEncontrado.increment();
                        return new DepositoNoEncontradoException("No existe un deposito con ese ID");
                    });
            Algoritmo algoritmoDelDeposito = deposito.getAlgoritmoObj();
            if (algoritmoDelDeposito == null) {
                erroresNegocio.increment();
                throw new AlgoritmoNoConfiguradoException("El depÃ³sito no tiene algoritmo configurado");
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
            val asignacion = new Asignacion(paqueteDTO.id(), necesidadElegidaDTO.id(), LocalDateTime.now(), ASIGNADA);
            val asignacionGuardada = logisticaRepository.guardarAsignacion(asignacion);
            matchmakingsEjecutados.increment();
            return logisticaDataMapper.toAsignacionDTO(asignacionGuardada);
        });
    }

    public void reportarEntrega(PaqueteDTO paqueteDTO) {
        AsignacionDTO asignacionDTO = this.buscarAsignacionPorPaqueteID(paqueteDTO.id());
        donadoresYEntidadesClient.satisfacerNecesidad(asignacionDTO.necesidadID(), paqueteDTO.cantidad());
        donacionesClient.cambiarEstadoDeDonacion(paqueteDTO.donacionID(), ACEPTADA);
        logisticaRepository.actualizarEstadoAsignacion(asignacionDTO.id(), COMPLETADA);
        entregasReportadas.increment();
    }

    public AsignacionDTO agregarAsignacion(AsignacionDTO asignacionDTO) {
        if (asignacionDTO.id() != null && logisticaRepository.buscarAsignacionPorID(asignacionDTO.id()).isPresent())
            throw new AsignacionYaExistenteException("Ya existe una asignacion con ese ID");
        val nuevaAsignacion = logisticaDataMapper.toAsignacion(asignacionDTO);
        val asignacionGuardada = logisticaRepository.guardarAsignacion(nuevaAsignacion);
        return logisticaDataMapper.toAsignacionDTO(asignacionGuardada);
    }

    public DepositoDTO borrarDeposito(String depositoID) {
        var deposito = logisticaRepository.buscarDepositoPorID(depositoID)
                .orElseThrow(() -> {
                    erroresNoEncontrado.increment();
                    return new DepositoNoEncontradoException("No existe un deposito con ese ID");
                });
        logisticaRepository.eliminarDeposito(depositoID);
        depositosEliminados.increment();
        return logisticaDataMapper.toDepositoDTO(deposito);
    }

    public AsignacionDTO buscarAsignacionPorID(String asignacionID) {
        val asignacion = logisticaRepository.buscarAsignacionPorID(asignacionID)
                .orElseThrow(() -> {
                    erroresNoEncontrado.increment();
                    return new AsignacionNoEncontrada("No existe una asignacion con ese ID");
                });
        return logisticaDataMapper.toAsignacionDTO(asignacion);
    }

    public PaqueteDTO buscarPaquetePorID(String paqueteID) {
        return logisticaRepository.obtenerTodosLosDepositos().stream()
                .flatMap(deposito -> deposito.getStockActual().stream())
                .filter(paquete -> paquete.getId().toString().equals(paqueteID))
                .map(logisticaDataMapper::toPaqueteDTO)
                .findFirst()
                .orElseThrow(() -> {
                    erroresNoEncontrado.increment();
                    return new RuntimeException("No existe un paquete con ese ID");
                });
    }

    public List<DepositoDTO> obtenerTodosLosDepositos() {
        return logisticaRepository.obtenerTodosLosDepositos().stream()
                .map(logisticaDataMapper::toDepositoDTO).toList();
    }

    public List<AsignacionDTO> obtenerTodasLasAsignaciones() {
        return logisticaRepository.obtenerTodasLasAsignaciones().stream()
                .map(logisticaDataMapper::toAsignacionDTO).toList();
    }

    public void limpiarBaseDeDatos() {
        logisticaRepository.limpiarAsignaciones();
        logisticaRepository.limpiarDepositos();
    }
}