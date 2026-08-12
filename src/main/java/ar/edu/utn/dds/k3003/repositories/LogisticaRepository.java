package ar.edu.utn.dds.k3003.repositories;

import ar.edu.utn.dds.k3003.catedra.dtos.logistica.EstadoAsginacionEnum;
import ar.edu.utn.dds.k3003.exceptions.AsignacionNoEncontrada;
import ar.edu.utn.dds.k3003.model.Asignacion;
import ar.edu.utn.dds.k3003.model.Deposito;
import ar.edu.utn.dds.k3003.model.Paquete;
import ar.edu.utn.dds.k3003.repositories.JpaAsignacionesRepo;
import ar.edu.utn.dds.k3003.repositories.JpaDepositosRepo;
import ar.edu.utn.dds.k3003.repositories.JpaPaquetesRepo;
import org.springframework.beans.factory.annotation.Autowired;
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

    public List<Paquete> buscarPaquetesEnStockPorProducto(String producto) {
        return paquetesRepo.findByProductoAndDepositoIsNotNullOrderByIdAsc(producto);
    }

    public void eliminarPaquete(Paquete paquete) {
        paquetesRepo.eliminarPorId(paquete.getId());
    }
}