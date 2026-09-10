package fisio.ast;

import fisio.token.TokenFisio;

/**
 * NodoParametroFuncion(NodoAST): nombre: TokenFisio, tipo: TokenFisio.
 *
 * Revision general (Rust-like): un parametro de una declaracion de funcion
 * ("fn f(nombre: tipo)"). "tipo" se guarda tal cual (un ID cualquiera, p.ej.
 * i32/f64/bool/String) sin validacion semantica -- igual criterio que el
 * "tipo" opcional de NodoDeclaracion.
 *
 * No confundir con el NodoParametro heredado del FISIO de fisica (variable
 * + expresion, para llamadas tipo "mru(velocidad := 5)"): representan
 * conceptos distintos y ambos se conservan.
 */
public record NodoParametroFuncion(int linea, int columna, TokenFisio nombre, TokenFisio tipo) implements NodoAST {
}
