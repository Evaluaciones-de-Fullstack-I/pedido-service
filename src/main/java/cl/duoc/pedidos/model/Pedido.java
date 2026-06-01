package cl.duoc.pedidos.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "pedidos")
public class Pedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String numeroOrden;

    @Column(nullable = false)
    private Long clienteId;

    @Column(nullable = false)
    private Double montoTotal;

    @Column(nullable = false)
    private String direccionEnvio;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoPedido estado;

    @Column(nullable = false)
    private LocalDateTime fechaCreacion;

    @PrePersist
    protected void onCreate() {
        this.fechaCreacion = LocalDateTime.now();
        this.numeroOrden = "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        if (this.estado == null) {
            this.estado = EstadoPedido.CREADO;
        }
    }
}