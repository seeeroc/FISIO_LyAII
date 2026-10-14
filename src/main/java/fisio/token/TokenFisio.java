package fisio.token;

/** Token reconocido por el analizador léxico. */
public record TokenFisio(String lexema, CategoriaToken categoria, String idFisio, int linea, int columna) {
}
