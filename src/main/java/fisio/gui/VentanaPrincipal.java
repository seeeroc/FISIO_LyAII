package fisio.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.KeyStroke;
import javax.swing.filechooser.FileNameExtensionFilter;

import fisio.analysis.FisioLexerRunner;
import fisio.analysis.FisioParserRunner;
import fisio.analysis.ResultadoLexico;
import fisio.analysis.ResultadoSintactico;
import fisio.token.ErrorLexico;
import fisio.token.TokenFisio;

/**
 * Ventana principal de FISIO, equivalente a AplicacionFISIO (gui.py).
 *
 * Paso 6D — "Léxico [F5]" (boton y tecla) ejecuta FisioLexerRunner y
 * alimenta tabla de tokens, resumen, resaltado del editor y status bar;
 * "Sintáctico [F6]" (boton y tecla) ejecuta FisioParserRunner y alimenta
 * PanelSintactico (AST/errores) y el resaltado del editor. NO se
 * implementa analisis semantico, ejecucion, simulacion ni graficas.
 */
public final class VentanaPrincipal extends JFrame {

    private static final String PLACEHOLDER_EJEMPLOS = "Ejemplos…";
    // Fase 2: el ejemplo inicial anterior ("MRU completo") era del lenguaje
    // de fisica y ya no es compatible con la gramatica actual (Rust-like);
    // se cambia SOLO esta constante para que la app arranque mostrando algo
    // que F5+F6 puedan analizar de verdad. El ejemplo viejo NO se borro,
    // sigue disponible en el combo de Ejemplos.
    // Revision general Rust-like: "Fase 2: declaraciones y control" se
    // renombro a "Rust-like - declaraciones y control" en EjemplosFisio
    // (mismo contenido) -- este nombre se actualiza junto con ese rename.
    private static final String EJEMPLO_INICIAL_NOMBRE = "Rust-like - declaraciones y control";
    private static final int INDICE_TAB_ALFABETO = 3;

    // gui.py usa "FISIO — Analizador Léxico y Sintáctico" como titulo base
    // (_configurar_ventana / _limpiar_todo) -- no un simple "FISIO".
    private static final String TITULO_BASE = "FISIO — Analizador Léxico y Sintáctico";

    private EditorCodigoPanel editor;
    private JTabbedPane tabs;
    private TablaTokensPanel tablaTokens;
    private PanelResumen panelResumen;
    private PanelSintactico panelSintactico;

    private JButton btnSintactico;
    private JComboBox<String> comboEjemplos;

    private JLabel lblArchivo;
    private JLabel lblTokens;
    private JLabel lblErrores;
    private JLabel lblSintaxis;
    private JLabel lblCursor;
    private JLabel lblEstado;

    // Estado del "documento" actual -- mutuamente excluyentes: si
    // archivoActual != null, el codigo vino de un archivo real; si es
    // null y nombreEjemploActual != null, vino de un ejemplo embebido; si
    // ambos son null, es "Sin archivo" (editor nuevo/limpiado).
    private Path archivoActual;
    private String nombreEjemploActual;

    // true si el usuario (o una carga programatica) modifico el editor
    // desde el ultimo setCodigo() "oficial".
    private boolean editorModificado;

    // Equivalente a _codigo_analizado_lexico en gui.py: el String exacto
    // que F5 analizo la ultima vez, o null si no se ha ejecutado F5 desde
    // el ultimo setCodigo()/limpiar(). _lexico_realizado de Python es
    // redundante con esto (siempre lexico_realizado == codigoAnalizadoLexico
    // != null), asi que no se duplica como campo aparte.
    private String codigoAnalizadoLexico;

    // Equivalentes minimos de self._tokens / self._errores en gui.py:
    // NO se recalculan en F6 (arquitectura aprobada: F6 re-tokeniza
    // internamente SOLO dentro de FisioParserRunner para el parseo; para
    // la GUI, el resaltado y el chequeo defensivo de "0 errores lexicos"
    // se reutiliza el resultado de F5, exactamente como Python reutiliza
    // self._tokens en editor.resaltar(self._tokens, ...) dentro de
    // _analizar_sintactico). No existe codigoAnalizadoSintactico aparte:
    // gui.py tampoco lo tiene, solo revalida contra _codigo_analizado_lexico.
    private List<TokenFisio> tokensAnalizados = List.of();
    private List<ErrorLexico> erroresLexicosAnalizados = List.of();

