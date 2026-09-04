package fisio.token;

/**
 * Representacion de dominio de un token FISIO valido, funcionalmente
 * equivalente al dataclass Token de token_types.py (lexema, tipo, id_tok,
 * linea, columna).
 */
public record TokenFisio(String lexema, CategoriaToken categoria, String idFisio, int linea, int columna) {
}
