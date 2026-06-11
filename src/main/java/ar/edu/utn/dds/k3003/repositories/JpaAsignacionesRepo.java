package ar.edu.utn.dds.k3003.repositories;

import ar.edu.utn.dds.k3003.catedra.dtos.logistica.EstadoAsginacionEnum;
import ar.edu.utn.dds.k3003.exceptions.AsignacionNoEncontrada;
import ar.edu.utn.dds.k3003.model.Asignacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
@Transactional
public interface JpaAsignacionesRepo extends JpaRepository<Asignacion, String> {
    Optional<Asignacion> findByPaqueteID(String id);

    default Asignacion updateEstado(String asignacionID, EstadoAsginacionEnum estado) {
        Asignacion asignacion = findById(asignacionID)
                .orElseThrow(() -> new AsignacionNoEncontrada("No existe la asignación"));
        asignacion.setEstado(estado);
        return save(asignacion);
    }
}