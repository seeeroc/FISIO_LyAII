package fisio.ast;

/**
 * Declaración refutable {@code let Patron = expresion else { ... };}.
 * Las vinculaciones del patrón permanecen en el ámbito que contiene la sentencia.
 */
public record NodoLetElse(int linea, int columna, PatronVariante patron,
        NodoAST expresion, NodoBloque cuerpoElse) implements NodoAST {
}
