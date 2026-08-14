package ar.edu.utn.dds.k3003.services;

import ar.edu.utn.dds.k3003.clients.DonacionesClient;
import ar.edu.utn.dds.k3003.clients.DonadoresYEntidadesClient;
import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.NecesidadMaterialDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.*;
import ar.edu.utn.dds.k3003.exceptions.*;
import ar.edu.utn.dds.k3003.model.*;
import ar.edu.utn.dds.k3003.repositories.*;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.val;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final DonacionQueuePublisher donacionQueuePublisher;
    private final LogisticaDataMapper logisticaDataMapper = new LogisticaDataMapper();

    // --- MÃ©tricas ---
    private final Counter depositosCreados;
    private final Counter depositosEliminados;
    private final Counter donacionesGestionadas;
    private final Counter matchmakingsEjecutados;
    private final Counter entregasReportadas;
    private final Counter erroresNoEncontrado;
    private final Counter erroresNegocio;
    private final Counter asignacionesSolicitudDirecta;
    private final Timer tiempoMatchmaking;

    public LogisticaService(
            LogisticaRepository logisticaRepository,
            DonacionesClient donacionesClient,
            DonadoresYEntidadesClient donadoresYEntidadesClient,
            DonacionQueuePublisher donacionQueuePublisher,
            MeterRegistry meterRegistry) {
        this.logisticaRepository = logisticaRepository;
        this.donacionesClient = donacionesClient;
        this.donadoresYEntidadesClient = donadoresYEntidadesClient;
        this.donacionQueuePublisher = donacionQueuePublisher;

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

        this.asignacionesSolicitudDirecta = Counter.builder("logistica.asignaciones.solicitud_directa")
                .description("Cantidad de asignaciones hechas por consumo directo de stock")
                .tag("componente", "logistica")
                .register(meterRegistry);
    }

    public DepositoDTO agregarDeposito(DepositoDTO depositoDTO) {

        if (depositoDTO.capacidadMaxima() == null)
            throw new IllegalArgumentException("La capacidad maximma es obligatoria");

        if (depositoDTO.capacidadMaxima() <= 0)
            throw new IllegalArgumentException("La capacidad maxima debe ser mayor a cero");

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

    // Parte B: ya no decide ni persiste la asignacion aca. Solo verifica que haya lugar
    // (chequeo optimista: si toda la cantidad tuviera que ir a stock, entra) y encola la
    // donacion para que un Worker (embebido en esta misma instancia via DonacionQueueListener,
    // o uno externo standalone) calcule la asignacion y la reporte via persistirResultadoWorker.
    public DepositoDTO gestionarDonacion(String depositoID, String donacionID, String productoID, Integer cantidad) {
        val deposito = logisticaRepository.buscarDepositoPorID(depositoID)
                .orElseThrow(() -> {
                    erroresNoEncontrado.increment();
                    return new DepositoNoEncontradoException("No existe un deposito con ese ID");
                });
        deposito.verificarCantidad(cantidad);

        donacionQueuePublisher.publicar(new DonacionMensajeDTO(depositoID, donacionID, productoID, cantidad));
        donacionesGestionadas.increment();

        return logisticaDataMapper.toDepositoDTO(deposito);
    }

    // Llamado por DonacionQueueListener (worker embebido) o por el endpoint que atiende al
    // Worker standalone, con la decision YA tomada (ver DecisionDeAsignacion). Esta parte
    // es la unica que persiste: crea el paquete asignado + Asignacion si corresponde, y
    // manda el sobrante a stock.
    @Transactional
    public DepositoDTO persistirResultadoWorker(
            String depositoID,
            String donacionID,
            String productoID,
            String necesidadElegidaID,
            Integer cantidadAsignada,
            Integer sobrante) {
        if (cantidadAsignada != null && cantidadAsignada < 0)
            throw new CantidadDeProductoInvalida("La cantidad asignada no puede ser negativa");

        if (sobrante != null && sobrante < 0)
            throw new CantidadDeProductoInvalida("El sobrante no puede ser negativo");

        val deposito = logisticaRepository.buscarDepositoPorID(depositoID)
                .orElseThrow(() -> {
                    erroresNoEncontrado.increment();
                    return new DepositoNoEncontradoException("No existe un deposito con ese ID");
                });

        if (cantidadAsignada != null && cantidadAsignada > 0) {
            val paqueteAsignado = logisticaRepository.guardarPaquete(
                    new Paquete(donacionID, productoID, cantidadAsignada));
            val asignacion = new Asignacion(
                    paqueteAsignado.getId().toString(), necesidadElegidaID, LocalDateTime.now(), ASIGNADA,
                    OrigenAsignacionEnum.MATCHMAKING);
            logisticaRepository.guardarAsignacion(asignacion);
            matchmakingsEjecutados.increment();
        }

        Deposito depositoActualizado = deposito;
        if (sobrante != null && sobrante > 0) {
            depositoActualizado = guardarEnStock(deposito, donacionID, productoID, sobrante);
        }

        return logisticaDataMapper.toDepositoDTO(depositoActualizado);
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
            // Misma logica de skip-and-retry para necesidades recurrentes insuficientes que usa
            // el flujo async real (DecisionDeAsignacion), para no divergir de ese comportamiento.
            DecisionDeAsignacion.Decision decision = DecisionDeAsignacion.decidir(
                    algoritmoDelDeposito, paqueteDTO.producto(), paqueteDTO.cantidad(), necesidadesDTO);
            if (decision.necesidadElegidaID() == null) {
                erroresNegocio.increment();
                throw new NoHayNecesidades("No hay necesidades materiales insatisfechas");
            }
            val asignacion = new Asignacion(paqueteDTO.id(), decision.necesidadElegidaID(), LocalDateTime.now(),
                    ASIGNADA, OrigenAsignacionEnum.MATCHMAKING);
            val asignacionGuardada = logisticaRepository.guardarAsignacion(asignacion);
            matchmakingsEjecutados.increment();
            return logisticaDataMapper.toAsignacionDTO(asignacionGuardada);
        });
    }

    public void reportarEntrega(PaqueteDTO paqueteDTO) {
        AsignacionDTO asignacionDTO = this.buscarAsignacionPorPaqueteID(paqueteDTO.id());
        if (asignacionDTO.estado() == COMPLETADA) {
            erroresNegocio.increment();
            throw new EntregaYaReportadaException(
                    "La entrega del paquete " + paqueteDTO.id() + " ya fue reportada anteriormente");
        }
        donadoresYEntidadesClient.satisfacerNecesidad(asignacionDTO.necesidadID(), paqueteDTO.cantidad());
        donacionesClient.cambiarEstadoDeDonacion(paqueteDTO.donacionID(), ACEPTADA);
        logisticaRepository.actualizarEstadoAsignacion(asignacionDTO.id(), COMPLETADA);
        entregasReportadas.increment();
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

    public StockDisponibleDTO consultarStockDisponible(String productoID) {
        int cantidadDisponible = logisticaRepository.buscarPaquetesEnStockPorProducto(productoID).stream()
                .mapToInt(Paquete::getCantidad)
                .sum();
        return new StockDisponibleDTO(productoID, cantidadDisponible);
    }

    @Transactional
    public ConsumoStockResponseDTO consumirStock(String productoID, String necesidadID, Integer cantidadNecesaria) {
        if (cantidadNecesaria == null || cantidadNecesaria <= 0) {
            throw new CantidadDeProductoInvalida("La cantidad necesaria debe ser mayor o igual a 1");
        }

        List<Paquete> paquetesEnStock = logisticaRepository.buscarPaquetesEnStockPorProducto(productoID);
        List<AsignacionDTO> asignaciones = new ArrayList<>();
        int restante = cantidadNecesaria;

        for (Paquete paquete : paquetesEnStock) {
            if (restante == 0) {
                break;
            }
            int tomado = Math.min(restante, paquete.getCantidad());

            if (tomado == paquete.getCantidad()) {
                logisticaRepository.eliminarPaquete(paquete);
            } else {
                paquete.setCantidad(paquete.getCantidad() - tomado);
                logisticaRepository.guardarPaquete(paquete);
            }

            val paqueteAsignado = logisticaRepository.guardarPaquete(
                    new Paquete(paquete.getDonacionID(), productoID, tomado));
            val asignacion = new Asignacion(
                    paqueteAsignado.getId().toString(), necesidadID, LocalDateTime.now(), ASIGNADA,
                    OrigenAsignacionEnum.SOLICITUD_DIRECTA);
            val asignacionGuardada = logisticaRepository.guardarAsignacion(asignacion);
            asignaciones.add(logisticaDataMapper.toAsignacionDTO(asignacionGuardada));
            asignacionesSolicitudDirecta.increment();

            restante -= tomado;
        }

        return new ConsumoStockResponseDTO(cantidadNecesaria - restante, asignaciones);
    }
}