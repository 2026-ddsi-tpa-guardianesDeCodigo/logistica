package ar.edu.utn.dds.k3003.exceptions;

public class DepositoYaExistenteException extends RuntimeException {
  public DepositoYaExistenteException(String mensaje) {
    super(mensaje);
  }
}
