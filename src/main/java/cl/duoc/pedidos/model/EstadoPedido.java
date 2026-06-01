package cl.duoc.pedidos.model;

public enum EstadoPedido {
    CREADO,
    ESPERANDO_PAGO,
    PAGADO,
    EN_PREPARACION,
    EN_DESPACHO,
    ENTREGADO,
    CANCELADO
}