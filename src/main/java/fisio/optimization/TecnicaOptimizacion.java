package fisio.optimization;

public enum TecnicaOptimizacion {
    ELIMINACION_COMENTARIOS("Eliminación de comentarios"),
    VARIABLE_NO_UTILIZADA("Eliminación de variable no utilizada"),
    CODIGO_INALCANZABLE("Eliminación de código inalcanzable"),
    PLEGADO_CONSTANTES("Plegado de constantes"),
    SIMPLIFICACION_ALGEBRAICA("Simplificación algebraica");

    private final String descripcion;

    TecnicaOptimizacion(String descripcion) {
        this.descripcion = descripcion;
    }

    public String descripcion() {
        return descripcion;
    }
}
