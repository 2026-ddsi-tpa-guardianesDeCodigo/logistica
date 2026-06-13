package ar.edu.utn.dds.k3003.repositories;

import ar.edu.utn.dds.k3003.catedra.dtos.logistica.EstadoAsginacionEnum;
import ar.edu.utn.dds.k3003.exceptions.AsignacionNoEncontrada;
import ar.edu.utn.dds.k3003.model.Asignacion;
import ar.edu.utn.dds.k3003.model.Deposito;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class LogisticaRepository {

    @Autowired
    private JpaDepositosRepo depositosRepo;
    @Autowired
    private JpaAsignacionesRepo asignacionesRepo;

    private AtomicLong idSecuencialDepositos;
    private AtomicLong idSecuencialAsignaciones;
    private AtomicLong idSecuencialPaquetes;

    @PostConstruct  // ← se ejecuta una vez al levantar la app
    public void inicializarContadores() {
        long maxDeposito = depositosRepo.findAll().stream()
                .map(d -> Long.parseLong(d.getId()))
                .max(Long::compareTo)
                .orElse(0L);

        long maxAsignacion = asignacionesRepo.findAll().stream()
                .map(a -> Long.parseLong(a.getId()))
                .max(Long::compareTo)
                .orElse(0L);

        long maxPaquete = depositosRepo.findAll().stream()
                .flatMap(d -> d.getStockActual().stream())
                .map(p -> Long.parseLong(p.getId()))
                .max(Long::compareTo)
                .orElse(0L);

        idSecuencialDepositos  = new AtomicLong(maxDeposito + 1);
        idSecuencialAsignaciones = new AtomicLong(maxAsignacion + 1);
        idSecuencialPaquetes   = new AtomicLong(maxPaquete + 1);
    }


    // ── Depósitos ──────────────────────────────────────────────

    public Deposito guardarDeposito(Deposito deposito) {
        if (deposito.getId() == null || deposito.getId().isBlank()) {
            deposito.setId(String.valueOf(idSecuencialDepositos.getAndIncrement()));
        }
        // Asignar ID a paquetes nuevos antes de persistir en cascada
        deposito.getStockActual().stream()
                .filter(p -> p.getId() == null || p.getId().isBlank())
                .forEach(p -> p.setId(String.valueOf(idSecuencialPaquetes.getAndIncrement())));
        return depositosRepo.save(deposito);
    }

    public Optional<Deposito> buscarDepositoPorID(String id) {
        return depositosRepo.findById(id);
    }

    public List<Deposito> obtenerTodosLosDepositos() {
        return depositosRepo.findAll();
    }

    public void eliminarDeposito(String id) {
        depositosRepo.deleteById(id);
    }

    public void limpiarDepositos() {
        depositosRepo.deleteAll();
    }

    // ── Asignaciones ───────────────────────────────────────────

    public Asignacion guardarAsignacion(Asignacion asignacion) {
        if (asignacion.getId() == null || asignacion.getId().isBlank()) {
            asignacion.setId(String.valueOf(idSecuencialAsignaciones.getAndIncrement()));
        }
        return asignacionesRepo.save(asignacion);
    }

    public Optional<Asignacion> buscarAsignacionPorID(String id) {
        return asignacionesRepo.findById(id);
    }

    public Optional<Asignacion> buscarAsignacionPorPaqueteID(String paqueteID) {
        return asignacionesRepo.findByPaqueteID(paqueteID);
    }

    public List<Asignacion> obtenerTodasLasAsignaciones() {
        return asignacionesRepo.findAll();
    }

    public Asignacion actualizarEstadoAsignacion(String asignacionID, EstadoAsginacionEnum estado) {
        Asignacion asignacion = asignacionesRepo.findById(asignacionID)
                .orElseThrow(() -> new AsignacionNoEncontrada("No existe la asignación"));
        asignacion.setEstado(estado);
        return asignacionesRepo.save(asignacion);
    }

    public void limpiarAsignaciones() {
        asignacionesRepo.deleteAll();
    }
}