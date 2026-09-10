package fisio.ast;

import java.util.List;

/**
 * NodoBloque(NodoAST): sentencias: list[NodoAST], expresionFinal: NodoAST
 * opcional (null si el bloque no termina en una expresion sin ';').
 *
 * Fase 2 ({ ... }); ampliado en la revision general Rust-like para admitir
 * el estilo de Rust "ultima expresion sin punto y coma es el valor del
 * bloque" (p.ej. "fn f() -> i32 { a * b }"). Sin semantica de retorno: solo
 * se conserva la expresion final en el AST si existe.
 */
public record NodoBloque(int linea, int columna, List<NodoAST> sentencias, NodoAST expresionFinal)
        implements NodoAST {

    public NodoBloque {
        sentencias = List.copyOf(sentencias);
    }
}
