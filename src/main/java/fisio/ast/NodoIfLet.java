package fisio.ast;

/** Condicional `if let patrón = expresión`, con guarda booleana y else opcionales. */
public record NodoIfLet(int linea, int columna, PatronVariante patron, NodoAST expresion, NodoAST guarda,
        NodoBloque cuerpoIf, NodoAST cuerpoElse) implements NodoAST {}
