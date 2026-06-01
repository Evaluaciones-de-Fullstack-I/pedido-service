package cl.duoc.pedidos.dto;

import cl.duoc.pedidos.model.EstadoPedido;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class PedidoResponseDTO {
    private Long id;
    private String numeroOrden;
    private Long clienteId;
    private Double montoTotal;
    private String direccionEnvio;
    private EstadoPedido estado;
    private LocalDateTime fechaCreacion;
}