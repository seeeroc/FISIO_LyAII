package fisio.semantic;

/** Diagnostico de usuario; posiciones base 1, sin excepciones de control. */
public record ErrorSemantico(String codigo, String mensaje, int linea, int columna) {
}
