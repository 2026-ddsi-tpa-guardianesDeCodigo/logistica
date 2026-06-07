package ar.edu.utn.dds.k3003.repositories;

import ar.edu.utn.dds.k3003.model.Deposito;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JpaDepositosRepo extends JpaRepository<Deposito, String> {
    // findById, save, deleteById, findAll ya vienen de JpaRepository
}