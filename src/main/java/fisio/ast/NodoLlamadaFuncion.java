package fisio.ast;

import java.util.List;

import fisio.token.TokenFisio;

/**
 * NodoLlamadaFuncion(NodoAST): nombre: TokenFisio, argumentos: list[NodoAST].
 *
 * Revision general (Rust-like): reconocimiento sintactico de "nombre(args)"
 * como expresion (p.ej. "multiplicar(contador, 5)"). No resuelve la funcion
 * ni valida aridad/tipos -- eso queda fuera de alcance (sin analisis
 * semantico).
 */
public record NodoLlamadaFuncion(int linea, int columna, TokenFisio nombre, List<NodoAST> argumentos)
        implements NodoAST {

    public NodoLlamadaFuncion {
        argumentos = List.copyOf(argumentos);
    }
}
