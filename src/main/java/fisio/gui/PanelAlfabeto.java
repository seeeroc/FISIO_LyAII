package fisio.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import fisio.token.CategoriaToken;

/**
 * Especificacion lexica de FISIO, equivalente a PanelAlfabeto (gui.py).
 *
 * Contenido estatico que NO depende de ningun analisis (igual que en
 * gui.py, donde _poblar_informacion() se ejecuta una sola vez en el
 * constructor). Los datos (lexema, tipo, id) son una traduccion 1:1 de
 * PALABRAS_RESERVADAS / OPERADORES_MATEMATICOS / OPERADORES_RELACIONALES /
 * SIGNOS en token_types.py — se reutiliza el enum CategoriaToken (ya
 * congelado) para las etiquetas de tipo en vez de duplicar strings, pero
 * ninguna clase de fisio.token se modifica.
 */
public final class PanelAlfabeto extends JPanel {

    private record Entrada(String lexema, CategoriaToken tipo, String idFisio) {
    }

    private record FilaTipo(String sigla, String descripcion, Color color) {
    }

    // Mismo orden que PALABRAS_RESERVADAS en token_types.py.
    private static final List<Entrada> PALABRAS_RESERVADAS = List.of(
        new Entrada("mru", CategoriaToken.PR, "PR_01"),
        new Entrada("mrua", CategoriaToken.PR, "PR_02"),
        new Entrada("caida", CategoriaToken.PR, "PR_03"),
        new Entrada("parabolico", CategoriaToken.PR, "PR_04"),
        new Entrada("despejar", CategoriaToken.PR, "PR_05"),
        new Entrada("graficar", CategoriaToken.PR, "PR_06"),
        new Entrada("simular", CategoriaToken.PR, "PR_07"),
        new Entrada("raiz", CategoriaToken.PR, "PR_08"),
        new Entrada("seno", CategoriaToken.PR, "PR_09"),
        new Entrada("coseno", CategoriaToken.PR, "PR_10"),
        new Entrada("magnitud", CategoriaToken.PR, "PR_11"),
        new Entrada("gravedad", CategoriaToken.PR, "PR_12"),
        new Entrada("posicion", CategoriaToken.PR, "PR_13"),
        new Entrada("velocidad", CategoriaToken.PR, "PR_14"),
        new Entrada("aceleracion", CategoriaToken.PR, "PR_15"),
        new Entrada("tiempo", CategoriaToken.PR, "PR_16"),
        new Entrada("altura", CategoriaToken.PR, "PR_17"),
        new Entrada("alcance", CategoriaToken.PR, "PR_18"),
        new Entrada("Inicio", CategoriaToken.PR, "PR_19"),
        new Entrada("Fin", CategoriaToken.PR, "PR_20")
    );

    private static final List<Entrada> OPERADORES_MATEMATICOS = List.of(
        new Entrada("+", CategoriaToken.OPM, "OPM_01"),
        new Entrada("-", CategoriaToken.OPM, "OPM_02"),
        new Entrada("*", CategoriaToken.OPM, "OPM_03"),
        new Entrada("/", CategoriaToken.OPM, "OPM_04"),
        new Entrada("^", CategoriaToken.OPM, "OPM_05")
    );

    private static final List<Entrada> OPERADORES_RELACIONALES = List.of(
        new Entrada(":=", CategoriaToken.OPR, "OPR_01"),
        new Entrada("=", CategoriaToken.OPR, "OPR_02"),
        new Entrada("<", CategoriaToken.OPR, "OPR_03"),
        new Entrada(">", CategoriaToken.OPR, "OPR_04"),
        new Entrada("<=", CategoriaToken.OPR, "OPR_05"),
        new Entrada(">=", CategoriaToken.OPR, "OPR_06"),
        new Entrada("!=", CategoriaToken.OPR, "OPR_07")
    );

    private static final List<Entrada> SIGNOS = List.of(
        new Entrada("(", CategoriaToken.SIG, "SIG_01"),
        new Entrada(")", CategoriaToken.SIG, "SIG_02"),
        new Entrada("[", CategoriaToken.SIG, "SIG_03"),
        new Entrada("]", CategoriaToken.SIG, "SIG_04"),
        new Entrada(",", CategoriaToken.SIG, "SIG_05"),
        new Entrada(".", CategoriaToken.SIG, "SIG_06"),
        new Entrada(";", CategoriaToken.SIG, "SIG_07")
    );

    private final JPanel contenido;

