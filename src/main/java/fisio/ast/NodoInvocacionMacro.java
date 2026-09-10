package fisio.ast;

import java.util.List;

import fisio.token.TokenFisio;

/**
 * NodoInvocacionMacro(NodoAST): nombre: TokenFisio, argumentos: list[NodoAST].
 *
 * Revision general (Rust-like): reconocimiento sintactico minimo de una
 * invocacion de macro tipo "println!(...)" -- forma general
 * "identificador ! ( [argumentos] ) ;". "println" NO se vuelve palabra
 * reservada: el lexer lo sigue viendo como ID normal; es el PARSER el que
 * reconoce la secuencia ID "!" "(" ... ")" ";" y construye este nodo. No
 * implementa el sistema real de macros de Rust (sin macro_rules!, sin
 * expansion): solo reconocimiento sintactico + AST.
 */
public record NodoInvocacionMacro(int linea, int columna, TokenFisio nombre, List<NodoAST> argumentos)
        implements NodoAST {

    public NodoInvocacionMacro {
        argumentos = List.copyOf(argumentos);
    }
}
