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
import fisio.optimization.OptimizadorCodigo;
import fisio.optimization.ResultadoOptimizacion;

/** Documento y presentación en EDT; el coordinador ejecuta las fases en segundo plano. */
public final class VentanaPrincipal extends JFrame {
    private static final String TITULO_BASE = "FISIO — Analizador de código";
    private static final System.Logger LOG = System.getLogger(VentanaPrincipal.class.getName());
    private final EditorCodigoPanel editor = new EditorCodigoPanel();
    private final TablaTokensPanel tablaTokens = new TablaTokensPanel();
    private final PanelResumen panelResumen = new PanelResumen();
    private final PanelLexico panelLexico = new PanelLexico();
    private final PanelSintactico panelSintactico = new PanelSintactico();
    private final PanelSemantico panelSemantico = new PanelSemantico();
    private final PanelCodigoIntermedio panelCodigoIntermedio = new PanelCodigoIntermedio();
    private final PanelOptimizacion panelOptimizacion = new PanelOptimizacion();
    private final Map<String, PanelModulo> modulos = new LinkedHashMap<>();
    private final CardLayout tarjetas = new CardLayout();
    private final JPanel resultados = new JPanel(tarjetas);
    private final JList<String> navegacion = new JList<>(new String[]{"Tokens", "Resumen", "Alfabeto FISIO",
        "Alfabeto Rust", "Léxico", "Sintáctico", "Semántico", "Código Intermedio (CI)", "Optimización"});
    private final JComboBox<String> selectorEjemplos = new JComboBox<>();
    private final JLabel lblArchivo = etiqueta("Sin archivo");
    private final JLabel lblCursor = etiqueta("Línea: 1 | Columna: 1");
    private final JLabel lblEstado = etiqueta("Listo · Ejecuta con F5");
    private final Action ejecutar = accion("Analizar  F5", this::ejecutarAnalisisCompleto);
    private final Action optimizar = accion("Optimizar  F9", this::ejecutarOptimizacion);
    private Path archivoActual;
    private CoordinadorAnalisis.Resultado ultimoResultado;
    private SwingWorker<CoordinadorAnalisis.Resultado, Void> tarea;
    private SwingWorker<ResultadoOptimizacion, Void> tareaOptimizacion;
    private ResultadoOptimizacion ultimaOptimizacion;
    private PanelOptimizacion.CambioVista cambioOptimizacionSeleccionado;
    private Path archivoAntesOptimizacion;
    private DialogoComparacion dialogoComparacion;
    private String codigoEnAnalisis;
    private long generacion;
    private long generacionOptimizacion;

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
        panelOptimizacion.addSeleccionCambioListener(this::seleccionarCambioOptimizacion);
        panelOptimizacion.configurarAcciones(this::mostrarComparacion, this::restaurarOriginal,
            this::guardarOptimizado, this::exportarReporte);
        atajo("F5", "ejecutar", ejecutar);
        atajo("F6", "verSintactico", accion("Sintáctico", () -> seleccionar("Sintáctico")));
        atajo("F7", "verSemantico", accion("Semántico", () -> seleccionar("Semántico")));
        atajo("F8", "verCodigoIntermedio", accion("Código Intermedio", () -> seleccionar("Código Intermedio (CI)")));
        atajo("F9", "optimizar", optimizar);
        cargarEjemplo("Fundamentos");
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
    private static JButton boton(Action accion, boolean principal, String ayuda) {
        JButton b = new JButton(accion);
        b.setFont(TemaFisio.fuenteUi(Font.BOLD, 12));
        b.setBackground(principal ? TemaFisio.ACCENT : TemaFisio.BG_MAIN);
        b.setForeground(principal ? TemaFisio.BG_MAIN : TemaFisio.FG_MAIN);
        b.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(
            principal ? TemaFisio.ACCENT : TemaFisio.BORDER),
            BorderFactory.createEmptyBorder(9, 14, 9, 14)));
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setToolTipText(ayuda);
        return b;
    }
    private JPanel construirToolbar() {
        JPanel barra = panel();
        barra.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, TemaFisio.BORDER),
            BorderFactory.createEmptyBorder(10, 12, 10, 12)));

        JPanel archivos = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        archivos.setOpaque(false);
        archivos.add(boton(accion("Abrir", this::abrirArchivo), false, "Abrir un archivo .fisio"));
        archivos.add(boton(accion("Guardar", this::guardarArchivo), false, "Guardar el código actual"));
        JSeparator separador = new JSeparator(SwingConstants.VERTICAL);
        separador.setPreferredSize(new Dimension(1, 30));
        archivos.add(Box.createHorizontalStrut(4));
        archivos.add(separador);
        archivos.add(Box.createHorizontalStrut(4));
        archivos.add(boton(accion("Limpiar", this::limpiarTodo), false,
            "Vaciar el editor y los resultados"));
        barra.add(archivos, BorderLayout.WEST);

        JPanel acciones = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        acciones.setOpaque(false);
        JLabel etiquetaEjemplo = etiqueta("Ejemplo:");
        etiquetaEjemplo.setForeground(TemaFisio.FG_DIM);
        acciones.add(etiquetaEjemplo);
        selectorEjemplos.addItem("Seleccionar…");
        EjemplosFisio.mapa().keySet().forEach(selectorEjemplos::addItem);
        selectorEjemplos.setPreferredSize(new Dimension(190, 36));
        selectorEjemplos.setFont(TemaFisio.fuenteUi(Font.PLAIN, 12));
        selectorEjemplos.setBackground(TemaFisio.BG_MAIN);
        selectorEjemplos.setForeground(TemaFisio.FG_MAIN);
        selectorEjemplos.setToolTipText("Reemplazar el editor con un ejemplo actual de FISIO");
        selectorEjemplos.addActionListener(e -> {
            if (selectorEjemplos.getSelectedIndex() > 0) {
                cargarEjemplo((String) selectorEjemplos.getSelectedItem());
                selectorEjemplos.setSelectedIndex(0);
            }
        });
        acciones.add(selectorEjemplos);
        acciones.add(Box.createHorizontalStrut(6));
        acciones.add(boton(optimizar, false, "Aplicar optimizaciones seguras y explicar cada cambio"));
        acciones.add(boton(ejecutar, true, "Ejecutar todas las fases del analizador"));
        barra.add(acciones, BorderLayout.EAST);
        return barra;
    }
    private JSplitPane construirCuerpo() {
        JPanel fuente = panel();
        JLabel titulo = etiqueta("CÓDIGO FISIO");
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
        agregarModulo("Código Intermedio (CI)", panelCodigoIntermedio);
        agregarModulo("Optimización", panelOptimizacion);
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
        navegacion.setPreferredSize(new Dimension(185, 0));
        navegacion.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && navegacion.getSelectedValue() != null) {
                tarjetas.show(resultados, navegacion.getSelectedValue());
                actualizarResaltado();
            }
        });
        JPanel derecha = panel();
        derecha.add(navegacion, BorderLayout.WEST);
        derecha.add(resultados);
        derecha.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, TemaFisio.BORDER));
        derecha.setMinimumSize(new Dimension(450, 0));
        seleccionar("Resumen");
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, fuente, derecha);
        split.setResizeWeight(.46);
        split.setDividerLocation(520);
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
        estado.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, TemaFisio.BORDER),
            BorderFactory.createEmptyBorder(7, 12, 7, 12)));
        lblArchivo.setForeground(TemaFisio.FG_DIM);
        lblCursor.setForeground(TemaFisio.FG_DIM);
        lblEstado.setHorizontalAlignment(SwingConstants.CENTER);
        estado.add(lblArchivo, BorderLayout.WEST);
        estado.add(lblEstado, BorderLayout.CENTER);
        estado.add(lblCursor, BorderLayout.EAST);
        return estado;
    }
    private void estados(EstadoAnalisis estado, String mensaje, boolean mostrar) {
        modulos.forEach((nombre, modulo) -> {
            if (!nombre.startsWith("Alfabeto ") && !nombre.equals("Optimización"))
                modulo.actualizar(estado, mensaje, mostrar);
        });
    }
    private void ejecutarAnalisisCompleto() {
        if (tarea != null || tareaOptimizacion != null) return;
        String codigo = editor.getCodigo();
        long solicitud = ++generacion;
        ultimoResultado = null;
        codigoEnAnalisis = codigo;
        estados(EstadoAnalisis.EN_EJECUCION, "Analizando el código. Puedes seguir navegando entre módulos.", false);
        editor.aplicarResaltado(List.of(), List.of());
        lblEstado.setText("Análisis en curso…");
        ejecutar.setEnabled(false);
        optimizar.setEnabled(false);
        tarea = new SwingWorker<>() {
            @Override protected CoordinadorAnalisis.Resultado doInBackground() {
                return new CoordinadorAnalisis().ejecutar(codigo);
            }
            @Override protected void done() {
                if (solicitud != generacion) return;
                tarea = null;
                ejecutar.setEnabled(true);
                optimizar.setEnabled(true);
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

    private void ejecutarOptimizacion() {
        if (tarea != null || tareaOptimizacion != null) return;
        String codigo = editor.getCodigo();
        if (codigo.isBlank()) {
            modulos.get("Optimización").actualizar(EstadoAnalisis.CON_ERRORES,
                "El editor está vacío. Escribe o selecciona un ejemplo antes de optimizar.", false);
            seleccionar("Optimización");
            return;
        }
        cancelarTrabajo();
        limpiarResultadosAnalisis();
        long solicitud = ++generacionOptimizacion;
        modulos.get("Optimización").actualizar(EstadoAnalisis.EN_EJECUCION,
            "Validando y optimizando una copia del código. El archivo original no se modificará.", false);
        seleccionar("Optimización");
        lblEstado.setText("Optimización en curso…");
        optimizar.setEnabled(false);
        ejecutar.setEnabled(false);
        tareaOptimizacion = new SwingWorker<>() {
            @Override protected ResultadoOptimizacion doInBackground() {
                return OptimizadorCodigo.optimizar(codigo);
            }
            @Override protected void done() {
                if (solicitud != generacionOptimizacion) return;
                tareaOptimizacion = null;
                optimizar.setEnabled(true);
                ejecutar.setEnabled(true);
                if (!codigo.equals(editor.getCodigo())) {
                    modulos.get("Optimización").actualizar(EstadoAnalisis.DESACTUALIZADO,
                        "El código cambió mientras se optimizaba. Ejecuta nuevamente F9.", false);
                    return;
                }
                try {
                    ResultadoOptimizacion resultado = get();
                    presentarOptimizacion(resultado);
                } catch (CancellationException ex) {
                    modulos.get("Optimización").actualizar(EstadoAnalisis.DESACTUALIZADO,
                        "La optimización fue cancelada porque cambió el documento.", false);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    errorOptimizacion(ex);
                } catch (ExecutionException ex) {
                    errorOptimizacion(ex.getCause());
                }
            }
        };
        tareaOptimizacion.execute();
    }

    private void presentarOptimizacion(ResultadoOptimizacion resultado) {
        ultimaOptimizacion = resultado;
        cambioOptimizacionSeleccionado = null;
        if (!resultado.exitoso()) {
            panelOptimizacion.poblar(resultado);
            modulos.get("Optimización").actualizar(EstadoAnalisis.CON_ERRORES,
                resultado.motivo(), true);
            seleccionar("Optimización");
            lblEstado.setText("Optimización bloqueada · " + resultado.motivo());
            return;
        }
        archivoAntesOptimizacion = archivoActual;
        editor.setCodigo(resultado.codigoOptimizado());
        panelOptimizacion.poblar(resultado);
        archivoActual = null;
        lblArchivo.setText("Código optimizado · sin guardar");
        setTitle("FISIO — Código optimizado");
        modulos.get("Optimización").actualizar(EstadoAnalisis.CORRECTO,
            resultado.cambios().size() + " cambio(s). Tiempo medido: optimizador, no ejecución. "
                + "Selecciona un cambio para revisarlo.", true);
        seleccionar("Optimización");
        lblEstado.setText("Optimización completada · " + resultado.cambios().size()
            + " cambio(s) · El original se conserva para restaurarlo");
    }

    private void seleccionarCambioOptimizacion(PanelOptimizacion.CambioVista cambio) {
        cambioOptimizacionSeleccionado = cambio;
        if (cambio.lineaOptimizadaInicio() > 0)
            editor.seleccionarLineas(cambio.lineaOptimizadaInicio(), cambio.lineaOptimizadaFin());
        else editor.limpiarSeleccionOptimizacion();
        if (dialogoComparacion != null && dialogoComparacion.isVisible())
            dialogoComparacion.seleccionarCambio(cambio.numero());
    }

    private void mostrarComparacion() {
        if (ultimaOptimizacion == null || !ultimaOptimizacion.exitoso()) return;
        if (dialogoComparacion == null) dialogoComparacion = new DialogoComparacion(this);
        dialogoComparacion.mostrar(ultimaOptimizacion.codigoOriginal(), ultimaOptimizacion.codigoOptimizado(),
            panelOptimizacion.getCambios());
    }

    private void restaurarOriginal() {
        if (ultimaOptimizacion == null || !ultimaOptimizacion.exitoso()) return;
        String original = ultimaOptimizacion.codigoOriginal();
        Path archivoOriginal = archivoAntesOptimizacion;
        limpiarResultados();
        editor.setCodigo(original);
        archivoActual = archivoOriginal;
        lblArchivo.setText(archivoActual == null ? "Código original restaurado"
            : "Archivo: " + archivoActual.getFileName());
        setTitle(archivoActual == null ? TITULO_BASE : "FISIO — " + archivoActual.getFileName());
        lblEstado.setText("Código original restaurado · Ejecuta F5 o vuelve a optimizar con F9");
    }

    private void guardarOptimizado() {
        if (ultimaOptimizacion != null && ultimaOptimizacion.exitoso()) guardarArchivo();
    }

    private void exportarReporte() {
        if (ultimaOptimizacion == null) return;
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Exportar reporte de optimización");
        chooser.setSelectedFile(new java.io.File("reporte_optimizacion.txt"));
        chooser.setFileFilter(new FileNameExtensionFilter("Reporte de texto (*.txt)", "txt"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        Path destino = chooser.getSelectedFile().toPath();
        if (!destino.getFileName().toString().contains("."))
            destino = destino.resolveSibling(destino.getFileName() + ".txt");
        if (Files.exists(destino) && JOptionPane.showConfirmDialog(this,
                "El reporte ya existe:\n" + destino + "\n¿Deseas sobrescribirlo?",
                "Confirmar sobrescritura", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE)
                != JOptionPane.YES_OPTION) return;
        try {
            Files.writeString(destino, ultimaOptimizacion.reporteTexto(), StandardCharsets.UTF_8);
            JOptionPane.showMessageDialog(this, "Reporte guardado:\n" + destino,
                "Reporte exportado", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException ex) {
            LOG.log(System.Logger.Level.ERROR, "No se pudo exportar " + destino, ex);
            JOptionPane.showMessageDialog(this, "No se pudo guardar el reporte:\n" + destino,
                "Error al exportar", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void errorOptimizacion(Throwable ex) {
        Throwable causa = ex == null ? new IllegalStateException("Causa no disponible") : ex;
        LOG.log(System.Logger.Level.ERROR, "No se pudo completar la optimización", causa);
        modulos.get("Optimización").actualizar(EstadoAnalisis.ERROR_INTERNO,
            "No se pudo optimizar por un error interno. " + causa.getClass().getSimpleName(), false);
        lblEstado.setText("Error interno durante la optimización");
    }
    private void errorInterno(Throwable ex) {
        Throwable causa = ex == null ? new IllegalStateException("Causa no disponible") : ex;
        LOG.log(System.Logger.Level.ERROR, "No se pudo completar el análisis", causa);
        estados(EstadoAnalisis.ERROR_INTERNO,
            "No se pudo completar el análisis por un error interno. " + causa.getClass().getSimpleName(), false);
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
        panelCodigoIntermedio.poblar(r.codigoIntermedio());
        modulos.get("Código Intermedio (CI)").actualizar(r.estadoCodigoIntermedio(),
            r.codigoIntermedio().generado()
                ? r.codigoIntermedio().expresiones().size() + " expresión(es) aritméticas procesadas."
                : r.codigoIntermedio().motivo(), true);
        actualizarResaltado();
        lblEstado.setText("Léxico: " + r.estadoLexico().texto + "   |   Sintáctico: " + r.estadoSintactico().texto
            + "   |   Semántico: " + r.estadoSemantico().texto + "   |   CI: " + r.estadoCodigoIntermedio().texto);
    }
    /** Conserva los colores de tokens; solo la fase visible marca sus errores. */
    private void actualizarResaltado() {
        String modulo = navegacion.getSelectedValue();
        if ("Optimización".equals(modulo) && cambioOptimizacionSeleccionado != null
                && cambioOptimizacionSeleccionado.lineaOptimizadaInicio() > 0) {
            editor.seleccionarLineas(cambioOptimizacionSeleccionado.lineaOptimizadaInicio(),
                cambioOptimizacionSeleccionado.lineaOptimizadaFin());
        } else if (!"Optimización".equals(modulo)) {
            editor.limpiarSeleccionOptimizacion();
        }
        var r = ultimoResultado;
        if (r == null || !r.codigo().equals(editor.getCodigo())
                || modulos.get("Tokens").estadoActual == EstadoAnalisis.DESACTUALIZADO) {
            editor.aplicarResaltado(List.of(), List.of());
            return;
        }
        if ("Léxico".equals(modulo)) editor.aplicarResaltado(r.lexico().tokens(), r.lexico().errores());
        else if ("Sintáctico".equals(modulo) && r.sintactico() != null)
            editor.aplicarResaltadoSintactico(r.lexico().tokens(), r.sintactico().errores());
        else if ("Semántico".equals(modulo) && r.semantico() != null && r.semantico().analizado())
            editor.aplicarResaltadoSemantico(r.lexico().tokens(), r.semantico().errores());
        else editor.aplicarResaltado(r.lexico().tokens(), List.of());
    }
    private void onCodigoModificado() {
        if (codigoEnAnalisis != null && !codigoEnAnalisis.equals(editor.getCodigo())) marcarDesactualizado();
        if (ultimaOptimizacion != null && ultimaOptimizacion.exitoso()
                && !ultimaOptimizacion.codigoOptimizado().equals(editor.getCodigo())) {
            modulos.get("Optimización").actualizar(EstadoAnalisis.DESACTUALIZADO,
                "El código optimizado fue editado. El reporte conserva la última optimización aplicada.", true);
        }
    }
    private void marcarDesactualizado() {
        cancelarTrabajo();
        estados(EstadoAnalisis.DESACTUALIZADO, "Los resultados corresponden a una versión anterior del código. Ejecuta nuevamente (F5).", false);
        editor.aplicarResaltado(List.of(), List.of());
        lblEstado.setText("Léxico / Sintáctico / Semántico / CI: desactualizados · Ejecuta nuevamente (F5)");
    }
    private void cancelarTrabajo() {
        ++generacion;
        if (tarea != null) tarea.cancel(true);
        tarea = null;
        ejecutar.setEnabled(true);
        if (tareaOptimizacion == null) optimizar.setEnabled(true);
    }

    private void cancelarOptimizacion() {
        ++generacionOptimizacion;
        if (tareaOptimizacion != null) tareaOptimizacion.cancel(true);
        tareaOptimizacion = null;
        optimizar.setEnabled(true);
        ejecutar.setEnabled(true);
    }

    private void limpiarResultadosAnalisis() {
        cancelarTrabajo();
        ultimoResultado = null;
        codigoEnAnalisis = null;
        tablaTokens.limpiar();
        panelLexico.limpiar();
        panelResumen.limpiar();
        panelSintactico.limpiar();
        panelSemantico.limpiar();
        panelCodigoIntermedio.limpiar();
        estados(EstadoAnalisis.NO_EJECUTADO, "Todavía no se ha realizado el análisis. Pulsa Ejecutar (F5).", false);
        editor.aplicarResaltado(List.of(), List.of());
        lblEstado.setText("Léxico: no ejecutado   |   Sintáctico: no ejecutado   |   Semántico: no ejecutado   |   CI: no ejecutado");
    }

    private void limpiarOptimizacion() {
        cancelarOptimizacion();
        ultimaOptimizacion = null;
        cambioOptimizacionSeleccionado = null;
        archivoAntesOptimizacion = null;
        panelOptimizacion.limpiar();
        modulos.get("Optimización").actualizar(EstadoAnalisis.NO_EJECUTADO,
            "Todavía no se ha optimizado el código. Pulsa Optimizar (F9).", false);
        editor.limpiarSeleccionOptimizacion();
        if (dialogoComparacion != null) dialogoComparacion.setVisible(false);
    }

    private void limpiarResultados() {
        limpiarResultadosAnalisis();
        limpiarOptimizacion();
    }

    @Override public void dispose() {
        cancelarTrabajo();
        cancelarOptimizacion();
        if (dialogoComparacion != null) dialogoComparacion.dispose();
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
            LOG.log(System.Logger.Level.ERROR, "No se pudo abrir el archivo " + ruta, ex);
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
            // Admite archivos heredados codificados en Latin-1.
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
            LOG.log(System.Logger.Level.ERROR, "No se pudo guardar el archivo " + destino, ex);
            JOptionPane.showMessageDialog(this,
                "No se pudo guardar el archivo:\n" + destino,
                "Error al guardar", JOptionPane.ERROR_MESSAGE);
            return;
        }

        archivoActual = destino;
        lblArchivo.setText("Archivo: " + destino.getFileName());
        setTitle("FISIO — " + destino.getFileName());
        JOptionPane.showMessageDialog(this,
            "Archivo guardado:\n" + destino,
            "Guardado", JOptionPane.INFORMATION_MESSAGE);
    }

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
