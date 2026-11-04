package fisio.ast;

/** Nodo base inmutable del AST del subconjunto Rust-like analizado por FISIO. */
public sealed interface NodoAST
    permits NodoPrograma, NodoAsignacion, NodoOperacion, NodoOperacionRelacional,
            NodoAgrupacion, NodoNumero, NodoIdentificador,
            NodoFuncion, NodoBloque, NodoDeclaracion, NodoLetElse, NodoIf, NodoIfLet, NodoWhile, NodoWhileLet, NodoFor,
            NodoLoop, NodoReturn, NodoBreak, NodoContinue, NodoOperacionUnaria, NodoConversion,
            NodoRango, NodoLlamadaFuncion, NodoInvocacionMacro, NodoParametroFuncion,
            NodoStruct, NodoEnum, NodoLiteralStruct, NodoLiteralVariante, NodoAccesoCampo, NodoVarianteEnum, NodoMatch,
            NodoArreglo, NodoTupla, NodoIndice, NodoAccesoTupla, NodoConstante, NodoAliasTipo,
            NodoEstatico, NodoReferencia, NodoDesreferencia, NodoImpl,
            NodoLlamadaAsociada, NodoLlamadaMetodo, NodoTrait, NodoFirmaTrait {

    int linea();

    int columna();
}
