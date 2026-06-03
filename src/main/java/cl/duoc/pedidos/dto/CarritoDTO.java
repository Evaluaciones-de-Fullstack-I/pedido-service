package cl.duoc.pedidos.dto;

public class CarritoDTO {

    private Integer productoId;
    private Integer cantidad;
    private Double subtotal;


  public CarritoDTO() {}

    public Integer getProductoId() { return productoId; }
    public void setProductoId(Integer productoId) { this.productoId = productoId; }

    public Integer getCantidad() { return cantidad; }
    public void setCantidad(Integer cantidad) { this.cantidad = cantidad; }

    public Double getSubtotal() { return subtotal; }
    public void setSubtotal(Double subtotal) { this.subtotal = subtotal; }


}
