package fisio.gui;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;

/** Documento y presentación en EDT; el coordinador ejecuta las fases en segundo plano. */
public final class VentanaPrincipal extends JFrame {
    private static final String TITULO_BASE = "FISIO — Analizador de código";
    private final EditorCodigoPanel editor = new EditorCodigoPanel();
    private final TablaTokensPanel tablaTokens = new TablaTokensPanel();
    private final PanelResumen panelResumen = new PanelResumen();
    private final PanelLexico panelLexico = new PanelLexico();
    private final PanelSintactico panelSintactico = new PanelSintactico();
    private final PanelSemantico panelSemantico = new PanelSemantico();
    private final Map<String, PanelModulo> modulos = new LinkedHashMap<>();
    private final CardLayout tarjetas = new CardLayout();
    private final JPanel resultados = new JPanel(tarjetas);
    private final JList<String> navegacion = new JList<>(new String[]{"Tokens", "Resumen", "Alfabeto FISIO", "Alfabeto Rust", "Léxico", "Sintáctico", "Semántico"});
    private final JLabel lblArchivo = etiqueta("Sin archivo");
    private final JLabel lblCursor = etiqueta("Línea: 1 | Columna: 1");
    private final JLabel lblEstado = etiqueta("Listo · Ejecuta con F5");
    private final Action ejecutar = accion("Ejecutar [F5]", this::ejecutarAnalisisCompleto);
    private Path archivoActual;
    private CoordinadorAnalisis.Resultado ultimoResultado;
    private SwingWorker<CoordinadorAnalisis.Resultado, Void> tarea;
    private String codigoEnAnalisis;
    private long generacion;

