package ar.edu.utn.dds.k3003.repositories;

import ar.edu.utn.dds.k3003.catedra.dtos.logistica.EstadoAsginacionEnum;
import ar.edu.utn.dds.k3003.exceptions.AsignacionNoEncontrada;
import ar.edu.utn.dds.k3003.model.Asignacion;
import ar.edu.utn.dds.k3003.model.Deposito;
import ar.edu.utn.dds.k3003.model.Paquete;
import ar.edu.utn.dds.k3003.repositories.JpaAsignacionesRepo;
import ar.edu.utn.dds.k3003.repositories.JpaDepositosRepo;
import ar.edu.utn.dds.k3003.repositories.JpaPaquetesRepo;
import ar.edu.utn.dds.k3003.eventos.CambioDeStock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class LogisticaRepository {

    @Autowired
    private JpaDepositosRepo depositosRepo;
    @Autowired
    private JpaAsignacionesRepo asignacionesRepo;
    @Autowired
    private JpaPaquetesRepo paquetesRepo;
    @Autowired
    private ApplicationEventPublisher eventos;

    // ── Depósitos ──────────────────────────────────────────────

    public Deposito guardarDeposito(Deposito deposito) {
        return depositosRepo.save(deposito);
    }

    public Optional<Deposito> buscarDepositoPorID(String id) {
        return depositosRepo.findById(Long.parseLong(id));
    }

    public List<Deposito> obtenerTodosLosDepositos() {
        return depositosRepo.findAll();
    }

    public void eliminarDeposito(String id) {
        depositosRepo.deleteById(Long.parseLong(id));
    }

    public void limpiarDepositos() {
        depositosRepo.deleteAll();
    }

    // ── Asignaciones ───────────────────────────────────────────

    public Asignacion guardarAsignacion(Asignacion asignacion) {
        return asignacionesRepo.save(asignacion);
    }

    public Optional<Asignacion> buscarAsignacionPorID(String id) {
        return asignacionesRepo.findById(Long.parseLong(id));
    }

    public Optional<Asignacion> buscarAsignacionPorPaqueteID(String paqueteID) {
        return asignacionesRepo.findByPaqueteID(paqueteID);
    }

    public List<Asignacion> obtenerTodasLasAsignaciones() {
        return asignacionesRepo.findAll();
    }

    /** Asignaciones cuyo paquete salió de esa donación (D12: el MCP espera a que se asigne). */
    public List<Asignacion> obtenerAsignacionesDeDonacion(String donacionID) {
        List<String> paqueteIDs = paquetesRepo.findByDonacionID(donacionID).stream()
                .map(p -> String.valueOf(p.getId()))
                .toList();
        return paqueteIDs.isEmpty() ? List.of() : asignacionesRepo.findByPaqueteIDIn(paqueteIDs);
    }

    public List<Asignacion> obtenerEntregas() {
        return asignacionesRepo.findByEstado(EstadoAsginacionEnum.COMPLETADA);
    }

    public Asignacion actualizarEstadoAsignacion(String asignacionID, EstadoAsginacionEnum estado) {
        Asignacion asignacion = asignacionesRepo.findById(Long.parseLong(asignacionID))
                .orElseThrow(() -> new AsignacionNoEncontrada("No existe la asignación"));
        asignacion.setEstado(estado);
        return asignacionesRepo.save(asignacion);
    }

    public void limpiarAsignaciones() {
        asignacionesRepo.deleteAll();
    }

    // ── Paquetes ───────────────────────────────────────────────

    public Paquete guardarPaquete(Paquete paquete) {
        return paquetesRepo.save(paquete);
    }

    public Optional<Paquete> buscarPaquetePorID(String id) {
        return paquetesRepo.findById(Long.parseLong(id));
    }

    public List<Paquete> buscarPaquetesEnStockPorProducto(String producto) {
        return paquetesRepo.findByProductoAndDepositoIsNotNullOrderByIdAsc(producto);
    }

    public List<Paquete> buscarStockDeDeposito(Long depositoId) {
        return paquetesRepo.findByDeposito_Id(depositoId);
    }

    public void eliminarPaquete(Paquete paquete) {
        paquetesRepo.eliminarPorId(paquete.getId());
        // El borrado masivo por JPQL no pasa por los listeners de JPA: el stock se avisa a mano.
        if (paquete.getDeposito() != null) {
            eventos.publishEvent(new CambioDeStock(paquete.getDeposito().getId(), paquete.getProducto()));
        }
    }
}