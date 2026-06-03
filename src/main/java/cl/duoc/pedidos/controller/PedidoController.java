package cl.duoc.pedidos.controller;

import cl.duoc.pedidos.dto.PedidoRequestDTO;
import cl.duoc.pedidos.dto.PedidoResponseDTO;
import cl.duoc.pedidos.model.EstadoPedido;
import cl.duoc.pedidos.model.Pedido;
import cl.duoc.pedidos.repository.PedidoRepository;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDate; // NUEVO IMPORT PARA LAS FECHAS DE ELI
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import cl.duoc.pedidos.dto.CarritoDTO;

@RestController
@RequestMapping("/api/pedidos")
public class PedidoController {

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private WebClient.Builder webClientBuilder;

    // Generar la orden de compra, cobrar y despachar
    @PostMapping
    public ResponseEntity<PedidoResponseDTO> crearPedido(@Valid @RequestBody PedidoRequestDTO dto) {
        
     List<CarritoDTO> carrito = webClientBuilder.build().get()
        .uri("http://localhost:8086/api/v1/carritos/cliente/" + dto.getClienteId())
        .retrieve()
        .bodyToFlux(CarritoDTO.class)
        .collectList()
        .block();
System.out.println("🛒 Carrito obtenido: " + carrito.size() + " productos");

if (carrito == null || carrito.isEmpty()) {
   
    throw new RuntimeException("El carrito está vacío");
}
System.out.println("✔ Carrito válido con " + carrito.size() + " productos");

double total = carrito.stream()
        .mapToDouble(CarritoDTO::getSubtotal)
        .sum();   
System.out.println("💰 Total calculado: " + total);
        // 1. Creamos y guardamos el pedido localmente
        Pedido pedido = new Pedido();
        pedido.setClienteId(dto.getClienteId());
      //  pedido.setMontoTotal(dto.getMontoTotal());
        pedido.setMontoTotal(total);
        pedido.setDireccionEnvio(dto.getDireccionEnvio());
        pedido.setEstado(EstadoPedido.ESPERANDO_PAGO); 
        
        Pedido guardado = pedidoRepository.save(pedido);

        // 2. CONEXIÓN AUTOMÁTICA CON MICROSERVICIO DE PAGOS
        boolean pagoExitoso = false;
        try {
            Map<String, Object> pagoRequest = new HashMap<>();
            pagoRequest.put("pedidoId", guardado.getId());
            pagoRequest.put("monto", guardado.getMontoTotal()); 
            pagoRequest.put("metodo", "TARJETA"); 

            webClientBuilder.build().post()
                    .uri("http://localhost:8088/api/pagos/procesar")
                    .bodyValue(pagoRequest)
                    .retrieve()
                    .bodyToMono(Void.class)
                    .block();

            System.out.println("💳 [CONEXIÓN] Pedido ID " + guardado.getId() + " enviado exitosamente a MS Pagos.");
            pagoExitoso = true; // El pago pasó, podemos despachar
            
        } catch (Exception e) {
            System.out.println("❌ [ERROR] No se pudo enviar el cobro a Pagos: " + e.getMessage());
        }

        // 3. CONEXIÓN AUTOMÁTICA CON MICROSERVICIO DE DELIVERY (Solo si se pagó)
        if (pagoExitoso) {
            try {
                // Armamos los datos EXACTOS que Eli pide en su CreateRequestDelivery record
                Map<String, Object> deliveryRequest = new HashMap<>();
                deliveryRequest.put("pedidoId", guardado.getId().intValue()); // Convertimos a Integer por si acaso
                deliveryRequest.put("nombreRepartidor", "Por Asignar");
                deliveryRequest.put("direccionEntrega", guardado.getDireccionEnvio());
                deliveryRequest.put("fechaDespacho", LocalDate.now().toString()); // ISO-8601 String que Jackson entiende perfecto
                deliveryRequest.put("fechaEntrega", LocalDate.now().plusDays(1).toString());

                webClientBuilder.build().post()
                        .uri("http://localhost:8084/api/v1/delivery")
                        .bodyValue(deliveryRequest)
                        .retrieve()
                        .bodyToMono(Void.class)
                        .block();

                System.out.println("🚚 [CONEXIÓN] Pedido ID " + guardado.getId() + " enviado a MS Delivery para despacho.");
                
                // Actualizamos el estado de nuestro pedido a PAGADO ya que todo salió bien
                guardado.setEstado(EstadoPedido.PAGADO);
                pedidoRepository.save(guardado);
                
            } catch (Exception e) {
                System.out.println("❌ [ERROR] No se pudo notificar a MS Delivery: " + e.getMessage());
            }
        }
        
        return new ResponseEntity<>(convertirADto(guardado), HttpStatus.CREATED);
    }

    // Consultar pedidos de un cliente
    @GetMapping("/cliente/{clienteId}")
    public ResponseEntity<List<PedidoResponseDTO>> obtenerPedidosCliente(@PathVariable Long clienteId) {
        List<Pedido> pedidos = pedidoRepository.findByClienteId(clienteId);
        List<PedidoResponseDTO> response = pedidos.stream()
                .map(this::convertirADto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    // Endpoint para que MS Pagos o MS Delivery actualicen el estado
    @PutMapping("/{id}/estado")
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