package ar.edu.utn.dds.k3003.services;

import ar.edu.utn.dds.k3003.clients.DonacionesClient;
import ar.edu.utn.dds.k3003.clients.DonadoresYEntidadesClient;
import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.NecesidadMaterialDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.*;
import ar.edu.utn.dds.k3003.exceptions.*;
import ar.edu.utn.dds.k3003.model.*;
import ar.edu.utn.dds.k3003.repositories.*;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.val;
import ar.edu.utn.dds.k3003.infra.logging.EventLogger;
import ar.edu.utn.dds.k3003.infra.logging.EventoLog;
import ar.edu.utn.dds.k3003.infra.logging.LogFields;
import ar.edu.utn.dds.k3003.infra.logging.Outcome;
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

    private static final EventLogger LOG = EventLogger.of(LogisticaService.class);

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
    private final Counter depositosCapacidadExcedida;
    private final Counter donacionesNoActualizadas;
    private final Counter compromisosNoNotificados;

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

        this.asignacionesSolicitudDirecta = Counter.builder("logistica.asignaciones.solicitud_directa")
                .description("Cantidad de asignaciones hechas por consumo directo de stock")
                .tag("componente", "logistica")
                .register(meterRegistry);

        this.depositosCapacidadExcedida = Counter.builder("logistica.depositos.capacidad_excedida")
                .description("Intentos de guardar mas stock del que permite la capacidad del deposito")
                .tag("componente", "logistica")
                .register(meterRegistry);

        this.donacionesNoActualizadas = Counter.builder("logistica.entregas.donacion_no_actualizada")
                .description("Entregas reportadas (necesidad satisfecha, asignacion completada) donde "
                        + "Donaciones no pudo pasar la donacion a ACEPTADA ni con el reintento")
                .tag("componente", "logistica")
                .register(meterRegistry);

        this.compromisosNoNotificados = Counter.builder("logistica.matchmaking.compromiso_no_notificado")
                .description("Asignaciones por matchmaking creadas donde Donadores no pudo enterarse "
                        + "del compromiso ni con el reintento: la necesidad puede sobre-asignarse")
                .tag("componente", "logistica")
                .register(meterRegistry);

        Gauge.builder("logistica.stock.unidades_totales", logisticaRepository,
                        repo -> repo.obtenerTodosLosDepositos().stream()
                                .flatMap(d -> d.getStockActual().stream())
                                .mapToInt(Paquete::getCantidad)
                                .sum())
                .description("Unidades totales en stock, sumadas entre todos los depositos")
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

        // Sin algoritmo, el worker va a fallar al decidir y la donacion se perderia de forma
        // asincronica (el mensaje ya consumido, el donante creyendo que entro). Se rechaza de
        // una, con un mensaje que dice como arreglarlo. Un deposito nace con algoritmo null
        // (spec E2), asi que configurarlo es parte de ponerlo operativo.
        if (deposito.getAlgoritmo() == null) {
            erroresNegocio.increment();
            LOG.evento(EventoLog.DONACION_RECIBIDA, "Donación rechazada")
                    .id(LogFields.DONACION, donacionID)
                    .id(LogFields.DEPOSITO, depositoID)
                    .dato(LogFields.MOTIVO, "algoritmo_no_configurado")
                    .outcome(Outcome.FAILURE).warn().emitir();
            throw new AlgoritmoNoConfiguradoException("El deposito " + depositoID
                    + " no tiene algoritmo de matchmaking configurado: configurarlo con "
                    + "PATCH /depositos/" + depositoID + "/algoritmo antes de recibir donaciones");
        }

        verificarCapacidad(deposito, cantidad);

        LOG.evento(EventoLog.DONACION_RECIBIDA, "Donación recibida")
                .id(LogFields.DONACION, donacionID)
                .id(LogFields.DEPOSITO, depositoID)
                .id(LogFields.PRODUCTO, productoID)
                .dato(LogFields.CANTIDAD, cantidad)
                .emitir();

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

        boolean huboAsignacion = cantidadAsignada != null && cantidadAsignada > 0;
        Paquete paqueteAsignado = null;
        Asignacion asignacionGuardada = null;
        if (huboAsignacion) {
            paqueteAsignado = logisticaRepository.guardarPaquete(
                    new Paquete(donacionID, productoID, cantidadAsignada));
            val asignacion = new Asignacion(
                    paqueteAsignado.getId().toString(), necesidadElegidaID, LocalDateTime.now(), ASIGNADA,
                    OrigenAsignacionEnum.MATCHMAKING);
            asignacionGuardada = logisticaRepository.guardarAsignacion(asignacion);
        }

        Deposito depositoActualizado = deposito;
        if (sobrante != null && sobrante > 0) {
            depositoActualizado = guardarEnStock(deposito, donacionID, productoID, sobrante);
        }

        // Recien aca, con todo lo persistido sin que se haya tirado ninguna excepcion, contamos
        // el matchmaking como exitoso. Si guardarEnStock tira DepositoLleno, el metodo nunca
        // llega a esta linea (y el @Transactional revierte los guardarPaquete/guardarAsignacion
        // de arriba) - asi el contador no queda desincronizado del estado real de la base.
        if (huboAsignacion) {
            matchmakingsEjecutados.increment();
            // Los eventos van recién acá, con todo persistido: si guardarEnStock hubiera tirado
            // DepositoLleno, la transacción se revierte y no habría que haber dicho que se creó nada.
            LOG.evento(EventoLog.PAQUETE_CREADO, "Paquete creado")
                    .id(LogFields.PAQUETE, paqueteAsignado.getId())
                    .id(LogFields.DONACION, donacionID)
                    .emitir();
            LOG.evento(EventoLog.ASIGNACION_CREADA, "Asignación creada")
                    .id(LogFields.ASIGNACION, asignacionGuardada.getId())
                    .id(LogFields.NECESIDAD, necesidadElegidaID)
                    .dato("origen", OrigenAsignacionEnum.MATCHMAKING)
                    .emitir();

            // Le avisa a Donadores que estas unidades quedaron reservadas para la necesidad, así
            // deja de figurar como insatisfecha y no se le sigue asignando de más (docs/coherencia-
            // necesidades_v1.md del repo de Donadores). No revierte lo ya persistido si falla: la
            // asignación es real, esto es solo la notificación.
            boolean donadoresRespondio = comprometerConReintento(necesidadElegidaID, cantidadAsignada);
            LOG.evento(EventoLog.NECESIDAD_COMPROMETIDA, "Compromiso de necesidad notificado")
                    .id(LogFields.NECESIDAD, necesidadElegidaID)
                    .dato(LogFields.CANTIDAD, cantidadAsignada)
                    .outcome(donadoresRespondio ? Outcome.SUCCESS : Outcome.DEGRADED)
                    .emitir();
        }

        return logisticaDataMapper.toDepositoDTO(depositoActualizado);
    }

    // Mismo patrón que avisarleADonacionesConReintento: un reintento simple para absorber una
    // falla transitoria sin tumbar el matchmaking (que ya persistió). No cubre un cold start de
    // Render (30-90s) - logistica.matchmaking.compromiso_no_notificado > 0 es la señal de que
    // hay compromisos que Donadores nunca se enteró.
    private boolean comprometerConReintento(String necesidadID, Integer cantidad) {
        for (int intento = 1; intento <= 2; intento++) {
            try {
                donadoresYEntidadesClient.comprometerNecesidad(necesidadID, cantidad);
                return true;
            } catch (RuntimeException e) {
                // El WARN de la llamada fallida ya lo emitió el interceptor.
                if (intento == 2) {
                    compromisosNoNotificados.increment();
                    return false;
                }
                try {
                    Thread.sleep(1500);
                } catch (InterruptedException interrumpido) {
                    Thread.currentThread().interrupt();
                    compromisosNoNotificados.increment();
                    return false;
                }
            }
        }
        return false;
    }

    private Deposito guardarEnStock(Deposito deposito, String donacionID, String productoID, Integer cantidad) {
        verificarCapacidad(deposito, cantidad);
        deposito.agregarPaquete(new Paquete(donacionID, productoID, cantidad));
        val guardado = logisticaRepository.guardarDeposito(deposito);
        LOG.evento(EventoLog.STOCK_SOBRANTE_PERSISTIDO, "Sobrante enviado a stock")
                .id(LogFields.DEPOSITO, deposito.getId())
                .id(LogFields.DONACION, donacionID)
                .dato(LogFields.CANTIDAD, cantidad)
                .emitir();
        return guardado;
    }

    // Centraliza el chequeo de capacidad para poder instrumentar DepositoLleno con una metrica
    // propia: es el sintoma visible de la condicion de carrera bajo Workers concurrentes ya
    // documentada (multiples Workers reservando espacio del mismo deposito sin lock).
    private void verificarCapacidad(Deposito deposito, Integer cantidad) {
        try {
            deposito.verificarCantidad(cantidad);
        } catch (DepositoLleno e) {
            depositosCapacidadExcedida.increment();
            int ocupado = deposito.getStockActual().stream().mapToInt(Paquete::getCantidad).sum();
            LOG.evento(EventoLog.DEPOSITO_CAPACIDAD_RECHAZADA, "Depósito sin capacidad para la donación")
                    .id(LogFields.DEPOSITO, deposito.getId())
                    .dato(LogFields.CANTIDAD, cantidad)
                    .dato("disponible", deposito.getCapacidadMaxima() - ocupado)
                    .outcome(Outcome.FAILURE).warn().emitir();
            throw e;
        }
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
        val algoritmoAnterior = deposito.getAlgoritmo();
        deposito.setAlgoritmo(tipoAlgoritmo);
        deposito.setAlgoritmoObj(algoritmo);
        logisticaRepository.guardarDeposito(deposito);
        LOG.evento(EventoLog.DEPOSITO_ALGORITMO_CAMBIADO, "Algoritmo de matchmaking cambiado")
                .id(LogFields.DEPOSITO, depositoID)
                .dato(LogFields.EST_ANT, algoritmoAnterior)
                .dato(LogFields.EST_NUE, tipoAlgoritmo)
                .emitir();
    }

    public AsignacionDTO ejecutarMatchmaking(String depositoID, PaqueteDTO paqueteDTO, List<NecesidadMaterialDTO> necesidadesDTO) {
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
        LOG.evento(EventoLog.MATCHMAKING_DECIDIDO, "Matchmaking decidido")
                .dato(LogFields.ALGORITMO, deposito.getAlgoritmo())
                .id(LogFields.NECESIDAD, decision.necesidadElegidaID())
                .dato(LogFields.CANTIDAD, decision.cantidadAsignada())
                .dato("parcial", esParcial(necesidadesDTO, decision))
                .emitir();
        LOG.evento(EventoLog.ASIGNACION_CREADA, "Asignación creada")
                .id(LogFields.ASIGNACION, asignacionGuardada.getId())
                .id(LogFields.NECESIDAD, decision.necesidadElegidaID())
                .dato("origen", OrigenAsignacionEnum.MATCHMAKING)
                .emitir();
        return logisticaDataMapper.toAsignacionDTO(asignacionGuardada);
    }

    public void reportarEntrega(PaqueteDTO paqueteDTO) {
        AsignacionDTO asignacionDTO = this.buscarAsignacionPorPaqueteID(paqueteDTO.id());
        if (asignacionDTO.estado() == COMPLETADA) {
            erroresNegocio.increment();
            LOG.evento(EventoLog.ENTREGA_REPORTADA, "Entrega rechazada")
                    .id(LogFields.PAQUETE, paqueteDTO.id())
                    .id(LogFields.NECESIDAD, asignacionDTO.necesidadID())
                    .dato(LogFields.MOTIVO, "ya_reportada")
                    .outcome(Outcome.FAILURE).warn().emitir();
            throw new EntregaYaReportadaException(
                    "La entrega del paquete " + paqueteDTO.id() + " ya fue reportada anteriormente");
        }
        // donacionID y cantidad se derivan del paquete YA PERSISTIDO, no del body: quien reporta
        // la entrega (bot, MCP, Postman, un reintento manual) podría mandar valores que no
        // coincidan con lo que Logística realmente asignó, y son esos los que importan para
        // satisfacer la necesidad y para avisarle a Donaciones. El id del paquete (paqueteDTO.id())
        // sí es del cliente: es la clave con la que se identificó la entrega desde el principio.
        Paquete paquete = logisticaRepository.buscarPaquetePorID(paqueteDTO.id())
                .orElseThrow(() -> {
                    erroresNoEncontrado.increment();
                    return new NoSuchElementException("No existe el paquete con ese ID");
                });

        // Primero se satisface la necesidad y recien despues se cierra la asignacion, para que
        // un reintento choque contra EntregaYaReportada en vez de volver a sumar la cantidad.
        donadoresYEntidadesClient.satisfacerNecesidad(asignacionDTO.necesidadID(), paquete.getCantidad());
        logisticaRepository.actualizarEstadoAsignacion(asignacionDTO.id(), COMPLETADA);
        entregasReportadas.increment();

        // El aviso a Donaciones va ultimo y no tumba la entrega: la necesidad ya fue satisfecha
        // y la asignacion ya quedo completada, asi que fallar aca mentiria sobre lo que paso.
        // Queda logueado (y contado en una metrica propia) para poder reconciliar el estado de
        // la donacion despues.
        boolean donacionesRespondio = avisarleADonacionesConReintento(paquete.getDonacionID());

        LOG.evento(EventoLog.ENTREGA_REPORTADA, "Entrega reportada")
                .id(LogFields.PAQUETE, paqueteDTO.id())
                .id(LogFields.NECESIDAD, asignacionDTO.necesidadID())
                .id(LogFields.DONACION, paquete.getDonacionID())
                .outcome(donacionesRespondio ? Outcome.SUCCESS : Outcome.DEGRADED)
                .emitir();
    }

    // Reintento simple (1 vez, con una pausa corta) para absorber una falla transitoria de
    // Donaciones sin tumbar la entrega. No cubre un cold start de Render (30-90s): para eso
    // hace falta una reconciliación aparte (endpoint o tarea) que todavía no existe - mientras
    // tanto, logistica.entregas.donacion_no_actualizada > 0 es la señal de que hay donaciones
    // que se entregaron pero quedaron sin pasar a ACEPTADA.
    private boolean avisarleADonacionesConReintento(String donacionID) {
        for (int intento = 1; intento <= 2; intento++) {
            try {
                donacionesClient.cambiarEstadoDeDonacion(donacionID, ACEPTADA);
                return true;
            } catch (RuntimeException e) {
                // El WARN de la llamada fallida ya lo emitió el interceptor.
                if (intento == 2) {
                    donacionesNoActualizadas.increment();
                    return false;
                }
                try {
                    Thread.sleep(1500);
                } catch (InterruptedException interrumpido) {
                    Thread.currentThread().interrupt();
                    donacionesNoActualizadas.increment();
                    return false;
                }
            }
        }
        return false;
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

    /** La donación cubre solo una parte de lo que la necesidad elegida pide (cantidad asignada < objetivo). */
    public static boolean esParcial(List<NecesidadMaterialDTO> necesidades, DecisionDeAsignacion.Decision decision) {
        if (decision.necesidadElegidaID() == null || necesidades == null) {
            return false;
        }
        return necesidades.stream()
                .filter(n -> decision.necesidadElegidaID().equals(n.id()))
                .findFirst()
                .map(n -> n.cantidadObjetivo() != null && decision.cantidadAsignada() < n.cantidadObjetivo())
                .orElse(false);
    }

    public void limpiarBaseDeDatos() {
        logisticaRepository.limpiarAsignaciones();
        logisticaRepository.limpiarDepositos();
    }

    public StockDisponibleDTO consultarStockDisponible(String productoID) {
        int cantidadDisponible = logisticaRepository.buscarPaquetesEnStockPorProducto(productoID).stream()
                .mapToInt(Paquete::getCantidad)
                .sum();
        LOG.evento(EventoLog.STOCK_CONSULTADO, "Stock consultado")
                .id(LogFields.PRODUCTO, productoID)
                .dato("disponible", cantidadDisponible)
                .emitir();
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
            LOG.evento(EventoLog.PAQUETE_CREADO, "Paquete creado")
                    .id(LogFields.PAQUETE, paqueteAsignado.getId())
                    .id(LogFields.DONACION, paquete.getDonacionID())
                    .emitir();
            LOG.evento(EventoLog.ASIGNACION_CREADA, "Asignación creada")
                    .id(LogFields.ASIGNACION, asignacionGuardada.getId())
                    .id(LogFields.NECESIDAD, necesidadID)
                    .dato("origen", OrigenAsignacionEnum.SOLICITUD_DIRECTA)
                    .emitir();

            restante -= tomado;
        }

        LOG.evento(EventoLog.STOCK_CONSUMO_DIRECTO, "Stock consumido para una necesidad")
                .id(LogFields.PRODUCTO, productoID)
                .id(LogFields.NECESIDAD, necesidadID)
                .dato(LogFields.CANTIDAD, cantidadNecesaria - restante)
                .emitir();

        return new ConsumoStockResponseDTO(cantidadNecesaria - restante, asignaciones);
    }
}