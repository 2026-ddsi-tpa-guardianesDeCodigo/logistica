package ar.edu.utn.dds.k3003.repositories;

import ar.edu.utn.dds.k3003.model.Deposito;
import ar.edu.utn.dds.k3003.model.Paquete;

import java.util.List;
import java.util.Optional;

public interface DepositosRepository {
  Optional<Deposito> findById(String id);

  Deposito save(Deposito deposito);

  Deposito deleteById(String id);

  List<Deposito> findAll();
}
