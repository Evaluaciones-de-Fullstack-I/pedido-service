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

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/pedidos")
public class PedidoController {

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private WebClient.Builder webClientBuilder;

    // Generar la orden de compra y mandar a cobrar
    @PostMapping
    public ResponseEntity<PedidoResponseDTO> crearPedido(@Valid @RequestBody PedidoRequestDTO dto) {
        // 1. Creamos y guardamos el pedido localmente
        Pedido pedido = new Pedido();
        pedido.setClienteId(dto.getClienteId());
        pedido.setMontoTotal(dto.getMontoTotal());
        pedido.setDireccionEnvio(dto.getDireccionEnvio());
        pedido.setEstado(EstadoPedido.ESPERANDO_PAGO); 
        
        Pedido guardado = pedidoRepository.save(pedido);

        // 2. CONEXIÓN AUTOMÁTICA CON MICROSERVICIO DE PAGOS
        try {
            // Preparamos los datos mínimos que Pagos necesita saber
            Map<String, Object> pagoRequest = new HashMap<>();
            pagoRequest.put("pedidoId", guardado.getId());
            pagoRequest.put("montoTotal", guardado.getMontoTotal());

            // Enviamos la petición POST al servicio de Pagos
            // NOTA: Si usas Eureka/Naming Server, cambia "localhost:8085" por "pago-service" (o como se llame tu MS)
            webClientBuilder.build().post()
                    .uri("http://localhost:8088/api/pagos") 
                    .bodyValue(pagoRequest)
                    .retrieve()
                    .bodyToMono(Void.class)
                    .block(); // Espera a que la llamada se complete

            System.out.println("💳 [CONEXIÓN] Pedido ID " + guardado.getId() + " enviado exitosamente a MS Pagos.");
            
        } catch (Exception e) {
            System.out.println("❌ [ERROR] No se pudo enviar el cobro a Pagos: " + e.getMessage());
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