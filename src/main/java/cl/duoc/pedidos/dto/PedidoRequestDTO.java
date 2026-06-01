package cl.duoc.pedidos.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PedidoRequestDTO {
    
    @NotNull(message = "El ID del cliente es obligatorio")
    private Long clienteId;

    @NotNull(message = "El monto total es obligatorio")
    @Min(value = 1, message = "El monto debe ser mayor a 0")
    private Double montoTotal;

    @NotBlank(message = "La dirección de envío es obligatoria")
    private String direccionEnvio;
}