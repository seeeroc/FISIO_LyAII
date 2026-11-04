package fisio.optimization;

import fisio.analysis.FisioLexerRunner;
import fisio.analysis.FisioParserRunner;
import fisio.ast.*;
import fisio.ci.GeneradorCodigoIntermedio;
import fisio.semantic.FisioSemanticRunner;
import fisio.token.CategoriaToken;

import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class OptimizadorCodigo {
    private static final String ID = "[\\p{L}_][\\p{L}\\p{N}_]*";
    private static final Pattern DECLARACION_LINEA = Pattern.compile(
        "(?m)(^\\h*|(?<=[{;])\\h*)let\\s+(?:mut\\s+)?(" + ID
            + ")\\s*(?::\\s*[^=;]+)?=\\s*([^;\\r\\n]+);\\h*");
    private static final Pattern EXPRESION_ASIGNADA = Pattern.compile(
        "(?m)^(\\h*(?:(?:let\\s+(?:mut\\s+)?" + ID
            + "(?:\\s*:\\s*[^=;]+)?\\s*=)|(?:" + ID + "\\s*=))\\s*)([^;\\r\\n]+)(;\\h*)$");
    private static final Pattern AUTOASIGNACION = Pattern.compile(
        "(?m)^\\h*(" + ID + ")\\s*=\\s*\\1\\s*;\\h*(?:\\R|$)");
    private static final Pattern[] IDENTIDADES = {
        Pattern.compile("^(.+?)\\s*\\+\\s*0(?:\\.0+)?$"),
        Pattern.compile("^0(?:\\.0+)?\\s*\\+\\s*(.+)$"),
        Pattern.compile("^(.+?)\\s*-\\s*0(?:\\.0+)?$"),
        Pattern.compile("^(.+?)\\s*\\*\\s*1(?:\\.0+)?$"),
        Pattern.compile("^1(?:\\.0+)?\\s*\\*\\s*(.+)$"),
        Pattern.compile("^(.+?)\\s*/\\s*1(?:\\.0+)?$"),
        Pattern.compile("^(.+?)\\s*<<\\s*0$"),
        Pattern.compile("^(.+?)\\s*>>\\s*0$"),
        Pattern.compile("^(.+?)\\s*\\|\\s*0$"),
        Pattern.compile("^(.+?)\\s*\\^\\s*0$")
    };
    private static final String[] OPERADORES_IDENTIDAD = {
        "+", "+", "-", "*", "*", "/", "<<", ">>", "|", "^"
    };
    private OptimizadorCodigo() {
    }

    public static ResultadoOptimizacion optimizar(String codigo) {
        Objects.requireNonNull(codigo, "codigo");
        long inicio = System.nanoTime();
        var lexico = FisioLexerRunner.tokenize(codigo);
        if (!lexico.errores().isEmpty()) {
            return bloqueado(codigo, inicio, "Corrige primero los errores léxicos.");
        }
        var sintaxis = FisioParserRunner.parse(codigo);
        if (!sintaxis.errores().isEmpty() || sintaxis.ast() == null) {
            return bloqueado(codigo, inicio, "Corrige primero los errores sintácticos.");
        }
        var semantica = FisioSemanticRunner.analizar(lexico, sintaxis);
        if (!semantica.analizado() || !semantica.errores().isEmpty()) {
            String detalle = semantica.errores().isEmpty() ? semantica.motivo()
                : "Corrige primero los errores semánticos (" + semantica.errores().size() + ").";
            return bloqueado(codigo, inicio, detalle);
        }

        Documento documento = new Documento(codigo);
        eliminarCodigoInalcanzable(documento, sintaxis.ast());
        eliminarComentarios(documento);
        eliminarVariablesNoUtilizadas(documento, sintaxis.ast());
        simplificarAlgebra(documento);
        eliminarAutoasignaciones(documento);
        plegarConstantes(documento);

        String optimizado = documento.codigo();
        var lexicoFinal = FisioLexerRunner.tokenize(optimizado);
        var sintaxisFinal = lexicoFinal.errores().isEmpty() ? FisioParserRunner.parse(optimizado) : null;
        var semanticaFinal = sintaxisFinal == null ? null
            : FisioSemanticRunner.analizar(lexicoFinal, sintaxisFinal);
        if (!lexicoFinal.errores().isEmpty() || sintaxisFinal == null || !sintaxisFinal.errores().isEmpty()
                || semanticaFinal == null || !semanticaFinal.analizado() || !semanticaFinal.errores().isEmpty()) {
            return bloqueado(codigo, inicio,
                "La versión optimizada no superó la validación completa; se conservó el código original.");
        }

        Map<Integer, Integer> mapaLineas = documento.lineaOriginalPorOptimizada();
        List<CambioOptimizacion> cambios = documento.cambiosFinales(mapaLineas);
        long tiempo = System.nanoTime() - inicio;
        MetricasOptimizacion metricas = metricas(codigo, optimizado, cambios, tiempo,
            contarCuadruplos(sintaxis.ast()), contarCuadruplos(sintaxisFinal.ast()));
        return new ResultadoOptimizacion(true, codigo, optimizado, cambios, metricas, mapaLineas,
            cambios.isEmpty() ? "No se encontraron transformaciones seguras." : "");
    }

    private static ResultadoOptimizacion bloqueado(String codigo, long inicio, String motivo) {
        long tiempo = System.nanoTime() - inicio;
        MetricasOptimizacion metricas = metricas(codigo, codigo, List.of(), tiempo, 0, 0);
        return new ResultadoOptimizacion(false, codigo, codigo, List.of(), metricas, Map.of(), motivo);
    }

    private static MetricasOptimizacion metricas(String original, String optimizado,
            List<CambioOptimizacion> cambios, long tiempo, int ciOriginal, int ciOptimizado) {
        EnumMap<TecnicaOptimizacion, Integer> porTecnica = new EnumMap<>(TecnicaOptimizacion.class);
        for (CambioOptimizacion cambio : cambios) porTecnica.merge(cambio.tecnica(), 1, Integer::sum);
        return new MetricasOptimizacion(
            contarLineas(original), contarLineas(optimizado), original.length(), optimizado.length(),
            original.getBytes(StandardCharsets.UTF_8).length,
            optimizado.getBytes(StandardCharsets.UTF_8).length,
            ciOriginal, ciOptimizado, tiempo, porTecnica);
    }

    private static int contarLineas(String codigo) {
        if (codigo.isEmpty()) return 0;
        int lineas = 1;
        for (int i = 0; i < codigo.length(); i++) if (codigo.charAt(i) == '\n') lineas++;
        return lineas;
    }

    private static int contarCuadruplos(NodoPrograma programa) {
        return GeneradorCodigoIntermedio.generar(programa).expresiones().stream()
            .mapToInt(e -> e.cuadruplos().size()).sum();
    }

    private static void eliminarComentarios(Documento documento) {
        String codigo = documento.codigo();
        List<Edicion> ediciones = new ArrayList<>();
        int i = 0;
        while (i < codigo.length()) {
            char actual = codigo.charAt(i);
            if (actual == '"' || actual == '\'') {
                i = saltarLiteral(codigo, i, actual);
                continue;
            }
            if (actual == '/' && i + 1 < codigo.length() && codigo.charAt(i + 1) == '/') {
                int inicio = i;
                int fin = i + 2;
                while (fin < codigo.length() && codigo.charAt(fin) != '\n' && codigo.charAt(fin) != '\r') fin++;
                int inicioLinea = codigo.lastIndexOf('\n', Math.max(0, inicio - 1)) + 1;
                if (codigo.substring(inicioLinea, inicio).isBlank()) {
                    inicio = inicioLinea;
                    fin = incluirSaltoLinea(codigo, fin);
                }
                ediciones.add(new Edicion(inicio, fin, "", TecnicaOptimizacion.ELIMINACION_COMENTARIOS,
                    "Se retiró un comentario de línea que el compilador no necesita."));
                i = fin;
                continue;
            }
            if (actual == '/' && i + 1 < codigo.length() && codigo.charAt(i + 1) == '*') {
                int inicio = i;
                int profundidad = 1;
                i += 2;
                while (i < codigo.length() && profundidad > 0) {
                    if (i + 1 < codigo.length() && codigo.charAt(i) == '/' && codigo.charAt(i + 1) == '*') {
                        profundidad++;
                        i += 2;
                    } else if (i + 1 < codigo.length() && codigo.charAt(i) == '*' && codigo.charAt(i + 1) == '/') {
                        profundidad--;
                        i += 2;
                    } else {
                        i++;
                    }
                }
                int fin = i;
                int inicioEdicion = inicio;
                int finEdicion = fin;
                String reemplazo = codigo.substring(inicio, fin).replaceAll("[^\\r\\n]", "");
                int inicioLinea = codigo.lastIndexOf('\n', Math.max(0, inicio - 1)) + 1;
                int finLinea = fin;
                while (finLinea < codigo.length() && codigo.charAt(finLinea) != '\n'
                        && codigo.charAt(finLinea) != '\r') finLinea++;
                if (codigo.substring(inicioLinea, inicio).isBlank()
                        && codigo.substring(fin, finLinea).isBlank()) {
                    inicioEdicion = inicioLinea;
                    finEdicion = incluirSaltoLinea(codigo, finLinea);
                    reemplazo = "";
                }
                ediciones.add(new Edicion(inicioEdicion, finEdicion, reemplazo,
                    TecnicaOptimizacion.ELIMINACION_COMENTARIOS,
                    "Se retiró un comentario de bloque sin modificar el código que lo rodea."));
                continue;
            }
            i++;
        }
        documento.aplicar(ediciones);
    }

    private static int incluirSaltoLinea(String codigo, int posicion) {
        if (posicion < codigo.length() && codigo.charAt(posicion) == '\r') posicion++;
        if (posicion < codigo.length() && codigo.charAt(posicion) == '\n') posicion++;
        return posicion;
    }

    private static int saltarLiteral(String codigo, int inicio, char delimitador) {
        int i = inicio + 1;
        boolean escapado = false;
        while (i < codigo.length()) {
            char c = codigo.charAt(i++);
            if (escapado) escapado = false;
            else if (c == '\\') escapado = true;
            else if (c == delimitador) break;
        }
        return i;
    }

    private static void eliminarCodigoInalcanzable(Documento documento, NodoPrograma programa) {
        List<NodoBloque> bloques = new ArrayList<>();
        recorrer(programa, nodo -> {
            if (nodo instanceof NodoBloque bloque) bloques.add(bloque);
        });
        List<Edicion> ediciones = new ArrayList<>();
        String codigo = documento.codigo();
        for (NodoBloque bloque : bloques) {
            List<NodoAST> sentencias = bloque.sentencias();
            for (int i = 0; i < sentencias.size(); i++) {
                if (!terminaFlujo(sentencias.get(i))) continue;
                int lineaInicio = i + 1 < sentencias.size() ? sentencias.get(i + 1).linea()
                    : bloque.expresionFinal() == null ? -1 : bloque.expresionFinal().linea();
                if (lineaInicio < 1) break;
                int cierre = encontrarCierreBloque(codigo, bloque.linea(), bloque.columna());
                if (cierre < 0) break;
                int lineaCierre = lineaEn(codigo, cierre);
                if (lineaInicio >= lineaCierre) break;
                int inicio = inicioLinea(codigo, lineaInicio);
                int fin = inicioLinea(codigo, lineaCierre);
                if (inicio >= 0 && fin > inicio) {
                    ediciones.add(new Edicion(inicio, fin, "", TecnicaOptimizacion.CODIGO_INALCANZABLE,
                        "Se eliminó código posterior a una transferencia de control definitiva en el mismo bloque."));
                }
                break;
            }
        }
        documento.aplicar(sinSolapamientos(ediciones));
    }

    private static boolean terminaFlujo(NodoAST nodo) {
        return nodo instanceof NodoReturn || nodo instanceof NodoBreak || nodo instanceof NodoContinue;
    }

    private static List<Edicion> sinSolapamientos(List<Edicion> ediciones) {
        List<Edicion> ordenadas = new ArrayList<>(ediciones);
        ordenadas.sort(Comparator.comparingInt(Edicion::inicio)
            .thenComparing(Comparator.comparingInt(Edicion::fin).reversed()));
        List<Edicion> resultado = new ArrayList<>();
        int cubiertoHasta = -1;
        for (Edicion edicion : ordenadas) {
            if (edicion.inicio() < cubiertoHasta) continue;
            resultado.add(edicion);
            cubiertoHasta = edicion.fin();
        }
        return resultado;
    }

    private static int encontrarCierreBloque(String codigo, int linea, int columna) {
        int desde = inicioLinea(codigo, linea);
        if (desde < 0) return -1;
        int posicionPreferida = Math.min(codigo.length(), desde + Math.max(0, columna - 1));
        int apertura = codigo.indexOf('{', posicionPreferida);
        int finLinea = codigo.indexOf('\n', desde);
        if (finLinea < 0) finLinea = codigo.length();
        if (apertura < 0 || apertura > finLinea) apertura = codigo.indexOf('{', desde);
        if (apertura < 0) return -1;
        int profundidad = 0;
        for (int i = apertura; i < codigo.length(); i++) {
            char c = codigo.charAt(i);
            if (c == '"' || c == '\'') {
                i = saltarLiteral(codigo, i, c) - 1;
                continue;
            }
            if (c == '/' && i + 1 < codigo.length() && codigo.charAt(i + 1) == '/') {
                while (i < codigo.length() && codigo.charAt(i) != '\n') i++;
                continue;
            }
            if (c == '/' && i + 1 < codigo.length() && codigo.charAt(i + 1) == '*') {
                int nivel = 1;
                i += 2;
                while (i < codigo.length() && nivel > 0) {
                    if (i + 1 < codigo.length() && codigo.charAt(i) == '/' && codigo.charAt(i + 1) == '*') {
                        nivel++;
                        i += 2;
                    } else if (i + 1 < codigo.length() && codigo.charAt(i) == '*' && codigo.charAt(i + 1) == '/') {
                        nivel--;
                        i += 2;
                    } else i++;
                }
                i--;
                continue;
            }
            if (c == '{') profundidad++;
            else if (c == '}' && --profundidad == 0) return i;
        }
        return -1;
    }

    private static int lineaEn(String codigo, int posicion) {
        int linea = 1;
        for (int i = 0; i < posicion && i < codigo.length(); i++) if (codigo.charAt(i) == '\n') linea++;
        return linea;
    }

    private static int inicioLinea(String codigo, int linea) {
        if (linea < 1) return -1;
        if (linea == 1) return 0;
        int actual = 1;
        for (int i = 0; i < codigo.length(); i++) {
            if (codigo.charAt(i) == '\n' && ++actual == linea) return i + 1;
        }
        return -1;
    }

    private static void eliminarVariablesNoUtilizadas(Documento documento, NodoPrograma programa) {
        Map<String, NodoDeclaracion> declaraciones = new LinkedHashMap<>();
        recorrer(programa, nodo -> {
            if (nodo instanceof NodoDeclaracion declaracion) {
                declaraciones.put(declaracion.linea() + "\u0000" + declaracion.identificador().lexema(), declaracion);
            }
        });
        for (int vuelta = 0; vuelta < declaraciones.size(); vuelta++) {
            Matcher matcher = DECLARACION_LINEA.matcher(documento.codigo());
            List<Edicion> ediciones = new ArrayList<>();
            while (matcher.find()) {
                String nombre = matcher.group(2);
                if (nombre.startsWith("_") || seUsaEnAmbito(documento.codigo(), matcher.end(), nombre)) continue;
                int lineaOriginal = documento.lineaOriginalEn(matcher.start());
                NodoDeclaracion declaracion = declaraciones.get(lineaOriginal + "\u0000" + nombre);
                if (declaracion == null || !esPuraParaEliminar(declaracion.expresion())) continue;
                int fin = matcher.end();
                if (matcher.start() == 0 || codigoEnLineaNueva(documento.codigo(), matcher.start()))
                    fin = incluirSaltoLinea(documento.codigo(), fin);
                ediciones.add(new Edicion(matcher.start(), fin, "",
                    TecnicaOptimizacion.VARIABLE_NO_UTILIZADA,
                    "La variable '" + nombre + "' nunca se usa y su inicializador no tiene efectos observables."));
            }
            if (ediciones.isEmpty()) break;
            documento.aplicar(ediciones);
        }
    }

    private static boolean codigoEnLineaNueva(String codigo, int posicion) {
        return posicion > 0 && (codigo.charAt(posicion - 1) == '\n' || codigo.charAt(posicion - 1) == '\r');
    }

    private static boolean seUsaEnAmbito(String codigo, int despuesDeclaracion, String nombre) {
        int fin = cierreAmbitoContenedor(codigo, despuesDeclaracion);
        String restoDelAmbito = codigo.substring(Math.min(despuesDeclaracion, codigo.length()), fin);
        return FisioLexerRunner.tokenize(restoDelAmbito).tokens().stream()
            .anyMatch(token -> token.categoria() == CategoriaToken.ID && token.lexema().equals(nombre));
    }

    private static int cierreAmbitoContenedor(String codigo, int posicion) {
        List<Integer> aperturas = new ArrayList<>();
        for (int i = 0; i < posicion && i < codigo.length(); i++) {
            char c = codigo.charAt(i);
            if (c == '"' || c == '\'') {
                i = saltarLiteral(codigo, i, c) - 1;
            } else if (c == '{') {
                aperturas.add(i);
            } else if (c == '}' && !aperturas.isEmpty()) {
                aperturas.remove(aperturas.size() - 1);
            }
        }
        if (aperturas.isEmpty()) return codigo.length();
        int profundidad = 1;
        for (int i = aperturas.get(aperturas.size() - 1) + 1; i < codigo.length(); i++) {
            char c = codigo.charAt(i);
            if (c == '"' || c == '\'') {
                i = saltarLiteral(codigo, i, c) - 1;
            } else if (c == '{') {
                profundidad++;
            } else if (c == '}' && --profundidad == 0) {
                return i;
            }
        }
        return codigo.length();
    }

    private static boolean esPuraParaEliminar(NodoAST nodo) {
        if (nodo == null || nodo instanceof NodoNumero || nodo instanceof NodoIdentificador) return true;
        if (nodo instanceof NodoAgrupacion n) return esPuraParaEliminar(n.expresion());
        if (nodo instanceof NodoConversion n) return esPuraParaEliminar(n.expresion());
        if (nodo instanceof NodoOperacionUnaria n) return esPuraParaEliminar(n.operando());
        if (nodo instanceof NodoOperacion n) {
            String operador = n.operador().lexema();
            return !operador.equals("/") && !operador.equals("%")
                && esPuraParaEliminar(n.izquierda()) && esPuraParaEliminar(n.derecha());
        }
        if (nodo instanceof NodoOperacionRelacional n)
            return esPuraParaEliminar(n.izquierda()) && esPuraParaEliminar(n.derecha());
        if (nodo instanceof NodoTupla n) return n.elementos().stream().allMatch(OptimizadorCodigo::esPuraParaEliminar);
        if (nodo instanceof NodoArreglo n) return n.elementos().stream().allMatch(OptimizadorCodigo::esPuraParaEliminar);
        return false;
    }

    private static void simplificarAlgebra(Documento documento) {
        for (int vuelta = 0; vuelta < 6; vuelta++) {
            Matcher matcher = EXPRESION_ASIGNADA.matcher(documento.codigo());
            List<Edicion> ediciones = new ArrayList<>();
            while (matcher.find()) {
                String expresion = matcher.group(2).trim();
                Simplificacion simplificacion = simplificar(expresion);
                if (simplificacion == null || simplificacion.resultado().equals(expresion)) continue;
                ediciones.add(new Edicion(matcher.start(2), matcher.end(2), simplificacion.resultado(),
                    TecnicaOptimizacion.SIMPLIFICACION_ALGEBRAICA, simplificacion.explicacion()));
            }
            if (ediciones.isEmpty()) break;
            documento.aplicar(ediciones);
        }
    }

    private static void eliminarAutoasignaciones(Documento documento) {
        Matcher matcher = AUTOASIGNACION.matcher(documento.codigo());
        List<Edicion> ediciones = new ArrayList<>();
        while (matcher.find()) {
            ediciones.add(new Edicion(matcher.start(), matcher.end(), "",
                TecnicaOptimizacion.SIMPLIFICACION_ALGEBRAICA,
                "Se eliminó una autoasignación que conservaba exactamente el mismo valor."));
        }
        documento.aplicar(ediciones);
    }

    private static Simplificacion simplificar(String expresion) {
        String operadorRaiz = operadorRaiz(expresion);
        for (int i = 0; i < IDENTIDADES.length; i++) {
            if (!OPERADORES_IDENTIDAD[i].equals(operadorRaiz)) continue;
            Matcher matcher = IDENTIDADES[i].matcher(expresion);
            if (matcher.matches() && esExpresionPura(matcher.group(1))) {
                return new Simplificacion(matcher.group(1).trim(),
                    "Se eliminó una operación con elemento neutro sin cambiar el valor de la expresión.");
            }
        }
        return null;
    }

    private static String operadorRaiz(String expresion) {
        NodoAST nodo = extraerExpresion(expresion);
        while (nodo instanceof NodoAgrupacion agrupacion) nodo = agrupacion.expresion();
        return nodo instanceof NodoOperacion operacion ? operacion.operador().lexema() : "";
    }

    private static boolean esExpresionPura(String expresion) {
        NodoAST nodo = extraerExpresion(expresion);
        return nodo != null && esPuraParaEliminar(nodo);
    }

    private static void plegarConstantes(Documento documento) {
        Matcher matcher = EXPRESION_ASIGNADA.matcher(documento.codigo());
        List<Edicion> ediciones = new ArrayList<>();
        while (matcher.find()) {
            String expresion = matcher.group(2).trim();
            NodoAST nodo = extraerExpresion(expresion);
            if (!esRaizCalculable(nodo) || contieneIdentificador(nodo)) continue;
            String resultado = evaluarConstante(expresion);
            if (resultado == null || resultado.equals(expresion)) continue;
            if (esDecimal(nodo) && !resultado.contains(".")) resultado += ".0";
            ediciones.add(new Edicion(matcher.start(2), matcher.end(2), resultado,
                TecnicaOptimizacion.PLEGADO_CONSTANTES,
                "La expresión contiene únicamente constantes y se resolvió durante la optimización."));
        }
        documento.aplicar(ediciones);
    }

    private static boolean esRaizCalculable(NodoAST nodo) {
        if (nodo instanceof NodoAgrupacion n) return esRaizCalculable(n.expresion());
        return nodo instanceof NodoOperacion || nodo instanceof NodoOperacionUnaria || nodo instanceof NodoConversion;
    }

    private static boolean contieneIdentificador(NodoAST nodo) {
        if (nodo == null) return false;
        final boolean[] encontrado = {false};
        recorrer(nodo, n -> {
            if (n instanceof NodoIdentificador) encontrado[0] = true;
        });
        return encontrado[0];
    }

    private static boolean esDecimal(NodoAST nodo) {
        final boolean[] decimal = {false};
        recorrer(nodo, n -> {
            if (n instanceof NodoNumero numero && numero.token().lexema().contains(".")) decimal[0] = true;
            if (n instanceof NodoConversion conversion && conversion.tipo().lexema().startsWith("f")) decimal[0] = true;
        });
        return decimal[0];
    }

    private static String evaluarConstante(String expresion) {
        String fuente = "fn main() { let __resultado_opt = " + expresion + "; }";
        try {
            var sintaxis = FisioParserRunner.parse(fuente);
            if (!sintaxis.errores().isEmpty() || sintaxis.ast() == null) return null;
            var ci = GeneradorCodigoIntermedio.generar(sintaxis.ast());
            if (!ci.problemas().isEmpty() || ci.expresiones().size() != 1) return null;
            return ci.expresiones().get(0).resultadoFinal();
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static NodoAST extraerExpresion(String expresion) {
        try {
            var sintaxis = FisioParserRunner.parse("fn main() { let __opt = " + expresion + "; }");
            if (!sintaxis.errores().isEmpty() || sintaxis.ast() == null
                    || sintaxis.ast().sentencias().isEmpty()
                    || !(sintaxis.ast().sentencias().get(0) instanceof NodoFuncion funcion)
                    || funcion.cuerpo().sentencias().isEmpty()
                    || !(funcion.cuerpo().sentencias().get(0) instanceof NodoDeclaracion declaracion)) return null;
            return declaracion.expresion();
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static void recorrer(Object raiz, Consumer<NodoAST> consumidor) {
        Set<Object> visitados = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        recorrer(raiz, consumidor, visitados);
    }

    private static void recorrer(Object objeto, Consumer<NodoAST> consumidor, Set<Object> visitados) {
        if (objeto == null || !visitados.add(objeto)) return;
        if (objeto instanceof NodoAST nodo) consumidor.accept(nodo);
        if (objeto instanceof Iterable<?> iterable) {
            for (Object elemento : iterable) recorrer(elemento, consumidor, visitados);
            return;
        }
        Class<?> tipo = objeto.getClass();
        if (!tipo.isRecord() || !tipo.getPackageName().startsWith("fisio.ast")) return;
        for (RecordComponent componente : tipo.getRecordComponents()) {
            try {
                recorrer(componente.getAccessor().invoke(objeto), consumidor, visitados);
            } catch (ReflectiveOperationException ex) {
                throw new IllegalStateException("No se pudo recorrer el AST", ex);
            }
        }
    }

    private record Simplificacion(String resultado, String explicacion) {
    }

    private record Edicion(int inicio, int fin, String reemplazo,
            TecnicaOptimizacion tecnica, String explicacion) {
    }

    private record CambioPendiente(TecnicaOptimizacion tecnica, int lineaOriginalInicio,
            int lineaOriginalFin, String antes, String despues, String explicacion) {
    }

    private static final class Documento {
        private final StringBuilder texto;
        private final List<Integer> origenPorCaracter;
        private final List<CambioPendiente> cambios = new ArrayList<>();

        Documento(String original) {
            texto = new StringBuilder(original);
            origenPorCaracter = new ArrayList<>(original.length());
            int linea = 1;
            for (int i = 0; i < original.length(); i++) {
                origenPorCaracter.add(linea);
                if (original.charAt(i) == '\n') linea++;
            }
        }

        String codigo() {
            return texto.toString();
        }

        int lineaOriginalEn(int posicion) {
            if (origenPorCaracter.isEmpty()) return 1;
            int indice = Math.max(0, Math.min(posicion, origenPorCaracter.size() - 1));
            return origenPorCaracter.get(indice);
        }

        void aplicar(List<Edicion> ediciones) {
            List<Edicion> ordenadas = new ArrayList<>(ediciones);
            ordenadas.sort(Comparator.comparingInt(Edicion::inicio).reversed());
            int limite = texto.length() + 1;
            for (Edicion edicion : ordenadas) {
                if (edicion.inicio() < 0 || edicion.fin() < edicion.inicio()
                        || edicion.fin() > texto.length() || edicion.fin() > limite) continue;
                aplicar(edicion);
                limite = edicion.inicio();
            }
        }

        private void aplicar(Edicion edicion) {
            String antes = texto.substring(edicion.inicio(), edicion.fin());
            int lineaInicio = lineaOriginalEn(edicion.inicio());
            int lineaFin = edicion.fin() > edicion.inicio()
                ? lineaOriginalEn(edicion.fin() - 1) : lineaInicio;
            texto.replace(edicion.inicio(), edicion.fin(), edicion.reemplazo());
            for (int i = edicion.fin() - 1; i >= edicion.inicio(); i--) origenPorCaracter.remove(i);
            int linea = lineaInicio;
            for (int i = 0; i < edicion.reemplazo().length(); i++) {
                origenPorCaracter.add(edicion.inicio() + i, linea);
                if (edicion.reemplazo().charAt(i) == '\n') linea++;
            }
            cambios.add(new CambioPendiente(edicion.tecnica(), lineaInicio, lineaFin,
                antes.strip(), edicion.reemplazo().strip(), edicion.explicacion()));
        }

        Map<Integer, Integer> lineaOriginalPorOptimizada() {
            Map<Integer, Integer> mapa = new LinkedHashMap<>();
            if (texto.isEmpty()) return mapa;
            int lineaOptimizada = 1;
            int inicio = 0;
            while (inicio <= texto.length()) {
                int fin = texto.indexOf("\n", inicio);
                if (fin < 0) fin = texto.length();
                int origen = origenDeRango(inicio, fin);
                mapa.put(lineaOptimizada++, origen);
                if (fin == texto.length()) break;
                inicio = fin + 1;
            }
            return mapa;
        }

        private int origenDeRango(int inicio, int fin) {
            if (origenPorCaracter.isEmpty()) return 1;
            for (int i = inicio; i < fin && i < origenPorCaracter.size(); i++) return origenPorCaracter.get(i);
            if (inicio < origenPorCaracter.size()) return origenPorCaracter.get(inicio);
            return origenPorCaracter.get(origenPorCaracter.size() - 1);
        }

        List<CambioOptimizacion> cambiosFinales(Map<Integer, Integer> mapa) {
            Map<Integer, Integer> optimizadaPorOriginal = new LinkedHashMap<>();
            mapa.forEach((optimizada, original) -> optimizadaPorOriginal.putIfAbsent(original, optimizada));
            List<CambioPendiente> ordenados = new ArrayList<>(cambios);
            ordenados.sort(Comparator.comparingInt(CambioPendiente::lineaOriginalInicio)
                .thenComparing(c -> c.tecnica().ordinal()));
            List<CambioOptimizacion> resultado = new ArrayList<>();
            int numero = 1;
            for (CambioPendiente cambio : ordenados) {
                int lineaOptimizada = optimizadaPorOriginal.getOrDefault(cambio.lineaOriginalInicio(), 0);
                resultado.add(new CambioOptimizacion(numero++, cambio.tecnica(),
                    cambio.lineaOriginalInicio(), cambio.lineaOriginalFin(), lineaOptimizada,
                    lineaOptimizada, cambio.antes(), cambio.despues(), cambio.explicacion()));
            }
            return resultado;
        }
    }
}
