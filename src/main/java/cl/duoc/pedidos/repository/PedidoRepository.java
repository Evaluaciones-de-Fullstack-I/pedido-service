package cl.duoc.pedidos.repository;

import cl.duoc.pedidos.model.Pedido;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PedidoRepository extends JpaRepository<Pedido, Long> {
    List<Pedido> findByClienteId(Long clienteId);
    Optional<Pedido> findByNumeroOrden(String numeroOrden);
}