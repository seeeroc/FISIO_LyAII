package fisio.ast;

/**
 * NodoIf(NodoAST): condicion: NodoAST, cuerpoIf: NodoBloque,
 * cuerpoElse: NodoAST opcional (null si no hay 'else'; puede ser un
 * NodoBloque para 'else { }' o un NodoIf anidado para 'else if ...').
 */
public record NodoIf(int linea, int columna, NodoAST condicion, NodoBloque cuerpoIf, NodoAST cuerpoElse)
        implements NodoAST {
}
