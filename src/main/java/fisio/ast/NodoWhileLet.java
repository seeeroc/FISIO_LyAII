package fisio.ast;

/** Bucle `while let patrón = expresión`, con guarda booleana opcional. */
public record NodoWhileLet(int linea, int columna, PatronVariante patron, NodoAST expresion, NodoAST guarda,
        NodoBloque cuerpo) implements NodoAST {}
