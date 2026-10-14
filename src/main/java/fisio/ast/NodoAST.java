package fisio.ast;

/** Nodo base inmutable del AST del subconjunto Rust-like analizado por FISIO. */
public sealed interface NodoAST
    permits NodoPrograma, NodoAsignacion, NodoOperacion, NodoOperacionRelacional,
            NodoAgrupacion, NodoNumero, NodoIdentificador,
            NodoFuncion, NodoBloque, NodoDeclaracion, NodoIf, NodoWhile, NodoFor,
            NodoLoop, NodoReturn, NodoOperacionUnaria,
            NodoRango, NodoLlamadaFuncion, NodoInvocacionMacro, NodoParametroFuncion {

    int linea();

    int columna();
}
