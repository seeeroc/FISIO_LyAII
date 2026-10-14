package fisio.gui;

import java.util.ArrayList;
import java.util.List;

import fisio.ast.NodoAST;
import fisio.ast.NodoAgrupacion;
import fisio.ast.NodoAsignacion;
import fisio.ast.NodoBloque;
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
import fisio.ast.NodoParametroFuncion;
import fisio.ast.NodoPrograma;
import fisio.ast.NodoRango;
import fisio.ast.NodoReturn;
import fisio.ast.NodoWhile;

/** Convierte el AST en líneas indentadas para el panel sintáctico. */
public final class FormateadorAst {

    private FormateadorAst() {
    }

    public static List<String> formatear(NodoAST nodo) {
        List<String> lineas = new ArrayList<>();
        formatear(nodo, 0, lineas);
        return lineas;
    }

    private static void formatear(NodoAST nodo, int nivel, List<String> lineas) {
        if (nodo == null) {
            lineas.add("<sin AST>");
            return;
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
            detalles.add("operador='" + n.operador().lexema() + "'");
            etiquetasUnicas.add("lugar");
            hijosUnicos.add(n.lugar());
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
        } else if (nodo instanceof NodoFuncion n) {
            detalles.add("nombre='" + n.nombre().lexema() + "'");
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

        lineas.add(encabezado.toString());

        if (hijosLista != null && !hijosLista.isEmpty()) {
            lineas.add(indent + "  " + etiquetaLista + ":");
            for (NodoAST hijo : hijosLista) {
                formatear(hijo, nivel + 2, lineas);
            }
        }

        for (int i = 0; i < hijosUnicos.size(); i++) {
            NodoAST hijo = hijosUnicos.get(i);
            if (hijo != null) {
                lineas.add(indent + "  " + etiquetasUnicas.get(i) + ":");
                formatear(hijo, nivel + 2, lineas);
            }
        }
    }
}
