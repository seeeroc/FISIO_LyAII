package fisio.token;

/** Diagnóstico léxico con el fragmento y la posición que lo originaron. */
public record ErrorLexico(String lexema, String mensaje, int linea, int columna) {
}
