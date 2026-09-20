package fisio.gui;

import java.util.ArrayList;
import java.util.List;

import fisio.ast.NodoAST;
import fisio.ast.NodoAccesoArreglo;
import fisio.ast.NodoAgrupacion;
import fisio.ast.NodoAsignacion;
import fisio.ast.NodoBloque;
import fisio.ast.NodoCalculo;
import fisio.ast.NodoComando;
import fisio.ast.NodoDeclaracion;
import fisio.ast.NodoFor;
import fisio.ast.NodoFuncion;
import fisio.ast.NodoIdentificador;
import fisio.ast.NodoIf;
import fisio.ast.NodoInvocacionMacro;
import fisio.ast.NodoLlamadaFuncion;
import fisio.ast.NodoLoop;
import fisio.ast.NodoNumero;
import fisio.ast.NodoOperacion;
import fisio.ast.NodoOperacionRelacional;
import fisio.ast.NodoOperacionUnaria;
import fisio.ast.NodoParametro;
import fisio.ast.NodoParametroFuncion;
import fisio.ast.NodoPrograma;
import fisio.ast.NodoRango;
import fisio.ast.NodoReturn;
import fisio.ast.NodoVariableFisica;
import fisio.ast.NodoWhile;

/**
 * Traduccion directa de PanelSintactico._formatear_ast() (gui.py) a Java.
 *
 * Python recorre los nodos de forma generica via getattr() sobre nombres
 * de atributo fijos, en este orden exacto:
 *   - atributos "de un solo token" (se muestran entre parentesis en el
 *     encabezado): nombre, identificador, variable, operador, token, indice
 *   - listas de hijos (con encabezado propio "atributo:"): sentencias, argumentos
 *   - hijos unicos (con encabezado propio "atributo:"): lugar, expresion, izquierda, derecha
 *
 * Java no tiene getattr()/hasattr() sobre records, y no se debe modificar
 * el modelo AST (records congelados) para simular esa reflexion generica
 * ni para agregar un metodo visitor. En su lugar, esta clase hace un
 * despacho explicito por tipo (instanceof) que produce, para cada uno de
 * los tipos de nodo reales, EXACTAMENTE la misma salida que produciria
 * el algoritmo generico de Python sobre ese tipo (para los 12 records
 * originales de fisio.ast) o una salida analoga y consistente con ese
 * mismo criterio (para los nodos agregados en Fase 2 y en la revision
 * general Rust-like: NodoFuncion..NodoOperacionUnaria y
 * NodoRango/NodoLlamadaFuncion/NodoInvocacionMacro/NodoParametroFuncion).
 */
public final class FormateadorAst {

    private FormateadorAst() {
    }

    public static List<String> formatear(NodoAST nodo) {
        return formatear(nodo, 0);
    }

