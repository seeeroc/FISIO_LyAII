package fisio.gui;

import java.util.ArrayList;
import java.util.List;

import fisio.ast.NodoAST;
import fisio.ast.NodoAgrupacion;
import fisio.ast.NodoAsignacion;
import fisio.ast.NodoBloque;
import fisio.ast.NodoBreak;
import fisio.ast.NodoContinue;
import fisio.ast.NodoConversion;
import fisio.ast.NodoStruct;
import fisio.ast.NodoEnum;
import fisio.ast.NodoLiteralStruct;
import fisio.ast.NodoLiteralVariante;
import fisio.ast.NodoAccesoCampo;
import fisio.ast.NodoVarianteEnum;
import fisio.ast.NodoMatch;
import fisio.ast.NodoArreglo;
import fisio.ast.NodoTupla;
import fisio.ast.NodoIndice;
import fisio.ast.NodoAccesoTupla;
import fisio.ast.NodoConstante;
import fisio.ast.NodoAliasTipo;
import fisio.ast.NodoEstatico;
import fisio.ast.NodoReferencia;
import fisio.ast.NodoDesreferencia;
import fisio.ast.NodoImpl;
import fisio.ast.NodoLlamadaAsociada;
import fisio.ast.NodoLlamadaMetodo;
import fisio.ast.NodoTrait;
import fisio.ast.NodoFirmaTrait;
import fisio.ast.NodoDeclaracion;
import fisio.ast.NodoLetElse;
import fisio.ast.NodoFor;
import fisio.ast.NodoFuncion;
import fisio.ast.NodoIdentificador;
import fisio.ast.NodoIf;
import fisio.ast.NodoIfLet;
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
import fisio.ast.NodoWhileLet;
import fisio.ast.PatronVariante;

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
        } else if (nodo instanceof NodoImpl n) {
            detalles.add("tipo='" + n.tipo().lexema() + "'");
            if (n.rasgo() != null) detalles.add("trait='" + n.rasgo().lexema() + "'");
            hijosLista = new ArrayList<>(n.metodos());
            etiquetaLista = "metodos";
        } else if (nodo instanceof NodoTrait n) {
            detalles.add("nombre='" + n.nombre().lexema() + "'");
            detalles.add("publico=" + n.publico());
            hijosLista = new ArrayList<>(n.metodos());
            etiquetaLista = "firmas";
        } else if (nodo instanceof NodoFirmaTrait n) {
            detalles.add("nombre='" + n.nombre().lexema() + "'");
            if (n.tipoRetorno() != null) detalles.add("tipoRetorno='" + n.tipoRetorno().lexema() + "'");
            hijosLista = new ArrayList<>(n.parametros());
            etiquetaLista = "parametros";
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
            detalles.add("publico=" + n.publico());
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
        } else if (nodo instanceof NodoLlamadaAsociada n) {
            detalles.add("tipo='" + n.tipo().lexema() + "'");
            detalles.add("nombre='" + n.nombre().lexema() + "'");
            hijosLista = n.argumentos();
            etiquetaLista = "argumentos";
        } else if (nodo instanceof NodoLlamadaMetodo n) {
            detalles.add("nombre='" + n.nombre().lexema() + "'");
            etiquetasUnicas.add("receptor");
            hijosUnicos.add(n.receptor());
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
        } else if (nodo instanceof NodoLetElse n) {
            detalles.add("patron='" + formatearPatron(n.patron()) + "'");
            etiquetasUnicas.add("expresion");
            hijosUnicos.add(n.expresion());
            etiquetasUnicas.add("cuerpoElse");
            hijosUnicos.add(n.cuerpoElse());
        } else if (nodo instanceof NodoConstante n) {
            detalles.add("nombre='" + n.nombre().lexema() + "'");
            detalles.add("tipo='" + n.tipo().lexema() + "'");
            detalles.add("publico=" + n.publico());
            etiquetasUnicas.add("expresion");
            hijosUnicos.add(n.expresion());
        } else if (nodo instanceof NodoAliasTipo n) {
            detalles.add("nombre='" + n.nombre().lexema() + "'");
            detalles.add("tipo='" + n.tipo().lexema() + "'");
            detalles.add("publico=" + n.publico());
        } else if (nodo instanceof NodoEstatico n) {
            detalles.add("nombre='" + n.nombre().lexema() + "'");
            detalles.add("tipo='" + n.tipo().lexema() + "'");
            detalles.add("publico=" + n.publico());
            detalles.add("mutable=" + n.mutable());
            etiquetasUnicas.add("expresion");
            hijosUnicos.add(n.expresion());
        } else if (nodo instanceof NodoReferencia n) {
            detalles.add("mutable=" + n.mutable());
            etiquetasUnicas.add("expresion");
            hijosUnicos.add(n.expresion());
        } else if (nodo instanceof NodoDesreferencia n) {
            etiquetasUnicas.add("referencia");
            hijosUnicos.add(n.referencia());
        } else if (nodo instanceof NodoIf n) {
            etiquetasUnicas.add("condicion");
            hijosUnicos.add(n.condicion());
            etiquetasUnicas.add("cuerpoIf");
            hijosUnicos.add(n.cuerpoIf());
            etiquetasUnicas.add("cuerpoElse");
            hijosUnicos.add(n.cuerpoElse());
        } else if (nodo instanceof NodoIfLet n) {
            detalles.add("patron='" + formatearPatron(n.patron()) + "'");
            etiquetasUnicas.add("expresion");
            hijosUnicos.add(n.expresion());
            etiquetasUnicas.add("guarda");
            hijosUnicos.add(n.guarda());
            etiquetasUnicas.add("cuerpoIf");
            hijosUnicos.add(n.cuerpoIf());
            etiquetasUnicas.add("cuerpoElse");
            hijosUnicos.add(n.cuerpoElse());
        } else if (nodo instanceof NodoWhile n) {
            etiquetasUnicas.add("condicion");
            hijosUnicos.add(n.condicion());
            etiquetasUnicas.add("cuerpo");
            hijosUnicos.add(n.cuerpo());
        } else if (nodo instanceof NodoWhileLet n) {
            detalles.add("patron='" + formatearPatron(n.patron()) + "'");
            etiquetasUnicas.add("expresion");
            hijosUnicos.add(n.expresion());
            etiquetasUnicas.add("guarda");
            hijosUnicos.add(n.guarda());
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
        } else if (nodo instanceof NodoBreak) {
            detalles.add("control='break'");
        } else if (nodo instanceof NodoContinue) {
            detalles.add("control='continue'");
        } else if (nodo instanceof NodoConversion n) {
            detalles.add("tipo='" + n.tipo().lexema() + "'");
            etiquetasUnicas.add("expresion");
            hijosUnicos.add(n.expresion());
        } else if (nodo instanceof NodoStruct n) {
            detalles.add("nombre='" + n.nombre().lexema() + "'");
            detalles.add("publico=" + n.publico());
            detalles.add("tupla=" + n.tupla());
            detalles.add("campos='" + n.campos().stream()
                .map(c -> (c.publico() ? "pub " : "") + c.nombre().lexema() + ": " + c.tipo().lexema())
                .collect(java.util.stream.Collectors.joining(", ")) + "'");
        } else if (nodo instanceof NodoEnum n) {
            detalles.add("nombre='" + n.nombre().lexema() + "'");
            detalles.add("publico=" + n.publico());
            detalles.add("variantes='" + n.variantes().stream().map(v -> v.lexema()
                + (v.forma() == NodoEnum.Forma.TUPLA ? "(" + v.tipos().stream().map(t -> t.lexema())
                    .collect(java.util.stream.Collectors.joining(", ")) + ")"
                    : v.forma() == NodoEnum.Forma.ESTRUCTURA ? " { " + v.campos().stream()
                        .map(c -> c.nombre().lexema() + ": " + c.tipo().lexema())
                        .collect(java.util.stream.Collectors.joining(", ")) + " }" : ""))
                .collect(java.util.stream.Collectors.joining(", ")) + "'");
        } else if (nodo instanceof NodoLiteralStruct n) {
            detalles.add("tipo='" + n.nombre().lexema() + "'");
            for (var campo : n.campos()) {
                etiquetasUnicas.add("campo " + campo.nombre().lexema());
                hijosUnicos.add(campo.valor());
            }
            if (n.base() != null) {
                etiquetasUnicas.add("base de actualización");
                hijosUnicos.add(n.base());
            }
        } else if (nodo instanceof NodoLiteralVariante n) {
            detalles.add("variante='" + n.tipo().lexema() + "::" + n.variante().lexema() + "'");
            for (var campo : n.campos()) {
                etiquetasUnicas.add("campo " + campo.nombre().lexema());
                hijosUnicos.add(campo.valor());
            }
        } else if (nodo instanceof NodoAccesoCampo n) {
            detalles.add("campo='" + n.campo().lexema() + "'");
            etiquetasUnicas.add("objeto");
            hijosUnicos.add(n.objeto());
        } else if (nodo instanceof NodoArreglo n) {
            hijosLista = n.elementos();
            etiquetaLista = "elementos";
        } else if (nodo instanceof NodoTupla n) {
            hijosLista = n.elementos();
            etiquetaLista = "elementos";
        } else if (nodo instanceof NodoIndice n) {
            etiquetasUnicas.add("objeto");
            hijosUnicos.add(n.objeto());
            etiquetasUnicas.add("indice");
            hijosUnicos.add(n.indice());
        } else if (nodo instanceof NodoAccesoTupla n) {
            detalles.add("indice='" + n.indice().lexema() + "'");
            etiquetasUnicas.add("objeto");
            hijosUnicos.add(n.objeto());
        } else if (nodo instanceof NodoVarianteEnum n) {
            detalles.add("variante='" + n.tipo().lexema() + "::" + n.variante().lexema() + "'");
        } else if (nodo instanceof NodoMatch n) {
            etiquetasUnicas.add("expresion");
            hijosUnicos.add(n.expresion());
            for (var brazo : n.brazos()) {
                String patron = formatearPatron(PatronVariante.desde(brazo));
                if (brazo.guarda() != null) {
                    etiquetasUnicas.add("guarda " + patron);
                    hijosUnicos.add(brazo.guarda());
                }
                etiquetasUnicas.add("brazo " + patron);
                hijosUnicos.add(brazo.resultado());
            }
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

    private static String formatearPatron(PatronVariante patron) {
        if (patron.comodin()) return "_";
        String texto = patron.tipo().lexema() + "::" + patron.variante().lexema();
        if (patron.forma() == NodoMatch.FormaPatron.TUPLA) {
            String contenido = patron.elementos().stream().map(FormateadorAst::formatearElementoPatron)
                .collect(java.util.stream.Collectors.joining(", "));
            if (patron.resto()) contenido += (contenido.isEmpty() ? "" : ", ") + "..";
            return texto + "(" + contenido + ")";
        }
        if (patron.forma() == NodoMatch.FormaPatron.ESTRUCTURA) {
            String contenido = patron.campos().stream().map(c -> c.campo().lexema()
                + (c.esAnidado() ? ": " + formatearPatron(c.patron())
                    : c.campo().lexema().equals(c.vinculacion().lexema()) ? ""
                    : ": " + c.vinculacion().lexema()))
                .collect(java.util.stream.Collectors.joining(", "));
            if (patron.resto()) contenido += (contenido.isEmpty() ? "" : ", ") + "..";
            return texto + " { " + contenido + " }";
        }
        return texto;
    }

    private static String formatearElementoPatron(PatronVariante.Elemento elemento) {
        return elemento.esAnidado() ? formatearPatron(elemento.patron()) : elemento.nombre().lexema();
    }
}