    public VentanaPrincipal() {
        super("FISIO — Ejemplo: " + EJEMPLO_INICIAL_NOMBRE);
        configurarVentana();
        construirUi();
        configurarAtajos();

        nombreEjemploActual = EJEMPLO_INICIAL_NOMBRE;
        archivoActual = null;
        editor.setCodigo(EjemplosFisio.codigoDe(EJEMPLO_INICIAL_NOMBRE));
        editorModificado = false;
        lblArchivo.setText("Ejemplo: " + EJEMPLO_INICIAL_NOMBRE);
    }

    private void configurarVentana() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1280, 780);
        setMinimumSize(new Dimension(900, 600));
        setLocationRelativeTo(null);
        getContentPane().setBackground(TemaFisio.BG_MAIN);
    }

    private void construirUi() {
        setLayout(new BorderLayout());
        add(construirToolbar(), BorderLayout.NORTH);
        add(construirCuerpo(), BorderLayout.CENTER);
        add(construirStatusBar(), BorderLayout.SOUTH);
    }

    // F5/F6 deben ejecutar EXACTAMENTE la misma accion que sus botones --
    // ambos atajos llaman al mismo metodo que el boton, sin duplicar
    // logica (igual que gui.py: self.bind("<F5>", ...)/self.bind("<F6>",
    // ...)). El binding de F6 existe siempre (no se quita ni se
    // condiciona al estado de btnSintactico); es analizarSintactico() la
    // que decide internamente si hay algo que hacer, igual que en Python.
    private void configurarAtajos() {
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
            .put(KeyStroke.getKeyStroke("F5"), "analizarLexico");
        getRootPane().getActionMap().put("analizarLexico", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                analizarLexico();
            }
        });

        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
            .put(KeyStroke.getKeyStroke("F6"), "analizarSintactico");
        getRootPane().getActionMap().put("analizarSintactico", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                analizarSintactico();
            }
        });
    }

    // ── Toolbar ───────────────────────────────────────────────
    private JPanel construirToolbar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(TemaFisio.BG_TOOLBAR);
        bar.setPreferredSize(new Dimension(0, 46));

        JPanel izquierda = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 6));
        izquierda.setBackground(TemaFisio.BG_TOOLBAR);

        JButton btnAbrir = crearBotonToolbar(izquierda, "Abrir", null, null);
        btnAbrir.addActionListener(e -> abrirArchivo());

        JButton btnGuardar = crearBotonToolbar(izquierda, "Guardar", null, null);
        btnGuardar.addActionListener(e -> guardarArchivo());

        JButton btnAlfabeto = crearBotonToolbar(izquierda, "Alfabeto FISIO", null, null);
        btnAlfabeto.addActionListener(e -> tabs.setSelectedIndex(INDICE_TAB_ALFABETO));

        izquierda.add(separadorVerticalToolbar());
        JButton btnLexico = crearBotonToolbar(izquierda, "Léxico [F5]", TemaFisio.BG_MAIN, TemaFisio.ACCENT);
        btnLexico.addActionListener(e -> analizarLexico());
        btnSintactico = crearBotonToolbar(
            izquierda, "Sintáctico [F6]", TemaFisio.FG_MAIN, TemaFisio.BG_TOOLBAR);
        btnSintactico.addActionListener(e -> analizarSintactico());
        btnSintactico.setEnabled(false); // mismo estado inicial que _set_sintactico_disponible(False)
        izquierda.add(separadorVerticalToolbar());

        JButton btnLimpiar = crearBotonToolbar(izquierda, "Limpiar", null, null);
        btnLimpiar.addActionListener(e -> limpiarTodo());

        comboEjemplos = new JComboBox<>();
        comboEjemplos.addItem(PLACEHOLDER_EJEMPLOS);
        for (String nombre : EjemplosFisio.mapa().keySet()) {
            comboEjemplos.addItem(nombre);
        }
        comboEjemplos.setFont(TemaFisio.fuenteUi(Font.PLAIN, 10));
        comboEjemplos.setBackground(TemaFisio.BG_TOOLBAR);
        comboEjemplos.setForeground(TemaFisio.FG_MAIN);
        comboEjemplos.addActionListener(e -> {
            Object seleccionado = comboEjemplos.getSelectedItem();
            if (seleccionado == null || PLACEHOLDER_EJEMPLOS.equals(seleccionado)) {
                return;
            }
            cargarEjemplo((String) seleccionado);
            // gui.py restablece el OptionMenu al placeholder tras cargar
            // (_ejemplo_var.set("Ejemplos…") en _cargar_ejemplo) -- el
            // guard de arriba evita un bucle al disparar este mismo
            // listener de nuevo con el placeholder seleccionado.
            comboEjemplos.setSelectedItem(PLACEHOLDER_EJEMPLOS);
        });
        izquierda.add(comboEjemplos);

        bar.add(izquierda, BorderLayout.WEST);

        JLabel titulo = new JLabel("FISIO  Analizador Léxico y Sintáctico");
        titulo.setForeground(TemaFisio.ACCENT);
        titulo.setFont(TemaFisio.fuenteUi(Font.BOLD, 12));
        titulo.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 16));
        bar.add(titulo, BorderLayout.EAST);

        JPanel contenedor = new JPanel(new BorderLayout());
        contenedor.add(bar, BorderLayout.CENTER);
        contenedor.add(separadorHorizontal(), BorderLayout.SOUTH);
        return contenedor;
    }

    private JButton crearBotonToolbar(JPanel contenedor, String texto, Color fg, Color bg) {
        Color fgFinal = fg != null ? fg : TemaFisio.FG_MAIN;
        Color bgFinal = bg != null ? bg : TemaFisio.BG_TOOLBAR;

        JButton boton = new JButton(texto);
        boton.setForeground(fgFinal);
        boton.setBackground(bgFinal);
        boton.setFont(TemaFisio.fuenteUi(Font.BOLD, 10));
        boton.setFocusPainted(false);
        boton.setBorderPainted(false);
        boton.setOpaque(true);
        boton.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
        boton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        boton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (boton.isEnabled()) {
                    boton.setBackground(TemaFisio.BORDER);
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                boton.setBackground(bgFinal);
            }
        });
        contenedor.add(boton);
        return boton;
    }

    // ── Cuerpo principal ──────────────────────────────────────
    private JSplitPane construirCuerpo() {
        JPanel izquierda = new JPanel(new BorderLayout());
        izquierda.setBackground(TemaFisio.BG_EDITOR);

        JPanel headerIzq = new JPanel(new BorderLayout());
        headerIzq.setBackground(TemaFisio.BG_TOOLBAR);
        JLabel lblHeaderCodigo = new JLabel("CÓDIGO FUENTE");
        lblHeaderCodigo.setForeground(TemaFisio.ACCENT);
        lblHeaderCodigo.setFont(TemaFisio.fuenteUi(Font.BOLD, 9));
        lblHeaderCodigo.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 0));
        headerIzq.add(lblHeaderCodigo, BorderLayout.CENTER);
        headerIzq.add(separadorHorizontal(), BorderLayout.SOUTH);
        izquierda.add(headerIzq, BorderLayout.NORTH);

        editor = new EditorCodigoPanel();
        editor.addCambioListener(this::onCodigoModificado);
        editor.addCursorListener(this::onCursorMovido);
        izquierda.add(editor, BorderLayout.CENTER);
        izquierda.setMinimumSize(new Dimension(340, 0));

        tabs = new JTabbedPane();
        tabs.setBackground(TemaFisio.BG_TOOLBAR);
        tabs.setForeground(TemaFisio.FG_DIM);
        tabs.setFont(TemaFisio.fuenteUi(Font.BOLD, 10));

        tablaTokens = new TablaTokensPanel();
        panelResumen = new PanelResumen();
        panelSintactico = new PanelSintactico();
        PanelAlfabeto panelAlfabeto = new PanelAlfabeto();

        String[] nombresTabs = {"Tokens", "Resumen", "Sintaxis", "Alfabeto"};
        JComponent[] paneles = {tablaTokens, panelResumen, panelSintactico, panelAlfabeto};
        List<JLabel> etiquetasTabs = new ArrayList<>();
        for (int i = 0; i < nombresTabs.length; i++) {
            tabs.addTab(null, paneles[i]);
            JLabel etiqueta = crearEtiquetaTab(nombresTabs[i]);
            etiquetasTabs.add(etiqueta);
            tabs.setTabComponentAt(i, etiqueta);
        }
        actualizarEstiloTabs(tabs, etiquetasTabs);
        tabs.addChangeListener(e -> actualizarEstiloTabs(tabs, etiquetasTabs));
        tabs.setMinimumSize(new Dimension(340, 0));

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, izquierda, tabs);
        split.setContinuousLayout(true);
        split.setDividerSize(4);
        split.setBorder(BorderFactory.createEmptyBorder());
        split.setBackground(TemaFisio.BORDER);
        split.setResizeWeight(0.45);
        split.setDividerLocation(560);
        return split;
    }

    // Componente de tab propio en vez del texto nativo de JTabbedPane: el
    // resaltado por defecto de la tab seleccionada (chrome del L&F) deja el
    // texto con bajo contraste; con un JLabel propio controlamos
    // directamente fondo/texto de la tab activa vs. inactiva.
    private static JLabel crearEtiquetaTab(String texto) {
        JLabel lbl = new JLabel(texto);
        lbl.setOpaque(true);
        lbl.setFont(TemaFisio.fuenteUi(Font.BOLD, 10));
        lbl.setBorder(BorderFactory.createEmptyBorder(6, 14, 6, 14));
        return lbl;
    }

    private static void actualizarEstiloTabs(JTabbedPane tabs, List<JLabel> etiquetas) {
        int seleccionado = tabs.getSelectedIndex();
        for (int i = 0; i < etiquetas.size(); i++) {
            JLabel lbl = etiquetas.get(i);
            boolean activa = i == seleccionado;
            lbl.setBackground(activa ? TemaFisio.ACCENT : TemaFisio.BG_TOOLBAR);
            lbl.setForeground(activa ? TemaFisio.BG_MAIN : TemaFisio.FG_DIM);
        }
    }

    // ── Barra de estado ───────────────────────────────────────
    private JPanel construirStatusBar() {
        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBackground(TemaFisio.BG_STATUS);
        statusBar.setPreferredSize(new Dimension(0, 27));
        statusBar.add(separadorHorizontal(), BorderLayout.NORTH);

        JPanel izq = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        izq.setBackground(TemaFisio.BG_STATUS);

        lblArchivo = crearEtiquetaStatus("Sin archivo", TemaFisio.FG_DIM);
        izq.add(lblArchivo);
        izq.add(separadorVerticalStatus());
        lblTokens = crearEtiquetaStatus("Tokens: —", TemaFisio.COL_NUM);
        izq.add(lblTokens);
        izq.add(separadorVerticalStatus());
        lblErrores = crearEtiquetaStatus("Errores: —", TemaFisio.COL_ERROR);
        izq.add(lblErrores);
        izq.add(separadorVerticalStatus());
        lblSintaxis = crearEtiquetaStatus("Sintaxis: —", TemaFisio.ACCENT);
        izq.add(lblSintaxis);

        JPanel der = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 4));
        der.setBackground(TemaFisio.BG_STATUS);
        // Indicador de posicion del cursor (Fase 2): puramente del editor,
        // no depende de F5/F6 ni de TokenFisio/ErrorLexico/ErrorSintactico.
        lblCursor = crearEtiquetaStatus("Línea: 1 | Columna: 1", TemaFisio.FG_DIM);
        der.add(lblCursor);
        der.add(separadorVerticalStatus());
        lblEstado = crearEtiquetaStatus("Listo", TemaFisio.ACCENT);
        der.add(lblEstado);

        JPanel centro = new JPanel(new BorderLayout());
        centro.setBackground(TemaFisio.BG_STATUS);
        centro.add(izq, BorderLayout.WEST);
        centro.add(der, BorderLayout.EAST);
        statusBar.add(centro, BorderLayout.CENTER);

        return statusBar;
    }

    private JLabel crearEtiquetaStatus(String texto, Color color) {
        JLabel lbl = new JLabel(texto);
        lbl.setForeground(color);
        lbl.setFont(TemaFisio.fuenteUi(Font.PLAIN, 9));
        return lbl;
    }

    // ── Indicador de cursor (Línea/Columna) ────────────────────
    /**
     * Actualiza el indicador de la barra de estado. Se alimenta del caret
     * REAL del editor (ver EditorCodigoPanel.addCursorListener) -- no
     * tiene relacion con los analizadores lexico/sintactico ni con sus
     * lineas/columnas de token; funciona aunque nunca se haya ejecutado
     * F5/F6.
     */
    private void onCursorMovido(int linea, int columna) {
        lblCursor.setText("Línea: " + linea + " | Columna: " + columna);
    }

    // ── Acciones: análisis sintáctico ─────────────────────────
    /**
     * Equivalente a _analizar_sintactico en gui.py. Comprobaciones
     * defensivas, en el mismo orden y con los mismos mensajes:
     * 1) hubo un analisis lexico previo (codigoAnalizadoLexico != null);
     * 2) el codigo actual sigue siendo exactamente el analizado por F5
     *    (aunque la invalidacion de 6C ya lo garantiza en la practica, se
     *    revalida aqui de forma explicita, igual que Python);
     * 3) ese analisis lexico no tuvo errores.
     * Si algo falla, NO se ejecuta FisioParserRunner.
     */
