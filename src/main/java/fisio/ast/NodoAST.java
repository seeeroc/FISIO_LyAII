package fisio.ast;

/**
 * Equivalente de la clase base NodoAST en parser.py
 * (@dataclass(frozen=True, slots=True), con linea/columna como campos
 * almacenados, heredados posicionalmente por cada subclase).
 *
 * Se modela como sealed interface (no clase abstracta) para que cada nodo
 * concreto sea un record: inmutabilidad, igualdad estructural y toString
 * razonable "gratis", sin boilerplate manual. Los primeros 12 nodos
 * (permits originales) vienen del FISIO de fisica; los siguientes 9
 * (NodoFuncion..NodoOperacionUnaria) se agregaron en la Fase 2 (sentencias
 * de control y declaraciones estilo Rust); los ultimos 4 (NodoRango en
 * adelante) se agregaron en la revision general Rust-like (rangos,
 * llamadas a funcion, macros tipo println! y parametros de funcion). Ningun
 * grupo reemplaza ni renombra a los anteriores. linea/columna se repiten
 * como componente en cada record en vez de heredarse de una clase base
 * -Java no permite herencia de estado entre records.
 */
public sealed interface NodoAST
    permits NodoPrograma, NodoAsignacion, NodoComando, NodoCalculo, NodoParametro,
            NodoOperacion, NodoOperacionRelacional, NodoAgrupacion, NodoNumero,
            NodoIdentificador, NodoAccesoArreglo, NodoVariableFisica,
            NodoFuncion, NodoBloque, NodoDeclaracion, NodoIf, NodoWhile, NodoFor,
            NodoLoop, NodoReturn, NodoOperacionUnaria,
            NodoRango, NodoLlamadaFuncion, NodoInvocacionMacro, NodoParametroFuncion {

    int linea();

    int columna();
}
