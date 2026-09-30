package ar.edu.utn.dds.k3003.repositories;

import ar.edu.utn.dds.k3003.catedra.dtos.logistica.EstadoAsginacionEnum;
import ar.edu.utn.dds.k3003.model.Asignacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
@Transactional
public interface JpaAsignacionesRepo extends JpaRepository<Asignacion, Long> {

    Optional<Asignacion> findByPaqueteID(String id);

    List<Asignacion> findByEstado(EstadoAsginacionEnum estado);

}