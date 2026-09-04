package fisio.ast;

/**
 * Equivalente de la clase base NodoAST en parser.py
 * (@dataclass(frozen=True, slots=True), con linea/columna como campos
 * almacenados, heredados posicionalmente por cada subclase).
 *
 * Se modela como sealed interface (no clase abstracta) para que cada nodo
 * concreto sea un record: inmutabilidad, igualdad estructural y toString
 * razonable "gratis", sin boilerplate manual, y sin poder existir ningun
 * nodo fuera de los 12 que declara parser.py (permits). linea/columna se
 * repiten como componente en cada record en vez de heredarse de una clase
 * base -Java no permite herencia de estado entre records- pero eso refleja
 * fielmente el __init__ generado por la dataclass de Python, que tambien
 * antepone linea/columna a los campos propios de cada subclase.
 */
public sealed interface NodoAST
    permits NodoPrograma, NodoAsignacion, NodoComando, NodoCalculo, NodoParametro,
            NodoOperacion, NodoOperacionRelacional, NodoAgrupacion, NodoNumero,
            NodoIdentificador, NodoAccesoArreglo, NodoVariableFisica {

    int linea();

    int columna();
}
