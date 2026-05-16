package ar.edu.utn.dds.k3003.repositories;

import ar.edu.utn.dds.k3003.model.Deposito;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import ar.edu.utn.dds.k3003.model.Paquete;
import lombok.val;

public class InMemoryDepositosRepo implements DepositosRepository {

  private List<Deposito> depositos;
  private AtomicLong idSecuencial = new AtomicLong(1);

  public InMemoryDepositosRepo() {
    this.depositos = new ArrayList<>();
  }

  @Override
  public Optional<Deposito> findById(String id) {
    return this.depositos.stream().filter(d -> d.getId().equals(id)).findFirst();
  }

  @Override
  public Deposito save(Deposito deposito) {
    if (deposito.getId() == null) {
      deposito.setId(String.valueOf(idSecuencial.getAndIncrement()));
      this.depositos.add(deposito);
    } else {
      if (!this.depositos.contains(deposito)) {
        this.depositos.add(deposito);
      }
    }
    return deposito;
  }

  @Override
  public Deposito deleteById(String id) {
    val deposito = this.findById(id);
    this.depositos.remove(deposito.get());
    return deposito.get();
  }

  @Override
  public List<Deposito> findAll() {
    return new ArrayList<>(depositos);
  }

}
