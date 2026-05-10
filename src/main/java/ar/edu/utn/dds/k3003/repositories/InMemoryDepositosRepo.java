package ar.edu.utn.dds.k3003.repositories;

import ar.edu.utn.dds.k3003.model.Deposito;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
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
    Deposito depositoConID = deposito;
    depositoConID.setId(String.valueOf(idSecuencial.getAndIncrement()));

    this.depositos.add(depositoConID);
    return this.findById(depositoConID.getId()).get();
  }

  @Override
  public Deposito deleteById(String id) {
    val deposito = this.findById(id);
    this.depositos.remove(deposito.get());
    return deposito.get();
  }
}
