package ar.edu.utn.dds.k3003.repositories;

import ar.edu.utn.dds.k3003.catedra.dtos.logistica.EstadoAsginacionEnum;
import ar.edu.utn.dds.k3003.model.Asignacion;

import java.util.Optional;

public interface AsignacionesRepository {

    Optional<Asignacion> findById(String id);

    Optional<Asignacion> findByPaqueteId(String id);

    Asignacion save(Asignacion asignacion);

    Asignacion deleteById(String id);

    Asignacion updateEstado(String asignacionID, EstadoAsginacionEnum estadoAsginacionEnum);

}
