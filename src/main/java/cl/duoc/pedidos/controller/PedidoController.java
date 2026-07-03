package cl.duoc.pedidos.controller;

import cl.duoc.pedidos.dto.PedidoRequestDTO;
import cl.duoc.pedidos.dto.PedidoResponseDTO;
import cl.duoc.pedidos.model.EstadoPedido;
import cl.duoc.pedidos.model.Pedido;
import cl.duoc.pedidos.repository.PedidoRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import cl.duoc.pedidos.dto.CarritoDTO;

@RestController
@RequestMapping("/api/pedidos")
@Tag(name = "Pedidos", description = "Controlador principal del flujo de checkout. Coordina la recolección del carrito, cobro automático y despacho mediante WebClient")
public class PedidoController {

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private WebClient.Builder webClientBuilder;

    @PostMapping
    @Operation(
        summary = "Generar un nuevo pedido (Checkout)",
        description = "Consulta el carrito de compras del cliente dinámicamente, calcula el total, crea la orden de compra local, procesa síncronamente el cobro en el MS Pagos y agenda el envío en el MS Delivery.",
        responses = {
            @ApiResponse(
                responseCode = "201", 
                description = "Pedido generado, pagado y enviado a reparto exitosamente"
            ),
            @ApiResponse(
                responseCode = "400", 
                description = "Parámetros de entrada incorrectos"
            ),
            @ApiResponse(
                responseCode = "500", 
                description = "Error interno o carrito vacío al intentar procesar la transacción"
            )
        }
    )
    public ResponseEntity<PedidoResponseDTO> crearPedido(
            @Valid @RequestBody(
                description = "Estructura JSON con los datos del cliente y la dirección de despacho requeridos para el checkout",
                required = true,
                content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = PedidoRequestDTO.class),
                    examples = @ExampleObject(
                        name = "Ejemplo de Checkout de Pedido",
                        value = "{\n  \"clienteId\": 45,\n  \"direccionEnvio\": \"Av. Concha y Toro 1340, Puente Alto\"\n}"
                    )
                )
            )
            @org.springframework.web.bind.annotation.RequestBody PedidoRequestDTO dto) {
        
        // 1. Obtención dinámica del carrito usando Eureka
        List<CarritoDTO> carrito = webClientBuilder.build().get()
            .uri("http://carrito-service/api/v1/carritos/cliente/" + dto.getClienteId())
            .retrieve()
            .bodyToFlux(CarritoDTO.class)
            .collectList()
            .block();
        System.out.println("🛒 Carrito obtenido: " + (carrito != null ? carrito.size() : 0) + " productos");

        if (carrito == null || carrito.isEmpty()) {
            throw new RuntimeException("El carrito está vacío");
        }
        System.out.println("✔ Carrito válido con " + carrito.size() + " productos");

        double total = carrito.stream()
                .mapToDouble(CarritoDTO::getSubtotal)
                .sum();   
        System.out.println("💰 Total calculado: " + total);

        Pedido pedido = new Pedido();
        pedido.setClienteId(dto.getClienteId());
        pedido.setMontoTotal(total);
        pedido.setDireccionEnvio(dto.getDireccionEnvio());
        pedido.setEstado(EstadoPedido.ESPERANDO_PAGO); 
        
        Pedido guardado = pedidoRepository.save(pedido);

        boolean pagoExitoso = false;
        try {
            Map<String, Object> pagoRequest = new HashMap<>();
            pagoRequest.put("pedidoId", guardado.getId());
            pagoRequest.put("monto", guardado.getMontoTotal()); 
            pagoRequest.put("metodo", "TARJETA"); 

            // 2. Enrutamiento dinámico al MS Pagos
            webClientBuilder.build().post()
                    .uri("http://pagos-service/api/pagos/procesar")
                    .bodyValue(pagoRequest)
                    .retrieve()
                    .bodyToMono(Void.class)
                    .block();

            System.out.println("💳 [CONEXIÓN] Pedido ID " + guardado.getId() + " enviado exitosamente a MS Pagos.");
            pagoExitoso = true; 
            
        } catch (Exception e) {
            System.out.println("❌ [ERROR] No se pudo enviar el cobro a Pagos: " + e.getMessage());
        }

        if (pagoExitoso) {
            try {
                Map<String, Object> deliveryRequest = new HashMap<>();
                deliveryRequest.put("pedidoId", guardado.getId().intValue()); 
                deliveryRequest.put("nombreRepartidor", "Por Asignar");
                deliveryRequest.put("direccionEntrega", guardado.getDireccionEnvio());
                deliveryRequest.put("fechaDespacho", LocalDate.now().toString()); 
                deliveryRequest.put("fechaEntrega", LocalDate.now().plusDays(1).toString());

                // 3. Enrutamiento dinámico al MS Delivery
                webClientBuilder.build().post()
                        .uri("http://delivery-service/api/v1/delivery")
                        .bodyValue(deliveryRequest)
                        .retrieve()
                        .bodyToMono(Void.class)
                        .block();

                System.out.println("🚚 [CONEXIÓN] Pedido ID " + guardado.getId() + " enviado a MS Delivery para despacho.");
                
                guardado.setEstado(EstadoPedido.PAGADO);
                pedidoRepository.save(guardado);
                
            } catch (Exception e) {
                System.out.println("❌ [ERROR] No se pudo notificar a MS Delivery: " + e.getMessage());
            }
        }
        
        return new ResponseEntity<>(convertirADto(guardado), HttpStatus.CREATED);
    }

    @GetMapping("/cliente/{clienteId}")
    @Operation(
        summary = "Consultar historial de pedidos de un cliente",
        description = "Retorna el listado completo de todas las órdenes de compra efectuadas por un usuario específico.",
        responses = {
            @ApiResponse(
                responseCode = "200", 
                description = "Listado de pedidos devuelto con éxito"
            )
        }
    )
    public ResponseEntity<List<PedidoResponseDTO>> obtenerPedidosCliente(@PathVariable Long clienteId) {
        List<Pedido> pedidos = pedidoRepository.findByClienteId(clienteId);
        List<PedidoResponseDTO> response = pedidos.stream()
                .map(this::convertirADto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/estado")
    @Operation(
        summary = "Actualizar el estado de un pedido",
        description = "Modifica el estado logístico o financiero del pedido. Endpoint diseñado para ser invocado externamente por el MS de Pagos o el MS de Delivery.",
        responses = {
            @ApiResponse(
                responseCode = "200", 
                description = "Estado del pedido actualizado exitosamente"
            ),
            @ApiResponse(
                responseCode = "404", 
                description = "No se localizó un pedido con el ID entregado"
            )
        }
    )
    public ResponseEntity<PedidoResponseDTO> actualizarEstadoPedido(
            @PathVariable Long id, 
            @RequestParam EstadoPedido nuevoEstado) {
        
        Pedido pedido = pedidoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Pedido no encontrado"));

        pedido.setEstado(nuevoEstado);
        Pedido actualizado = pedidoRepository.save(pedido);

        return ResponseEntity.ok(convertirADto(actualizado));
    }

    private PedidoResponseDTO convertirADto(Pedido pedido) {
        PedidoResponseDTO dto = new PedidoResponseDTO();
        dto.setId(pedido.getId());
        dto.setNumeroOrden(pedido.getNumeroOrden());
        dto.setClienteId(pedido.getClienteId());
        dto.setMontoTotal(pedido.getMontoTotal());
        dto.setDireccionEnvio(pedido.getDireccionEnvio());
        dto.setEstado(pedido.getEstado());
        dto.setFechaCreacion(pedido.getFechaCreacion());
        return dto;
    }
}