package ar.edu.utn.dds.k3003.model;

import ar.edu.utn.dds.k3003.catedra.dtos.logistica.EstadoAsginacionEnum;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.OrigenAsignacionEnum;
import jakarta.persistence.*;
import ar.edu.utn.dds.k3003.eventos.PublicadorDeCambios;
import java.time.Instant;
import java.time.LocalDateTime;

@Entity
@EntityListeners(PublicadorDeCambios.class)
@Table(name = "asignaciones")
public class Asignacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Versión optimista (JPA). Viaja en cada evento del modelo de lectura (E6): el consumidor solo
    // aplica snapshots con versión mayor a la que ya tiene. El default de columna cubre las filas
    // existentes cuando ddl-auto=update agrega la columna.
    @Version
    @Column(name = "version", columnDefinition = "bigint default 0")
    private Long version;

    private String paqueteID;
    private String necesidadID;
    private LocalDateTime fecha;

    // Depósito de donde sale lo asignado. No lo sabe el paquete asignado (nace sin depósito), y el
    // modelo de lectura lo necesita para el estado de cada depósito.
    @Column(name = "deposito_id")
    private String depositoID;

    // Momento en que se reportó la entrega; `fecha` es el de la creación. Para medir tiempos.
    @Column(name = "completada_en")
    private Instant completadaEn;

    @Enumerated(EnumType.STRING)
    private EstadoAsginacionEnum estado;

    @Enumerated(EnumType.STRING)
    private OrigenAsignacionEnum origen;

    public Asignacion() {}

    public Asignacion(
            String paqueteID,
            String necesidadID,
            LocalDateTime fecha,
            EstadoAsginacionEnum estado,
            OrigenAsignacionEnum origen) {
        this.paqueteID = paqueteID;
        this.necesidadID = necesidadID;
        this.fecha = fecha;
        this.estado = estado;
        this.origen = origen;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPaqueteID() { return paqueteID; }
    public void setPaqueteID(String paqueteID) { this.paqueteID = paqueteID; }
    public String getNecesidadID() { return necesidadID; }
    public void setNecesidadID(String necesidadID) { this.necesidadID = necesidadID; }
    public LocalDateTime getFecha() { return fecha; }
    public void setFecha(LocalDateTime fecha) { this.fecha = fecha; }
    public EstadoAsginacionEnum getEstado() { return estado; }
    public void setEstado(EstadoAsginacionEnum estado) {
        this.estado = estado;
        if (estado == EstadoAsginacionEnum.COMPLETADA && completadaEn == null) {
            this.completadaEn = Instant.now();
        }
    }
    public Long getVersion() { return version; }
    public String getDepositoID() { return depositoID; }
    public void setDepositoID(String depositoID) { this.depositoID = depositoID; }
    public Instant getCompletadaEn() { return completadaEn; }
    public OrigenAsignacionEnum getOrigen() { return origen; }
    public void setOrigen(OrigenAsignacionEnum origen) { this.origen = origen; }
}