    private static List<String> formatear(NodoAST nodo, int nivel) {
        if (nodo == null) {
            return List.of("<sin AST>");
        }

        String indent = "  ".repeat(nivel);
        String nombre = nodo.getClass().getSimpleName();

        List<String> detalles = new ArrayList<>();
        List<NodoAST> hijosLista = null;
        String etiquetaLista = null;
        List<String> etiquetasUnicas = new ArrayList<>();
        List<NodoAST> hijosUnicos = new ArrayList<>();

        if (nodo instanceof NodoPrograma n) {
            hijosLista = n.sentencias();
            etiquetaLista = "sentencias";
        } else if (nodo instanceof NodoAsignacion n) {
            // Revision general Rust-like: n.operador() es "=" para la
            // asignacion simple o "+=", "-=", "*=", "/=", "%=" para las
            // compuestas (campo agregado; antes no existia).
            detalles.add("operador='" + n.operador().lexema() + "'");
            etiquetasUnicas.add("lugar");
            hijosUnicos.add(n.lugar());
            etiquetasUnicas.add("expresion");
            hijosUnicos.add(n.expresion());
        } else if (nodo instanceof NodoComando n) {
            detalles.add("nombre='" + n.nombre().lexema() + "'");
            hijosLista = n.argumentos();
            etiquetaLista = "argumentos";
        } else if (nodo instanceof NodoCalculo n) {
            detalles.add("nombre='" + n.nombre().lexema() + "'");
            etiquetasUnicas.add("expresion");
            hijosUnicos.add(n.expresion());
        } else if (nodo instanceof NodoParametro n) {
            detalles.add("variable='" + n.variable().lexema() + "'");
            etiquetasUnicas.add("expresion");
            hijosUnicos.add(n.expresion());
        } else if (nodo instanceof NodoOperacion n) {
            detalles.add("operador='" + n.operador().lexema() + "'");
            etiquetasUnicas.add("izquierda");
            hijosUnicos.add(n.izquierda());
            etiquetasUnicas.add("derecha");
            hijosUnicos.add(n.derecha());
        } else if (nodo instanceof NodoOperacionRelacional n) {
            detalles.add("operador='" + n.operador().lexema() + "'");
            etiquetasUnicas.add("izquierda");
            hijosUnicos.add(n.izquierda());
            etiquetasUnicas.add("derecha");
            hijosUnicos.add(n.derecha());
        } else if (nodo instanceof NodoAgrupacion n) {
            etiquetasUnicas.add("expresion");
            hijosUnicos.add(n.expresion());
        } else if (nodo instanceof NodoNumero n) {
            detalles.add("token='" + n.token().lexema() + "'");
        } else if (nodo instanceof NodoIdentificador n) {
            detalles.add("token='" + n.token().lexema() + "'");
        } else if (nodo instanceof NodoAccesoArreglo n) {
            detalles.add("identificador='" + n.identificador().lexema() + "'");
            detalles.add("indice='" + n.indice().lexema() + "'");
        } else if (nodo instanceof NodoVariableFisica n) {
            detalles.add("token='" + n.token().lexema() + "'");
        } else if (nodo instanceof NodoFuncion n) {
            detalles.add("nombre='" + n.nombre().lexema() + "'");
            // Revision general Rust-like: tipoRetorno (opcional, "-> tipo")
            // y parametros (antes se descartaban; ahora viven en el AST).
            if (n.tipoRetorno() != null) {
                detalles.add("tipoRetorno='" + n.tipoRetorno().lexema() + "'");
            }
            if (!n.parametros().isEmpty()) {
                hijosLista = new ArrayList<>(n.parametros());
                etiquetaLista = "parametros";
            }
            etiquetasUnicas.add("cuerpo");
            hijosUnicos.add(n.cuerpo());
        } else if (nodo instanceof NodoParametroFuncion n) {
            detalles.add("nombre='" + n.nombre().lexema() + "'");
            detalles.add("tipo='" + n.tipo().lexema() + "'");
        } else if (nodo instanceof NodoBloque n) {
            hijosLista = n.sentencias();
            etiquetaLista = "sentencias";
            // Revision general Rust-like: expresion final sin ';' (estilo
            // "fn f() -> i32 { a * b }"); null si el bloque no termina asi.
            if (n.expresionFinal() != null) {
                etiquetasUnicas.add("expresionFinal");
                hijosUnicos.add(n.expresionFinal());
            }
        } else if (nodo instanceof NodoRango n) {
            detalles.add("operador='" + (n.inclusivo() ? "..=" : "..") + "'");
            etiquetasUnicas.add("inicio");
            hijosUnicos.add(n.inicio());
            etiquetasUnicas.add("fin");
            hijosUnicos.add(n.fin());
        } else if (nodo instanceof NodoLlamadaFuncion n) {
            detalles.add("nombre='" + n.nombre().lexema() + "'");
            hijosLista = n.argumentos();
            etiquetaLista = "argumentos";
        } else if (nodo instanceof NodoInvocacionMacro n) {
            detalles.add("nombre='" + n.nombre().lexema() + "'");
            hijosLista = n.argumentos();
            etiquetaLista = "argumentos";
        } else if (nodo instanceof NodoDeclaracion n) {
            detalles.add("identificador='" + n.identificador().lexema() + "'");
            detalles.add("mutable=" + n.mutable());
            if (n.tipo() != null) {
                detalles.add("tipo='" + n.tipo().lexema() + "'");
            }
            etiquetasUnicas.add("expresion");
            hijosUnicos.add(n.expresion());
        } else if (nodo instanceof NodoIf n) {
            etiquetasUnicas.add("condicion");
            hijosUnicos.add(n.condicion());
            etiquetasUnicas.add("cuerpoIf");
            hijosUnicos.add(n.cuerpoIf());
            etiquetasUnicas.add("cuerpoElse");
            hijosUnicos.add(n.cuerpoElse());
        } else if (nodo instanceof NodoWhile n) {
            etiquetasUnicas.add("condicion");
            hijosUnicos.add(n.condicion());
            etiquetasUnicas.add("cuerpo");
            hijosUnicos.add(n.cuerpo());
        } else if (nodo instanceof NodoFor n) {
            detalles.add("variable='" + n.variable().lexema() + "'");
            etiquetasUnicas.add("iterable");
            hijosUnicos.add(n.iterable());
            etiquetasUnicas.add("cuerpo");
            hijosUnicos.add(n.cuerpo());
        } else if (nodo instanceof NodoLoop n) {
            etiquetasUnicas.add("cuerpo");
            hijosUnicos.add(n.cuerpo());
        } else if (nodo instanceof NodoReturn n) {
            etiquetasUnicas.add("expresion");
            hijosUnicos.add(n.expresion());
        } else if (nodo instanceof NodoOperacionUnaria n) {
            detalles.add("operador='" + n.operador().lexema() + "'");
            etiquetasUnicas.add("operando");
            hijosUnicos.add(n.operando());
        }

        StringBuilder encabezado = new StringBuilder(indent).append(nombre);
        if (!detalles.isEmpty()) {
            encabezado.append(" (").append(String.join(", ", detalles)).append(")");
        }

        List<String> lineas = new ArrayList<>();
        lineas.add(encabezado.toString());

        if (hijosLista != null && !hijosLista.isEmpty()) {
            lineas.add(indent + "  " + etiquetaLista + ":");
            for (NodoAST hijo : hijosLista) {
                lineas.addAll(formatear(hijo, nivel + 2));
            }
        }

        for (int i = 0; i < hijosUnicos.size(); i++) {
            NodoAST hijo = hijosUnicos.get(i);
            if (hijo != null) {
                lineas.add(indent + "  " + etiquetasUnicas.get(i) + ":");
                lineas.addAll(formatear(hijo, nivel + 2));
            }
        }

        return lineas;
    }
}
