package ar.edu.utn.dds.k3003.repositories;

import ar.edu.utn.dds.k3003.model.Paquete;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface JpaPaquetesRepo extends JpaRepository<Paquete, Long> {
    List<Paquete> findByProductoAndDepositoIsNotNullOrderByIdAsc(String producto);

    // delete(entity) es un no-op cuando el paquete todavia esta referenciado por la
    // coleccion stockActual de su Deposito (cascade=ALL sin orphanRemoval). Bulk delete
    // por ID evita el problema porque no depende del grafo de entidades cargado.
    @Transactional
    @Modifying
    @Query("delete from Paquete p where p.id = :id")
    void eliminarPorId(@Param("id") Long id);
}