    public PanelAlfabeto() {
        super(new BorderLayout());
        setBackground(TemaFisio.BG_PANEL);

        contenido = new JPanel();
        contenido.setBackground(TemaFisio.BG_PANEL);
        contenido.setLayout(new BoxLayout(contenido, BoxLayout.Y_AXIS));
        contenido.setBorder(BorderFactory.createEmptyBorder(8, 0, 12, 0));

        poblarInformacion();

        JScrollPane scroll = new JScrollPane(contenido);
        scroll.getViewport().setBackground(TemaFisio.BG_PANEL);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);
    }

    private void poblarInformacion() {
        agregarTitulo("ESPECIFICACIÓN LÉXICA FISIO", 13);
        agregarSeparador();

        agregarTexto(
            "FISIO es un lenguaje orientado a simulaciones y cálculos de física clásica.\n"
          + "El analizador léxico identifica palabras reservadas, operadores, signos, "
          + "identificadores y números.",
            TemaFisio.fuenteUi(Font.PLAIN, 10));
        agregarSeparador();

        agregarTitulo("Alfabeto Permitido", 12);
        agregarTexto(
            "• Letras: a-z A-Z\n"
          + "• Dígitos: 0-9\n"
          + "• Operadores matemáticos: + - * / ^\n"
          + "• Operadores relacionales: := = < > <= >= !=\n"
          + "• Signos: ( ) [ ] , . ;\n"
          + "• Espacios válidos: espacio, tabulación y salto de línea",
            TemaFisio.fuenteMono(Font.PLAIN, 10));
        agregarSeparador();

        agregarTitulo("Palabras Reservadas", 12);
        agregarTablaItems(PALABRAS_RESERVADAS, TemaFisio.COL_PR);
        agregarSeparador();

        agregarTitulo("Operadores Matemáticos", 12);
        agregarTablaItems(OPERADORES_MATEMATICOS, TemaFisio.COL_OPM);
        agregarSeparador();

        agregarTitulo("Operadores Relacionales", 12);
        agregarTablaItems(OPERADORES_RELACIONALES, TemaFisio.COL_OPR);
        agregarSeparador();

        agregarTitulo("Signos", 12);
        agregarTablaItems(SIGNOS, TemaFisio.COL_SIG);
        agregarSeparador();

        agregarTitulo("Tipos de Token", 12);
        agregarTablaTipos(List.of(
            new FilaTipo("PR", "Palabra Reservada", TemaFisio.COL_PR),
            new FilaTipo("OPM", "Operador Matemático", TemaFisio.COL_OPM),
            new FilaTipo("OPR", "Operador Relacional", TemaFisio.COL_OPR),
            new FilaTipo("SIG", "Signo", TemaFisio.COL_SIG),
            new FilaTipo("ID", "Identificador", TemaFisio.COL_ID),
            new FilaTipo("NUM", "Número", TemaFisio.COL_NUM),
            // Color hardcodeado tal cual en gui.py (difiere ligeramente de
            // COL_ERROR "#f38ba8"; se reproduce fielmente la inconsistencia
            // original en vez de "corregirla" por iniciativa propia).
            new FilaTipo("ERROR", "Token inválido", Color.decode("#ff5555"))
        ));
    }

    private void agregarTitulo(String texto, int tamano) {
        JLabel lbl = new JLabel(texto);
        lbl.setForeground(TemaFisio.ACCENT);
        lbl.setFont(TemaFisio.fuenteUi(Font.BOLD, tamano));
        lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        lbl.setBorder(BorderFactory.createEmptyBorder(16, 20, 4, 0));
        contenido.add(lbl);
    }

    private void agregarSeparador() {
        JPanel linea = new JPanel();
        linea.setBackground(TemaFisio.BORDER);
        linea.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel envoltura = new JPanel(new BorderLayout());
        envoltura.setBackground(TemaFisio.BG_PANEL);
        envoltura.setAlignmentX(Component.LEFT_ALIGNMENT);
        envoltura.setBorder(BorderFactory.createEmptyBorder(6, 20, 6, 20));
        envoltura.add(linea, BorderLayout.CENTER);
        envoltura.setMaximumSize(new Dimension(Short.MAX_VALUE, 13));
        envoltura.setPreferredSize(new Dimension(10, 13));
        contenido.add(envoltura);
    }

    private void agregarTexto(String texto, Font fuente) {
        JTextArea area = new JTextArea(texto);
        area.setEditable(false);
        area.setFocusable(false);
        area.setOpaque(false);
        area.setForeground(TemaFisio.FG_MAIN);
        area.setFont(fuente);
        area.setAlignmentX(Component.LEFT_ALIGNMENT);
        area.setBorder(BorderFactory.createEmptyBorder(4, 20, 8, 0));
        contenido.add(area);
    }

    /**
     * Tabla de 3 columnas (Lexema / Tipo / ID Token) para una seccion
     * completa (Palabras Reservadas, Operadores, Signos). Se usa un unico
     * GridBagLayout por seccion (no un JPanel por fila) para que las 3
     * columnas queden realmente alineadas entre filas — evita el problema
     * observado en ejecucion real (texto concatenado sin separacion) que
     * ocurria al fijar anchos con setPreferredSize dentro de BoxLayout.
     */
    private void agregarTablaItems(List<Entrada> entradas, Color color) {
        JPanel tabla = new JPanel(new GridBagLayout());
        tabla.setBackground(TemaFisio.BG_PANEL);
        tabla.setAlignmentX(Component.LEFT_ALIGNMENT);
        tabla.setBorder(BorderFactory.createEmptyBorder(4, 30, 2, 0));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridy = 0;
        gbc.gridx = 0;
        gbc.insets = new Insets(0, 0, 6, 24);
        tabla.add(crearEncabezadoColumna("Lexema"), gbc);
        gbc.gridx = 1;
        tabla.add(crearEncabezadoColumna("Tipo"), gbc);
        gbc.gridx = 2;
        gbc.insets = new Insets(0, 0, 6, 0);
        tabla.add(crearEncabezadoColumna("ID Token"), gbc);

        int fila = 1;
        for (Entrada e : entradas) {
            gbc.gridy = fila;

            gbc.gridx = 0;
            gbc.insets = new Insets(2, 0, 2, 24);
            JLabel lblLexema = new JLabel(e.lexema());
            lblLexema.setForeground(color);
            lblLexema.setFont(TemaFisio.fuenteMono(Font.BOLD, 10));
            tabla.add(lblLexema, gbc);

            gbc.gridx = 1;
            JLabel lblTipo = new JLabel(e.tipo().name());
            lblTipo.setForeground(TemaFisio.FG_MAIN);
            lblTipo.setFont(TemaFisio.fuenteMono(Font.PLAIN, 9));
            tabla.add(lblTipo, gbc);

            gbc.gridx = 2;
            gbc.insets = new Insets(2, 0, 2, 0);
            JLabel lblId = new JLabel(e.idFisio());
            lblId.setForeground(TemaFisio.FG_DIM);
            lblId.setFont(TemaFisio.fuenteMono(Font.PLAIN, 9));
            tabla.add(lblId, gbc);

            fila++;
        }
        contenido.add(tabla);
    }

    /** Tabla de 2 columnas (Tipo / Descripcion) para la seccion "Tipos de Token". */
    private void agregarTablaTipos(List<FilaTipo> filas) {
        JPanel tabla = new JPanel(new GridBagLayout());
        tabla.setBackground(TemaFisio.BG_PANEL);
        tabla.setAlignmentX(Component.LEFT_ALIGNMENT);
        tabla.setBorder(BorderFactory.createEmptyBorder(4, 30, 2, 0));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridy = 0;
        gbc.gridx = 0;
        gbc.insets = new Insets(0, 0, 6, 24);
        tabla.add(crearEncabezadoColumna("Tipo"), gbc);
        gbc.gridx = 1;
        gbc.insets = new Insets(0, 0, 6, 0);
        tabla.add(crearEncabezadoColumna("Descripción"), gbc);

        int fila = 1;
        for (FilaTipo f : filas) {
            gbc.gridy = fila;

            gbc.gridx = 0;
            gbc.insets = new Insets(2, 0, 2, 24);
            JLabel lblSigla = new JLabel(f.sigla());
            lblSigla.setForeground(f.color());
            lblSigla.setFont(TemaFisio.fuenteMono(Font.BOLD, 10));
            tabla.add(lblSigla, gbc);

            gbc.gridx = 1;
            gbc.insets = new Insets(2, 0, 2, 0);
            JLabel lblDesc = new JLabel(f.descripcion());
            lblDesc.setForeground(TemaFisio.FG_MAIN);
            lblDesc.setFont(TemaFisio.fuenteUi(Font.PLAIN, 9));
            tabla.add(lblDesc, gbc);

            fila++;
        }
        contenido.add(tabla);
    }

    private JLabel crearEncabezadoColumna(String texto) {
        JLabel lbl = new JLabel(texto);
        lbl.setForeground(TemaFisio.FG_DIM);
        lbl.setFont(TemaFisio.fuenteUi(Font.BOLD, 9));
        return lbl;
    }
}
