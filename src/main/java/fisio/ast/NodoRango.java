package fisio.ast;

/**
 * NodoRango(NodoAST): inicio: NodoAST, fin: NodoAST.
 *
 * Revision general (Rust-like): reconocimiento sintactico de "inicio..fin"
 * (p.ej. "0..10" o "a..b" en un "for x in ..."). Sin semantica de
 * Range/Iterator real de Rust -- solo se conserva la estructura en el AST.
 */
public record NodoRango(int linea, int columna, NodoAST inicio, NodoAST fin) implements NodoAST {
}
