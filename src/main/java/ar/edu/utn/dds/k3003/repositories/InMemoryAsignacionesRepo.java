package ar.edu.utn.dds.k3003.repositories;

import ar.edu.utn.dds.k3003.catedra.dtos.logistica.EstadoAsginacionEnum;
import ar.edu.utn.dds.k3003.exceptions.AsignacionNoEncontrada;
import ar.edu.utn.dds.k3003.model.Asignacion;
import lombok.val;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

public class InMemoryAsignacionesRepo implements AsignacionesRepository{

    private List<Asignacion> asignaciones;
    private AtomicLong idSecuencial = new AtomicLong(1);

    public InMemoryAsignacionesRepo  () {
        this.asignaciones = new ArrayList<>();
    }


    @Override
    public Optional<Asignacion> findById(String id) {
        return this.asignaciones.stream().filter(d -> d.getId().equals(id)).findFirst();
    }

    @Override
    public Optional<Asignacion> findByPaqueteId(String paqueteId) {
        return this.asignaciones.stream().filter(d -> d.getPaqueteID().equals(paqueteId)).findFirst();
    }

    @Override
    public Asignacion save(Asignacion asignacion) {
        Asignacion asignacionConID = asignacion;
        asignacion.setId(String.valueOf(idSecuencial.getAndIncrement()));

        this.asignaciones.add(asignacionConID);
        return this.findById(asignacionConID.getId()).get();
    }

    @Override
    public Asignacion deleteById(String id) {
        val asignacion = this.findById(id);
        this.asignaciones.remove(asignacion.get());
        return asignacion.get();
    }

    @Override
    public Asignacion updateEstado(String asignacionID, EstadoAsginacionEnum estadoAsginacionEnum) {

        var asignacion  = this.findById(asignacionID).orElseThrow(() -> new AsignacionNoEncontrada( "No existe la asignación"));

        asignacion.setEstado(estadoAsginacionEnum);

        return asignacion;
    }
}