// ── Acciones: análisis léxico ─────────────────────────────
    private void analizarLexico() {
        String codigo = editor.getCodigo();
        if (codigo.strip().isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "El editor está vacío.\nEscribe o carga código FISIO para analizar.",
                "Sin código", JOptionPane.WARNING_MESSAGE);
            return;
        }

        ResultadoLexico resultado = FisioLexerRunner.tokenize(codigo);
        codigoAnalizadoLexico = codigo;
        tokensAnalizados = resultado.tokens();
        erroresLexicosAnalizados = resultado.errores();
        panelSintactico.limpiar();

        tablaTokens.poblar(resultado.tokens());
        panelResumen.poblar(resultado.tokens(), resultado.errores());
        editor.aplicarResaltado(resultado.tokens(), resultado.errores());

        int nTok = resultado.tokens().size();
        int nErr = resultado.errores().size();
        lblTokens.setText("Tokens: " + nTok);
        lblErrores.setText("Errores: " + nErr);
        lblSintaxis.setText("Sintaxis: —");
        
        // ¡NUEVO!: Siempre habilitamos el botón sintáctico, haya o no haya errores léxicos.
        btnSintactico.setEnabled(true);

        if (nErr == 0) {
            lblEstado.setText("Analisis lexico exitoso");
            tabs.setSelectedIndex(0);
        } else {
            lblEstado.setText(nErr + " error(es) lexicos");
            tabs.setSelectedIndex(1);
        }
    }

    // ── Acciones: análisis sintáctico ─────────────────────────
    private void analizarSintactico() {
        String codigoActual = editor.getCodigo();

        if (codigoAnalizadoLexico == null) {
            JOptionPane.showMessageDialog(this,
                "Primero ejecuta el analisis lexico antes del analisis sintactico.",
                "Analisis lexico requerido", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (!codigoAnalizadoLexico.equals(codigoActual)) {
            invalidarAnalisisPorCambio();
            JOptionPane.showMessageDialog(this,
                "El codigo cambio despues del analisis lexico. Ejecuta de nuevo el analisis lexico.",
                "Codigo modificado", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // ¡NUEVO!: Eliminamos el bloque 'if (!erroresLexicosAnalizados.isEmpty())' 
        // que impedía el análisis sintáctico. Ahora pasa directo al Parser.

        ResultadoSintactico resultado;
        try {
            resultado = FisioParserRunner.parse(codigoActual);
        } catch (RuntimeException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this,
                "Error inesperado durante el analisis sintactico:\n" + ex.getMessage(),
                "Error interno", JOptionPane.ERROR_MESSAGE);
            lblSintaxis.setText("Sintaxis: error interno");
            lblEstado.setText("Error interno en el analisis sintactico");
            return;
        }

        panelSintactico.poblar(resultado.ast(), resultado.errores());
        editor.aplicarResaltadoSintactico(tokensAnalizados, resultado.errores());

        int nErr = resultado.errores().size();
        lblSintaxis.setText("Sintaxis: " + nErr + " error(es)");

        if (nErr == 0) {
            lblEstado.setText("Analisis sintactico exitoso");
        } else {
            lblEstado.setText(nErr + " error(es) sintacticos");
        }

        tabs.setSelectedIndex(2);
    }

    /**
     * Equivalente a _on_codigo_modificado en gui.py: solo invalida si ya
     * habia un analisis lexico realizado (si nunca se ejecuto F5, escribir
     * no hace nada especial aqui, igual que en Python).
     */
    private void onCodigoModificado() {
        editorModificado = true;
        if (codigoAnalizadoLexico != null) {
            invalidarAnalisisPorCambio();
        }
    }

    // ── Acciones: Abrir / Guardar ─────────────────────────────
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
        editorModificado = false;
        archivoActual = ruta;
        nombreEjemploActual = null;
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
        nombreEjemploActual = null;
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
        editorModificado = false;
        archivoActual = null;
        nombreEjemploActual = nombre;
        lblArchivo.setText("Ejemplo: " + nombre);
        setTitle("FISIO — Ejemplo: " + nombre);
        limpiarResultados();
    }

    private void limpiarTodo() {
        editor.limpiar();
        editorModificado = false;
        archivoActual = null;
        nombreEjemploActual = null;
        lblArchivo.setText("Sin archivo");
        setTitle(TITULO_BASE);
        limpiarResultados();
    }

    /** Equivalente a _limpiar_resultados en gui.py -- no toca el estado de "archivo". */
    private void limpiarResultados() {
        limpiarResultadosConEstado("Listo");
    }

    /** Equivalente a _invalidar_analisis_por_cambio en gui.py. */
    private void invalidarAnalisisPorCambio() {
        limpiarResultadosConEstado("Codigo modificado; ejecuta analisis lexico");
    }

    private void limpiarResultadosConEstado(String textoEstado) {
        codigoAnalizadoLexico = null;
        tokensAnalizados = List.of();
        erroresLexicosAnalizados = List.of();
        tablaTokens.limpiar();
        panelResumen.limpiar();
        panelSintactico.limpiar();
        editor.aplicarResaltado(List.of(), List.of());
        btnSintactico.setEnabled(false);
        lblTokens.setText("Tokens: —");
        lblErrores.setText("Errores: —");
        lblSintaxis.setText("Sintaxis: —");
        lblEstado.setText(textoEstado);
    }

    // ── Separadores ───────────────────────────────────────────
    private static JPanel separadorHorizontal() {
        JPanel p = new JPanel();
        p.setBackground(TemaFisio.BORDER);
        p.setPreferredSize(new Dimension(0, 1));
        return p;
    }

    private static JPanel separadorVerticalToolbar() {
        JPanel p = new JPanel();
        p.setBackground(TemaFisio.BORDER);
        p.setPreferredSize(new Dimension(1, 28));
        return p;
    }

    private static JPanel separadorVerticalStatus() {
        JPanel p = new JPanel();
        p.setBackground(TemaFisio.BORDER);
        p.setPreferredSize(new Dimension(1, 16));
        return p;
    }
}
