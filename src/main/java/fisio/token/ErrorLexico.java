package fisio.token;

/**
 * Equivalente funcional del dataclass ErrorLexico de token_types.py
 * (lexema, mensaje, linea, columna).
 */
public record ErrorLexico(String lexema, String mensaje, int linea, int columna) {
}
