package ar.edu.utn.dds.k3003.repositories;

import ar.edu.utn.dds.k3003.model.Paquete;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaPaquetesRepo extends JpaRepository<Paquete, Long> {
}