    public VentanaPrincipal() {
        super(TITULO_BASE);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1280, 780);
        setMinimumSize(new Dimension(900, 600));
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(0, 1));
        add(construirToolbar(), BorderLayout.NORTH);
        add(construirCuerpo(), BorderLayout.CENTER);
        add(construirStatusBar(), BorderLayout.SOUTH);
        editor.addCambioListener(this::onCodigoModificado);
        editor.addCursorListener((linea, columna) -> lblCursor.setText("Línea: " + linea + " | Columna: " + columna));
        atajo("F5", "ejecutar", ejecutar);
        atajo("F6", "verSintactico", accion("Sintáctico", () -> seleccionar("Sintáctico")));
        atajo("F7", "verSemantico", accion("Semántico", () -> seleccionar("Semántico")));
        cargarEjemplo("Rust-like - declaraciones y control");
    }

    private static Action accion(String nombre, Runnable trabajo) {
        return new AbstractAction(nombre) {
            @Override public void actionPerformed(ActionEvent e) { trabajo.run(); }
        };
    }
    private void atajo(String tecla, String nombre, Action accion) {
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(tecla), nombre);
        getRootPane().getActionMap().put(nombre, accion);
    }
    private static JLabel etiqueta(String texto) {
        JLabel label = new JLabel(texto);
        label.setFont(TemaFisio.fuenteUi(Font.PLAIN, 12));
        label.setForeground(TemaFisio.FG_MAIN);
        return label;
    }
    private static JPanel panel() {
        JPanel p = new JPanel(new BorderLayout(12, 6));
        p.setBackground(TemaFisio.BG_TOOLBAR);
        return p;
    }
    private static JButton boton(Action accion, boolean principal) {
        JButton b = new JButton(accion);
        b.setFont(TemaFisio.fuenteUi(Font.BOLD, 12));
        b.setBackground(principal ? TemaFisio.ACCENT : TemaFisio.BG_MAIN);
        b.setForeground(principal ? TemaFisio.BG_MAIN : TemaFisio.FG_MAIN);
        b.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(TemaFisio.BORDER),
            BorderFactory.createEmptyBorder(9, 14, 9, 14)));
        return b;
    }
    private JPanel construirToolbar() {
        JPanel barra = panel();
        barra.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        JPanel archivos = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        archivos.setOpaque(false);
        archivos.add(boton(accion("Abrir", this::abrirArchivo), false));
        archivos.add(boton(accion("Guardar", this::guardarArchivo), false));
        archivos.add(boton(accion("Limpiar", this::limpiarTodo), false));
        barra.add(archivos, BorderLayout.WEST);
        JPanel centro = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        centro.setOpaque(false);
        centro.add(boton(ejecutar, true));
        barra.add(centro, BorderLayout.CENTER);
        JComboBox<String> ejemplos = new JComboBox<>();
        ejemplos.addItem("Ejemplos…");
        EjemplosFisio.mapa().keySet().forEach(ejemplos::addItem);
        ejemplos.setPreferredSize(new Dimension(240, 36));
        ejemplos.setToolTipText("Cargar un ejemplo en el editor");
        ejemplos.addActionListener(e -> {
            if (ejemplos.getSelectedIndex() > 0) {
                cargarEjemplo((String) ejemplos.getSelectedItem());
                ejemplos.setSelectedIndex(0);
            }
        });
        barra.add(ejemplos, BorderLayout.EAST);
        return barra;
    }
    private JSplitPane construirCuerpo() {
        JPanel fuente = panel();
        JLabel titulo = etiqueta("CÓDIGO FUENTE");
        titulo.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        fuente.add(titulo, BorderLayout.NORTH);
        fuente.add(editor);
        fuente.setMinimumSize(new Dimension(260, 0));
        agregarModulo("Tokens", tablaTokens);
        agregarModulo("Resumen", panelResumen);
        agregarModulo("Alfabeto FISIO", new PanelAlfabeto(PanelAlfabeto.Version.FISIO));
        agregarModulo("Alfabeto Rust", new PanelAlfabeto(PanelAlfabeto.Version.RUST));
        agregarModulo("Léxico", panelLexico);
        agregarModulo("Sintáctico", panelSintactico);
        agregarModulo("Semántico", panelSemantico);
        modulos.get("Alfabeto FISIO").actualizar(EstadoAnalisis.CORRECTO, "Referencia del lenguaje anterior de física. No requiere ejecución.", true);
        modulos.get("Alfabeto Rust").actualizar(EstadoAnalisis.CORRECTO, "Referencia léxica del subconjunto Rust-like actual. No requiere ejecución.", true);
        navegacion.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        navegacion.setFixedCellHeight(40);
        navegacion.setFont(TemaFisio.fuenteUi(Font.BOLD, 12));
        navegacion.setBackground(TemaFisio.BG_TOOLBAR);
        navegacion.setForeground(TemaFisio.FG_MAIN);
        navegacion.setSelectionBackground(TemaFisio.ACCENT);
        navegacion.setSelectionForeground(TemaFisio.BG_MAIN);
        navegacion.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        navegacion.setPreferredSize(new Dimension(145, 0));
        navegacion.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && navegacion.getSelectedValue() != null) {
                tarjetas.show(resultados, navegacion.getSelectedValue());
                actualizarResaltado();
            }
        });
        JPanel derecha = panel();
        derecha.add(navegacion, BorderLayout.WEST);
        derecha.add(resultados);
        derecha.setMinimumSize(new Dimension(450, 0));
        seleccionar("Resumen");
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, fuente, derecha);
        split.setResizeWeight(.43);
        split.setDividerLocation(480);
        split.setContinuousLayout(true);
        split.setDividerSize(6);
        split.setBorder(BorderFactory.createEmptyBorder());
        return split;
    }
    private void agregarModulo(String nombre, JComponent contenido) {
        PanelModulo modulo = new PanelModulo(nombre, contenido);
        modulos.put(nombre, modulo);
        resultados.add(modulo, nombre);
    }
    private void seleccionar(String nombre) { navegacion.setSelectedValue(nombre, true); }
    private JPanel construirStatusBar() {
        JPanel estado = panel();
        estado.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
        estado.add(lblArchivo, BorderLayout.CENTER);
        estado.add(lblCursor, BorderLayout.EAST);
        estado.add(lblEstado, BorderLayout.SOUTH);
        return estado;
    }
    private void estados(EstadoAnalisis estado, String mensaje, boolean mostrar) {
        modulos.forEach((nombre, modulo) -> {
            if (!nombre.startsWith("Alfabeto ")) modulo.actualizar(estado, mensaje, mostrar);
        });
    }
    private void ejecutarAnalisisCompleto() {
        if (tarea != null) return;
        String codigo = editor.getCodigo();
        long solicitud = ++generacion;
        ultimoResultado = null;
        codigoEnAnalisis = codigo;
        estados(EstadoAnalisis.EN_EJECUCION, "Analizando el código. Puedes seguir navegando entre módulos.", false);
        editor.aplicarResaltado(List.of(), List.of());
        lblEstado.setText("Análisis en curso…");
        ejecutar.setEnabled(false);
        tarea = new SwingWorker<>() {
            @Override protected CoordinadorAnalisis.Resultado doInBackground() {
                return new CoordinadorAnalisis().ejecutar(codigo);
            }
            @Override protected void done() {
                if (solicitud != generacion) return;
                tarea = null;
                ejecutar.setEnabled(true);
                if (!codigo.equals(editor.getCodigo())) { marcarDesactualizado(); return; }
                try {
                    ultimoResultado = get();
                    presentarResultados();
                } catch (CancellationException ex) {
                    marcarDesactualizado();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    errorInterno(ex);
                } catch (ExecutionException ex) {
                    errorInterno(ex.getCause());
                }
            }
        };
        tarea.execute();
    }
    private void errorInterno(Throwable ex) {
        ex.printStackTrace();
        estados(EstadoAnalisis.ERROR_INTERNO, "No se pudo completar el análisis por un error interno. " + ex.getClass().getSimpleName(), false);
        lblEstado.setText("Error interno · No hay resultados completos");
    }
    private void presentarResultados() {
        var r = ultimoResultado;
        tablaTokens.poblar(r.lexico().tokens());
        panelLexico.poblar(r.lexico());
        panelResumen.poblar(r);
        modulos.get("Tokens").actualizar(r.estadoLexico(), r.lexico().tokens().size() + " tokens encontrados. Consulta los errores en Léxico.", true);
        modulos.get("Léxico").actualizar(r.estadoLexico(), "Análisis léxico finalizado.", true);
        modulos.get("Resumen").actualizar(r.errores() == 0 ? r.estadoSemantico() : EstadoAnalisis.CON_ERRORES,
            "Última ejecución: " + r.errores() + " error(es) en las fases realizadas.", true);
        if (r.sintactico() != null) panelSintactico.poblar(r.sintactico().ast(), r.sintactico().errores());
        modulos.get("Sintáctico").actualizar(r.estadoSintactico(), r.sintactico() == null
            ? "No se realizó el análisis sintáctico porque existen errores léxicos. Corrige y vuelve a ejecutar."
            : "Análisis sintáctico finalizado. Se conserva el AST recuperado.", r.sintactico() != null);
        if (r.semantico() != null) panelSemantico.poblar(r.semantico());
        modulos.get("Semántico").actualizar(r.estadoSemantico(), r.semantico() == null
            ? "No se realizó el análisis semántico porque existen errores " + (r.sintactico() == null ? "léxicos." : "sintácticos.") + " Corrige y vuelve a ejecutar."
            : r.semantico().analizado() ? "Análisis semántico finalizado." : r.semantico().motivo(), r.semantico() != null);
        actualizarResaltado();
        lblEstado.setText("Léxico: " + r.estadoLexico().texto + "   |   Sintáctico: " + r.estadoSintactico().texto
            + "   |   Semántico: " + r.estadoSemantico().texto);
    }
    /** Conserva los colores de tokens; solo la fase visible marca sus errores. */
    private void actualizarResaltado() {
        var r = ultimoResultado;
        if (r == null || !r.codigo().equals(editor.getCodigo())
                || modulos.get("Tokens").estadoActual == EstadoAnalisis.DESACTUALIZADO) {
            editor.aplicarResaltado(List.of(), List.of());
            return;
        }
        String modulo = navegacion.getSelectedValue();
        if ("Léxico".equals(modulo)) editor.aplicarResaltado(r.lexico().tokens(), r.lexico().errores());
        else if ("Sintáctico".equals(modulo) && r.sintactico() != null)
            editor.aplicarResaltadoSintactico(r.lexico().tokens(), r.sintactico().errores());
        else if ("Semántico".equals(modulo) && r.semantico() != null && r.semantico().analizado())
            editor.aplicarResaltadoSemantico(r.lexico().tokens(), r.semantico().errores());
        else editor.aplicarResaltado(r.lexico().tokens(), List.of());
    }
    private void onCodigoModificado() {
        if (codigoEnAnalisis != null && !codigoEnAnalisis.equals(editor.getCodigo())) marcarDesactualizado();
    }
    private void marcarDesactualizado() {
        cancelarTrabajo();
        estados(EstadoAnalisis.DESACTUALIZADO, "Los resultados corresponden a una versión anterior del código. Ejecuta nuevamente (F5).", false);
        editor.aplicarResaltado(List.of(), List.of());
        lblEstado.setText("Léxico / Sintáctico / Semántico: desactualizados · Ejecuta nuevamente (F5)");
    }
    private void cancelarTrabajo() {
        ++generacion;
        if (tarea != null) tarea.cancel(true);
        tarea = null;
        ejecutar.setEnabled(true);
    }
    private void limpiarResultados() {
        cancelarTrabajo();
        ultimoResultado = null;
        codigoEnAnalisis = null;
        tablaTokens.limpiar();
        panelResumen.limpiar();
        panelSintactico.limpiar();
        panelSemantico.limpiar();
        estados(EstadoAnalisis.NO_EJECUTADO, "Todavía no se ha realizado el análisis. Pulsa Ejecutar (F5).", false);
        editor.aplicarResaltado(List.of(), List.of());
        lblEstado.setText("Léxico: no ejecutado   |   Sintáctico: no ejecutado   |   Semántico: no ejecutado");
    }
    @Override public void dispose() {
        cancelarTrabajo();
        super.dispose();
    }
    private void abrirArchivo() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Abrir archivo FISIO");
        FileNameExtensionFilter filtroFisio = new FileNameExtensionFilter("Archivos FISIO (*.fisio)", "fisio");
        chooser.addChoosableFileFilter(filtroFisio);
        chooser.setFileFilter(filtroFisio);
        chooser.setAcceptAllFileFilterUsed(true);

        int resultado = chooser.showOpenDialog(this);
        if (resultado != JFileChooser.APPROVE_OPTION) {
            return;
        }

        Path ruta = chooser.getSelectedFile().toPath();
        String contenido;
        try {
            contenido = leerUtf8ConFallback(ruta);
        } catch (IOException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this,
                "No se pudo abrir el archivo:\n" + ruta,
                "Error al abrir", JOptionPane.ERROR_MESSAGE);
            return;
        }

        editor.setCodigo(contenido);
        archivoActual = ruta;
        lblArchivo.setText("Archivo: " + ruta.getFileName());
        setTitle("FISIO — " + ruta.getFileName());
        limpiarResultados();
    }

    private static String leerUtf8ConFallback(Path ruta) throws IOException {
        try {
            return Files.readString(ruta, StandardCharsets.UTF_8);
        } catch (MalformedInputException ex) {
            // Igual que _abrir_archivo en gui.py: si el archivo no es
            // UTF-8 valido, reintenta con latin-1 en vez de fallar.
            return Files.readString(ruta, StandardCharsets.ISO_8859_1);
        }
    }

    private void guardarArchivo() {
        Path destino = archivoActual;

        if (destino == null) {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Guardar código FISIO");
            FileNameExtensionFilter filtroFisio = new FileNameExtensionFilter("Archivos FISIO (*.fisio)", "fisio");
            chooser.addChoosableFileFilter(filtroFisio);
            chooser.setFileFilter(filtroFisio);

            int resultado = chooser.showSaveDialog(this);
            if (resultado != JFileChooser.APPROVE_OPTION) {
                return;
            }

            Path elegido = chooser.getSelectedFile().toPath();
            String nombre = elegido.getFileName().toString();
            if (!nombre.contains(".")) {
                elegido = elegido.resolveSibling(nombre + ".fisio");
            }

            if (Files.exists(elegido)) {
                int confirmacion = JOptionPane.showConfirmDialog(this,
                    "El archivo ya existe:\n" + elegido + "\n¿Deseas sobrescribirlo?",
                    "Confirmar sobrescritura", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                if (confirmacion != JOptionPane.YES_OPTION) {
                    return;
                }
            }
            destino = elegido;
        }

        try {
            Files.writeString(destino, editor.getCodigo(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this,
                "No se pudo guardar el archivo:\n" + destino,
                "Error al guardar", JOptionPane.ERROR_MESSAGE);
            return;
        }

        archivoActual = destino;
        lblArchivo.setText("Archivo: " + destino.getFileName());
        // gui.py tampoco actualiza el titulo de la ventana en
        // _guardar_archivo (solo en _abrir_archivo/_cargar_ejemplo) --
        // se reproduce esa misma omision tal cual, no se "corrige".
        JOptionPane.showMessageDialog(this,
            "Archivo guardado:\n" + destino,
            "Guardado", JOptionPane.INFORMATION_MESSAGE);
    }

    // ── Acciones: Ejemplos / Limpiar ──────────────────────────
    private void cargarEjemplo(String nombre) {
        editor.setCodigo(EjemplosFisio.codigoDe(nombre));
        archivoActual = null;
        lblArchivo.setText("Ejemplo: " + nombre);
        setTitle("FISIO — Ejemplo: " + nombre);
        limpiarResultados();
    }

    private void limpiarTodo() {
        editor.limpiar();
        archivoActual = null;
        lblArchivo.setText("Sin archivo");
        setTitle(TITULO_BASE);
        limpiarResultados();
    }

}
