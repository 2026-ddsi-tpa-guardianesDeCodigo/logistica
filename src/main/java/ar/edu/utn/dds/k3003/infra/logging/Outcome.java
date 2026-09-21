package ar.edu.utn.dds.k3003.infra.logging;

public enum Outcome {
    SUCCESS, FAILURE, DEGRADED;

    public String value() {
        return name().toLowerCase();
    }
}
