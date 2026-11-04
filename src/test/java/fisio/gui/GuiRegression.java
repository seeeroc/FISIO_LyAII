package fisio.gui;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import javax.swing.*;

/** Pruebas de orquestación real y estados Swing, sin dependencias externas. */
public final class GuiRegression {
    private static VentanaPrincipal ventana;
    private static EditorCodigoPanel editor;
    private static Map<?, ?> modulos;

    private static Object campo(Object objeto, String nombre) {
        try {
            Field campo = objeto.getClass().getDeclaredField(nombre);
            campo.setAccessible(true);
            return campo.get(objeto);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static void invocar(String nombre, Class<?>[] tipos, Object... argumentos) {
        try {
            var metodo = VentanaPrincipal.class.getDeclaredMethod(nombre, tipos);
            metodo.setAccessible(true);
            metodo.invoke(ventana, argumentos);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static void comprobar(boolean condicion, String mensaje) {
        if (!condicion) throw new AssertionError(mensaje);
    }
    private static void estado(String modulo, EstadoAnalisis esperado) {
        var panel = (PanelModulo) modulos.get(modulo);
        comprobar(panel.estadoActual == esperado, modulo + ": " + panel.estadoActual + " != " + esperado);
        comprobar(!((JTextArea) campo(panel, "mensaje")).getText().isBlank(), "Falta mensaje: " + modulo);
    }
    private static void accion(String nombre) {
        ventana.getRootPane().getActionMap().get(nombre).actionPerformed(null);
    }
    private static void comprobarResaltado(String faseConError) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JTextPane texto = (JTextPane) campo(editor, "texto");
            for (String modulo : new String[]{"Léxico", "Sintáctico", "Semántico", "Código Intermedio (CI)",
                    "Optimización", "Tokens", "Resumen", "Alfabeto FISIO", "Alfabeto Rust"}) {
                invocar("seleccionar", new Class<?>[]{String.class}, modulo);
                boolean rojo = false;
                var documento = texto.getStyledDocument();
                for (int i = 0; i < documento.getLength(); i++) {
                    var fondo = javax.swing.text.StyleConstants.getBackground(documento.getCharacterElement(i).getAttributes());
                    rojo |= fondo.equals(java.awt.Color.decode("#2d1b1b"));
                }
                comprobar(rojo == modulo.equals(faseConError), "Resaltado incorrecto en " + modulo + " para " + faseConError);
            }
        });
    }
    private static void ejecutar(String codigo) throws Exception {
        SwingUtilities.invokeAndWait(() -> editor.setCodigo(codigo));
        SwingUtilities.invokeAndWait(() -> accion("ejecutar"));
        esperar();
    }
    private static void esperar() throws Exception {
        long limite = System.nanoTime() + 15_000_000_000L;
        AtomicReference<Boolean> terminado = new AtomicReference<>(false);
        do {
            SwingUtilities.invokeAndWait(() -> terminado.set(campo(ventana, "tarea") == null
                && campo(ventana, "tareaOptimizacion") == null));
            if (terminado.get()) return;
            Thread.sleep(20);
        } while (System.nanoTime() < limite);
        throw new AssertionError("El análisis no terminó");
    }
    private static void captura(int ancho, String nombre) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ventana.pack();
            ventana.setSize(ancho, 780);
            ventana.validate();
            BufferedImage imagen = new BufferedImage(ancho, 780, BufferedImage.TYPE_INT_RGB);
            var graficos = imagen.createGraphics();
            ventana.getRootPane().printAll(graficos);
            graficos.dispose();
            try { ImageIO.write(imagen, "png", Path.of("target", nombre).toFile()); }
            catch (Exception e) { throw new AssertionError(e); }
        });
    }
    public static void main(String[] args) throws Exception {
        try {
            EjemplosFisio.codigoDe("ejemplo inexistente");
            throw new AssertionError("Una clave desconocida no debe vaciar silenciosamente el editor");
        } catch (IllegalArgumentException esperado) {
            comprobar(esperado.getMessage().contains("ejemplo inexistente"),
                "El error de ejemplo desconocido no identifica la clave");
        }
        for (Map.Entry<String, String> ejemplo : EjemplosFisio.mapa().entrySet()) {
            String codigo = ejemplo.getValue();
            long lineas = codigo.lines().filter(linea -> !linea.isBlank()).count();
            comprobar(lineas >= 200, ejemplo.getKey() + " tiene menos de 200 líneas de código");
            boolean contieneComentarios = codigo.contains("//") || codigo.contains("/*") || codigo.contains("*/");
            comprobar(contieneComentarios == ejemplo.getKey().equals("Optimización"),
                ejemplo.getKey() + " no cumple la política de comentarios de su ejemplo");
            var lexicoEjemplo = fisio.analysis.FisioLexerRunner.tokenize(codigo);
            var sintaxisEjemplo = fisio.analysis.FisioParserRunner.parse(codigo);
            comprobar(lexicoEjemplo.errores().isEmpty(), ejemplo.getKey() + " tiene errores léxicos");
            comprobar(sintaxisEjemplo.errores().isEmpty(), ejemplo.getKey() + " tiene errores sintácticos");
            var semanticaEjemplo = fisio.semantic.FisioSemanticRunner.analizar(lexicoEjemplo, sintaxisEjemplo);
            if (ejemplo.getKey().equals("Errores semánticos")) {
                comprobar(semanticaEjemplo.analizado() && !semanticaEjemplo.errores().isEmpty(),
                    "El ejemplo de errores no contiene diagnósticos semánticos");
            } else {
                comprobar(semanticaEjemplo.exitoso(),
                    ejemplo.getKey() + " tiene errores semánticos: " + semanticaEjemplo.errores());
            }
        }
        var astCompuesto = fisio.analysis.FisioParserRunner.parse(
            EjemplosFisio.codigoDe("Rust-like - structs, enums y match"));
        String arbolCompuesto = String.join("\n", FormateadorAst.formatear(astCompuesto.ast()));
        comprobar(astCompuesto.errores().isEmpty() && arbolCompuesto.contains("NodoStruct")
            && arbolCompuesto.contains("NodoEnum") && arbolCompuesto.contains("NodoMatch")
            && arbolCompuesto.contains("NodoAccesoCampo"), "La GUI no representa el AST de tipos compuestos");
        var astEnumsNombrados = fisio.analysis.FisioParserRunner.parse(
            EjemplosFisio.codigoDe("Rust-like - enums nombrados y guardas"));
        String arbolEnumsNombrados = String.join("\n", FormateadorAst.formatear(astEnumsNombrados.ast()));
        comprobar(astEnumsNombrados.errores().isEmpty()
            && arbolEnumsNombrados.contains("Mover { x: i32, y: i32 }")
            && arbolEnumsNombrados.contains("NodoLiteralVariante")
            && arbolEnumsNombrados.contains("guarda Mensaje::Mover { x, y }")
            && arbolEnumsNombrados.contains("x: horizontal"),
            "La GUI no representa variantes nombradas, patrones o guardas");
        var astPatronesParciales = fisio.analysis.FisioParserRunner.parse(
            EjemplosFisio.codigoDe("Rust-like - actualización y patrones parciales"));
        String arbolPatronesParciales = String.join("\n", FormateadorAst.formatear(astPatronesParciales.ast()));
        comprobar(astPatronesParciales.errores().isEmpty()
            && arbolPatronesParciales.contains("Punto { x, .. }")
            && arbolPatronesParciales.contains("Color(rojo, ..)")
            && arbolPatronesParciales.contains("base de actualización"),
            "La GUI no representa actualizaciones struct o patrones parciales");
        var astLetPatrones = fisio.analysis.FisioParserRunner.parse(
            EjemplosFisio.codigoDe("Rust-like - if let y while let"));
        String arbolLetPatrones = String.join("\n", FormateadorAst.formatear(astLetPatrones.ast()));
        comprobar(astLetPatrones.errores().isEmpty()
            && arbolLetPatrones.contains("NodoIfLet") && arbolLetPatrones.contains("NodoWhileLet")
            && arbolLetPatrones.contains("patron='Evento::Dato { valor, .. }'")
            && arbolLetPatrones.contains("guarda"),
            "La GUI no representa if let, while let o sus patrones");
        var astLetElse = fisio.analysis.FisioParserRunner.parse(
            EjemplosFisio.codigoDe("Rust-like - let else"));
        String arbolLetElse = String.join("\n", FormateadorAst.formatear(astLetElse.ast()));
        comprobar(astLetElse.errores().isEmpty() && arbolLetElse.contains("NodoLetElse")
            && arbolLetElse.contains("patron='Lectura::Dato { numero, .. }'")
            && arbolLetElse.contains("cuerpoElse"),
            "La GUI no representa let else o su ámbito de salida");
        var astAnidados = fisio.analysis.FisioParserRunner.parse(
            EjemplosFisio.codigoDe("Rust-like - patrones anidados"));
        String arbolAnidados = String.join("\n", FormateadorAst.formatear(astAnidados.ast()));
        comprobar(astAnidados.errores().isEmpty()
            && arbolAnidados.contains("Mensaje::Dato(Opcion::Valor(numero))")
            && arbolAnidados.contains("contenido: Opcion::Valor(numero)")
            && arbolAnidados.contains("NodoLetElse"),
            "La GUI no representa los patrones anidados");
        var astColecciones = fisio.analysis.FisioParserRunner.parse(
            EjemplosFisio.codigoDe("Rust-like - arreglos y tuplas"));
        String arbolColecciones = String.join("\n", FormateadorAst.formatear(astColecciones.ast()));
        comprobar(astColecciones.errores().isEmpty() && arbolColecciones.contains("NodoArreglo")
            && arbolColecciones.contains("NodoTupla") && arbolColecciones.contains("NodoIndice")
            && arbolColecciones.contains("NodoAccesoTupla"),
            "La GUI no representa el AST de arreglos y tuplas");
        var astConstantes = fisio.analysis.FisioParserRunner.parse(
            EjemplosFisio.codigoDe("Rust-like - constantes"));
        String arbolConstantes = String.join("\n", FormateadorAst.formatear(astConstantes.ast()));
        comprobar(astConstantes.errores().isEmpty() && arbolConstantes.contains("NodoConstante")
            && arbolConstantes.contains("nombre='BASE'") && arbolConstantes.contains("tipo='i32'"),
            "La GUI no representa las constantes en el AST");
        var astPublico = fisio.analysis.FisioParserRunner.parse(
            EjemplosFisio.codigoDe("Rust-like - visibilidad pub"));
        String arbolPublico = String.join("\n", FormateadorAst.formatear(astPublico.ast()));
        comprobar(astPublico.errores().isEmpty() && arbolPublico.contains("publico=true")
            && arbolPublico.contains("pub valor: i32"), "La GUI no representa la visibilidad pub");
        var astAlias = fisio.analysis.FisioParserRunner.parse(
            EjemplosFisio.codigoDe("Rust-like - alias de tipos"));
        String arbolAlias = String.join("\n", FormateadorAst.formatear(astAlias.ast()));
        comprobar(astAlias.errores().isEmpty() && arbolAlias.contains("NodoAliasTipo")
            && arbolAlias.contains("nombre='Entero'") && arbolAlias.contains("tipo='i32'"),
            "La GUI no representa los alias de tipos");
        var astStatic = fisio.analysis.FisioParserRunner.parse(
            EjemplosFisio.codigoDe("Rust-like - elementos static"));
        String arbolStatic = String.join("\n", FormateadorAst.formatear(astStatic.ast()));
        comprobar(astStatic.errores().isEmpty() && arbolStatic.contains("NodoEstatico")
            && arbolStatic.contains("nombre='VERSION'") && arbolStatic.contains("mutable=false"),
            "La GUI no representa elementos static");
        var astReferencias = fisio.analysis.FisioParserRunner.parse(
            EjemplosFisio.codigoDe("Rust-like - referencias"));
        String arbolReferencias = String.join("\n", FormateadorAst.formatear(astReferencias.ast()));
        comprobar(astReferencias.errores().isEmpty() && arbolReferencias.contains("NodoReferencia")
            && arbolReferencias.contains("NodoDesreferencia") && arbolReferencias.contains("mutable=true"),
            "La GUI no representa referencias compartidas y mutables");
        var astImpl = fisio.analysis.FisioParserRunner.parse(
            EjemplosFisio.codigoDe("Rust-like - impl y metodos"));
        String arbolImpl = String.join("\n", FormateadorAst.formatear(astImpl.ast()));
        comprobar(astImpl.errores().isEmpty() && arbolImpl.contains("NodoImpl")
            && arbolImpl.contains("NodoLlamadaAsociada") && arbolImpl.contains("NodoLlamadaMetodo")
            && arbolImpl.contains("tipo='Punto'"), "La GUI no representa bloques impl y métodos");
        var astTraits = fisio.analysis.FisioParserRunner.parse(
            EjemplosFisio.codigoDe("Rust-like - traits"));
        String arbolTraits = String.join("\n", FormateadorAst.formatear(astTraits.ast()));
        comprobar(astTraits.errores().isEmpty() && arbolTraits.contains("NodoTrait")
            && arbolTraits.contains("NodoFirmaTrait") && arbolTraits.contains("trait='Medible'")
            && arbolTraits.contains("tipo='Medicion'"), "La GUI no representa contratos trait");
        var astAlgebraico = fisio.analysis.FisioParserRunner.parse(
            EjemplosFisio.codigoDe("Rust-like - datos algebraicos"));
        String arbolAlgebraico = String.join("\n", FormateadorAst.formatear(astAlgebraico.ast()));
        comprobar(astAlgebraico.errores().isEmpty() && arbolAlgebraico.contains("tupla=true")
            && arbolAlgebraico.contains("Exito(i32)") && arbolAlgebraico.contains("Par(a, b)"),
            "La GUI no representa structs de tupla, variantes con datos y patrones");
        var astChecklist = fisio.analysis.FisioParserRunner.parse(
            "enum E { A } fn auxiliar() -> i32 { 1 } "
                + "fn f(e: E) -> i32 { match e { E::A => auxiliar(), } }");
        AtomicReference<String> checklist = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            PanelSintactico panel = new PanelSintactico();
            panel.poblar(astChecklist.ast(), astChecklist.errores());
            checklist.set(((JTextPane) campo(panel, "texto")).getText());
        });
        comprobar(checklist.get().contains("Llamada a una función"),
            "El checklist sintáctico no recorre los nodos contenidos en brazos de match");
        SwingUtilities.invokeAndWait(() -> {
            ventana = new VentanaPrincipal();
            editor = (EditorCodigoPanel) campo(ventana, "editor");
            modulos = (Map<?, ?>) campo(ventana, "modulos");
        });
        try {
            SwingUtilities.invokeAndWait(() -> {
                JComboBox<?> selector = (JComboBox<?>) campo(ventana, "selectorEjemplos");
                comprobar(EjemplosFisio.mapa().size() == 11, "El catálogo visible debe ser reducido");
                comprobar(selector.getItemCount() == EjemplosFisio.mapa().size() + 1,
                    "El selector no coincide con el catálogo de ejemplos");
                comprobar(editor.getCodigo().equals(EjemplosFisio.codigoDe("Fundamentos")),
                    "La GUI no inicia con el ejemplo de fundamentos");
                comprobar(!EjemplosFisio.mapa().containsKey("Fase 2: negativos y tipos"),
                    "El selector todavía muestra ejemplos antiguos");
            });
            SwingUtilities.invokeAndWait(() -> {
                accion("verSintactico"); estado("Sintáctico", EstadoAnalisis.NO_EJECUTADO);
                accion("verSemantico"); estado("Semántico", EstadoAnalisis.NO_EJECUTADO);
                accion("verCodigoIntermedio"); estado("Código Intermedio (CI)", EstadoAnalisis.NO_EJECUTADO);
                estado("Optimización", EstadoAnalisis.NO_EJECUTADO);
                comprobar(campo(ventana, "tarea") == null, "Navegar ejecutó análisis");
            });
            String valido = "fn main() { let mut contador = 0; contador += 1; }";
            ejecutar(valido);
            SwingUtilities.invokeAndWait(() -> {
                estado("Léxico", EstadoAnalisis.CORRECTO);
                estado("Sintáctico", EstadoAnalisis.CORRECTO);
                estado("Semántico", EstadoAnalisis.CORRECTO);
                estado("Código Intermedio (CI)", EstadoAnalisis.CORRECTO);
                var resultado = campo(ventana, "ultimoResultado");
                accion("verSintactico"); accion("verSemantico"); accion("verCodigoIntermedio");
                comprobar(resultado == campo(ventana, "ultimoResultado"), "Navegar reemplazó resultados");
                var panelCi = campo(ventana, "panelCodigoIntermedio");
                JEditorPane documentoCi = (JEditorPane) campo(panelCi, "documento");
                try {
                    String contenidoCi = documentoCi.getDocument().getText(0, documentoCi.getDocument().getLength());
                    comprobar(contenidoCi.contains("Asignación compuesta a contador"), "CI no mostró la expresión analizada");
                } catch (javax.swing.text.BadLocationException ex) {
                    throw new AssertionError(ex);
                }
            });
            captura(1280, "gui-ci.png");
            ejecutar("fn main() { let cero = 0; let x = 5 / cero; }");
            SwingUtilities.invokeAndWait(() -> {
                estado("Semántico", EstadoAnalisis.CORRECTO);
                estado("Código Intermedio (CI)", EstadoAnalisis.CON_ERRORES);
                var r = (CoordinadorAnalisis.Resultado) campo(ventana, "ultimoResultado");
                comprobar(r.codigoIntermedio().problemas().size() == 1, "CI no detectó división entre cero resuelta por variable");
            });
            ejecutar("fn main() { let x = $; }");
            SwingUtilities.invokeAndWait(() -> {
                estado("Léxico", EstadoAnalisis.CON_ERRORES);
                estado("Sintáctico", EstadoAnalisis.BLOQUEADO_POR_FASE_ANTERIOR);
                estado("Semántico", EstadoAnalisis.BLOQUEADO_POR_FASE_ANTERIOR);
                estado("Código Intermedio (CI)", EstadoAnalisis.BLOQUEADO_POR_FASE_ANTERIOR);
                var r = (CoordinadorAnalisis.Resultado) campo(ventana, "ultimoResultado");
                comprobar(r.sintactico() == null && r.semantico() == null && !r.codigoIntermedio().generado(), "Se ejecutaron fases bloqueadas");
            });
            comprobarResaltado("Léxico");
            ejecutar("fn main() { let = 10; let x = 1; }");
            SwingUtilities.invokeAndWait(() -> {
                estado("Sintáctico", EstadoAnalisis.CON_ERRORES);
                estado("Semántico", EstadoAnalisis.BLOQUEADO_POR_FASE_ANTERIOR);
                estado("Código Intermedio (CI)", EstadoAnalisis.BLOQUEADO_POR_FASE_ANTERIOR);
                JTextPane sintaxis = (JTextPane) campo(campo(ventana, "panelSintactico"), "texto");
                comprobar(sintaxis.getText().contains("AST parcial"), "Falta AST parcial");
            });
            comprobarResaltado("Sintáctico");
            ejecutar("fn main() { let mut contador = 0; contador += \"hola\"; }");
            SwingUtilities.invokeAndWait(() -> {
                estado("Semántico", EstadoAnalisis.CON_ERRORES);
                estado("Código Intermedio (CI)", EstadoAnalisis.BLOQUEADO_POR_FASE_ANTERIOR);
                JTextArea semantica = (JTextArea) campo(campo(ventana, "panelSemantico"), "texto");
                comprobar(semantica.getText().contains("OPERADOR"), "Falta diagnóstico semántico");
                accion("verSemantico");
            });
            comprobarResaltado("Semántico");
            SwingUtilities.invokeAndWait(() -> accion("verSemantico"));
            captura(1280, "gui-semantico.png");
            captura(900, "gui-compacta.png");
            for (String nombre : new String[]{"Alfabeto FISIO", "Alfabeto Rust"}) {
                SwingUtilities.invokeAndWait(() -> {
                    invocar("seleccionar", new Class<?>[]{String.class}, nombre);
                    var panel = (PanelAlfabeto) campo(modulos.get(nombre), "contenido");
                    var documento = (JEditorPane) campo(panel, "documento");
                    try {
                        String referencia = documento.getDocument().getText(0, documento.getDocument().getLength());
                        comprobar(referencia.contains(nombre.endsWith("FISIO") ? "mru" : "fn"), "Referencia ausente");
                        comprobar(!referencia.contains(nombre.endsWith("FISIO") ? "Rust-like" : "mru"), "Alfabetos mezclados");
                        comprobar(referencia.contains(nombre.endsWith("FISIO") ? ":=" : "<<="), "Operador ilegible");
                    } catch (Exception e) { throw new AssertionError(e); }
                });
                captura(900, nombre.endsWith("FISIO") ? "alfabeto-fisio.png" : "alfabeto-rust.png");
            }
            SwingUtilities.invokeAndWait(() -> editor.setCodigo(valido));
            SwingUtilities.invokeAndWait(() -> {
                estado("Tokens", EstadoAnalisis.DESACTUALIZADO);
                estado("Sintáctico", EstadoAnalisis.DESACTUALIZADO);
                estado("Semántico", EstadoAnalisis.DESACTUALIZADO);
                estado("Código Intermedio (CI)", EstadoAnalisis.DESACTUALIZADO);
                estado("Alfabeto FISIO", EstadoAnalisis.CORRECTO);
                estado("Alfabeto Rust", EstadoAnalisis.CORRECTO);
            });
            comprobarResaltado("");
            ejecutar(valido);
            SwingUtilities.invokeAndWait(() -> estado("Semántico", EstadoAnalisis.CORRECTO));
            // Editar antes de que SwingWorker publique su resultado debe invalidarlo.
            SwingUtilities.invokeAndWait(() -> { accion("ejecutar"); editor.setCodigo("fn otro() {}"); });
            esperar();
            SwingUtilities.invokeAndWait(() -> estado("Semántico", EstadoAnalisis.DESACTUALIZADO));
            ejecutar("fn otro() {}");
            SwingUtilities.invokeAndWait(() -> {
                estado("Semántico", EstadoAnalisis.CORRECTO);
                estado("Código Intermedio (CI)", EstadoAnalisis.CORRECTO);
                JEditorPane documentoCi = (JEditorPane) campo(campo(ventana, "panelCodigoIntermedio"), "documento");
                try {
                    String contenidoCi = documentoCi.getDocument().getText(0, documentoCi.getDocument().getLength());
                    comprobar(contenidoCi.contains("No se encontraron expresiones"), "Falta mensaje de CI vacío");
                } catch (javax.swing.text.BadLocationException ex) {
                    throw new AssertionError(ex);
                }
            });
            SwingUtilities.invokeAndWait(() -> {
                invocar("cargarEjemplo", new Class<?>[]{String.class}, "Fundamentos");
                estado("Semántico", EstadoAnalisis.NO_EJECUTADO);
                estado("Código Intermedio (CI)", EstadoAnalisis.NO_EJECUTADO);
                comprobar(editor.getCodigo().equals(EjemplosFisio.codigoDe("Fundamentos")), "Ejemplo incorrecto");
                invocar("cargarEjemplo", new Class<?>[]{String.class}, "Código intermedio");
                comprobar(editor.getCodigo().contains("let resultado = (a + b) * 2 - 8 / 4;"), "Ejemplo de CI ausente");
                estado("Código Intermedio (CI)", EstadoAnalisis.NO_EJECUTADO);
            });
            SwingUtilities.invokeAndWait(() -> invocar("cargarEjemplo",
                new Class<?>[]{String.class}, "Optimización"));
            String originalOptimizacion = editor.getCodigo();
            comprobar(originalOptimizacion.contains("variable_no_usada")
                && originalOptimizacion.contains("// La expresión constante"),
                "El ejemplo no contiene oportunidades de optimización");
            SwingUtilities.invokeAndWait(() -> accion("optimizar"));
            esperar();
            captura(900, "gui-optimizacion-resumen-compacta.png");
            SwingUtilities.invokeAndWait(() -> {
                estado("Optimización", EstadoAnalisis.CORRECTO);
                var resultado = (fisio.optimization.ResultadoOptimizacion) campo(ventana, "ultimaOptimizacion");
                comprobar(resultado != null && resultado.exitoso() && !resultado.cambios().isEmpty(),
                    "La GUI no conservó el resultado de optimización");
                for (fisio.optimization.TecnicaOptimizacion tecnica
                        : fisio.optimization.TecnicaOptimizacion.values()) {
                    comprobar(resultado.metricas().cambiosPorTecnica().getOrDefault(tecnica, 0) > 0,
                        "El ejemplo no demuestra " + tecnica.descripcion());
                }
                comprobar(resultado.metricas().bytesOptimizados() < resultado.metricas().bytesOriginales()
                    && resultado.metricas().cuadruplosOptimizados() < resultado.metricas().cuadruplosOriginales(),
                    "Las métricas no reflejan la reducción del ejemplo");
                comprobar(editor.getCodigo().equals(resultado.codigoOptimizado())
                    && !editor.getCodigo().contains("// La expresión constante")
                    && !editor.getCodigo().contains("variable_no_usada"),
                    "El editor no muestra el código optimizado");
                PanelOptimizacion panel = (PanelOptimizacion) campo(ventana, "panelOptimizacion");
                comprobar(panel.getReporte().contains("REPORTE DE OPTIMIZACIÓN FISIO")
                    && !panel.getCambios().isEmpty(), "El panel no muestra reporte o cambios");
                panel.seleccionarCambio(0);
                invocar("mostrarComparacion", new Class<?>[0]);
                comprobar(((DialogoComparacion) campo(ventana, "dialogoComparacion")).isVisible(),
                    "La comparación completa no se abrió");
                ((DialogoComparacion) campo(ventana, "dialogoComparacion")).setVisible(false);
            });
            captura(1280, "gui-optimizacion.png");
            captura(900, "gui-optimizacion-compacta.png");
            SwingUtilities.invokeAndWait(() -> {
                invocar("restaurarOriginal", new Class<?>[0]);
                comprobar(editor.getCodigo().equals(originalOptimizacion), "No se restauró el código original");
                estado("Optimización", EstadoAnalisis.NO_EJECUTADO);
                invocar("limpiarTodo", new Class<?>[0]);
                estado("Léxico", EstadoAnalisis.NO_EJECUTADO);
                estado("Sintáctico", EstadoAnalisis.NO_EJECUTADO);
                comprobar(editor.getCodigo().isEmpty(), "Limpiar no vació el editor");
                comprobar(campo(ventana, "ultimoResultado") == null, "Limpiar conservó resultados");
                JTextArea lexico = (JTextArea) campo(campo(ventana, "panelLexico"), "texto");
                comprobar(lexico.getText().isEmpty(), "Limpiar conservó el reporte léxico anterior");
            });
            SwingUtilities.invokeAndWait(() -> estado("Semántico", EstadoAnalisis.NO_EJECUTADO));
            System.out.println("GUI PASS: pipeline completo, CI, optimización, comparación, restauración, alfabetos y resaltado por módulo");
        } finally { SwingUtilities.invokeAndWait(ventana::dispose); }
    }
}
