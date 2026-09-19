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
            for (String modulo : new String[]{"Léxico", "Sintáctico", "Semántico", "Tokens", "Resumen", "Alfabeto FISIO", "Alfabeto Rust"}) {
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
            SwingUtilities.invokeAndWait(() -> terminado.set(campo(ventana, "tarea") == null));
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
        SwingUtilities.invokeAndWait(() -> {
            ventana = new VentanaPrincipal();
            editor = (EditorCodigoPanel) campo(ventana, "editor");
            modulos = (Map<?, ?>) campo(ventana, "modulos");
        });
        try {
            SwingUtilities.invokeAndWait(() -> {
                accion("verSintactico"); estado("Sintáctico", EstadoAnalisis.NO_EJECUTADO);
                accion("verSemantico"); estado("Semántico", EstadoAnalisis.NO_EJECUTADO);
                comprobar(campo(ventana, "tarea") == null, "Navegar ejecutó análisis");
            });
            String valido = "fn main() { let mut contador = 0; contador += 1; }";
            ejecutar(valido);
            SwingUtilities.invokeAndWait(() -> {
                estado("Léxico", EstadoAnalisis.CORRECTO);
                estado("Sintáctico", EstadoAnalisis.CORRECTO);
                estado("Semántico", EstadoAnalisis.CORRECTO);
                var resultado = campo(ventana, "ultimoResultado");
                accion("verSintactico"); accion("verSemantico");
                comprobar(resultado == campo(ventana, "ultimoResultado"), "Navegar reemplazó resultados");
            });
            ejecutar("fn main() { let x = $; }");
            SwingUtilities.invokeAndWait(() -> {
                estado("Léxico", EstadoAnalisis.CON_ERRORES);
                estado("Sintáctico", EstadoAnalisis.BLOQUEADO_POR_FASE_ANTERIOR);
                estado("Semántico", EstadoAnalisis.BLOQUEADO_POR_FASE_ANTERIOR);
                var r = (CoordinadorAnalisis.Resultado) campo(ventana, "ultimoResultado");
                comprobar(r.sintactico() == null && r.semantico() == null, "Se ejecutaron fases bloqueadas");
            });
            comprobarResaltado("Léxico");
            ejecutar("fn main() { let = 10; let x = 1; }");
            SwingUtilities.invokeAndWait(() -> {
                estado("Sintáctico", EstadoAnalisis.CON_ERRORES);
                estado("Semántico", EstadoAnalisis.BLOQUEADO_POR_FASE_ANTERIOR);
                JTextPane sintaxis = (JTextPane) campo(campo(ventana, "panelSintactico"), "texto");
                comprobar(sintaxis.getText().contains("AST parcial"), "Falta AST parcial");
            });
            comprobarResaltado("Sintáctico");
            ejecutar("fn main() { let mut contador = 0; contador += \"hola\"; }");
            SwingUtilities.invokeAndWait(() -> {
                estado("Semántico", EstadoAnalisis.CON_ERRORES);
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
            SwingUtilities.invokeAndWait(() -> estado("Semántico", EstadoAnalisis.CORRECTO));
            SwingUtilities.invokeAndWait(() -> {
                invocar("cargarEjemplo", new Class<?>[]{String.class}, "Rust-like - funciones y println!");
                estado("Semántico", EstadoAnalisis.NO_EJECUTADO);
                comprobar(editor.getCodigo().equals(EjemplosFisio.codigoDe("Rust-like - funciones y println!")), "Ejemplo incorrecto");
                invocar("limpiarTodo", new Class<?>[0]);
                estado("Léxico", EstadoAnalisis.NO_EJECUTADO);
                estado("Sintáctico", EstadoAnalisis.NO_EJECUTADO);
                comprobar(editor.getCodigo().isEmpty(), "Limpiar no vació el editor");
                comprobar(campo(ventana, "ultimoResultado") == null, "Limpiar conservó resultados");
            });
            SwingUtilities.invokeAndWait(() -> estado("Semántico", EstadoAnalisis.NO_EJECUTADO));
            System.out.println("GUI PASS: A-H, navegación sin ejecución, AST parcial, edición durante análisis alfabetos separados, resaltado por módulo y capturas 1280/900");
        } finally { SwingUtilities.invokeAndWait(ventana::dispose); }
    }
}
