package fisio.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JEditorPane;
import javax.swing.JScrollPane;

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

    /* ======================================================================
       SECCION B: ALFABETO RUST-LIKE / FASE 2 (revision general).
       Traduccion 1:1 de los tokens REALES definidos en
       src/main/javacc/fisio/FISIO.jj y de los IDs REALES asignados en
       fisio.token.MapeoTokens -- ningun dato aqui se inventa; si se agrega
       o cambia un token en FISIO.jj, esta lista debe actualizarse para
       seguir siendo exacta. No reemplaza ni borra PALABRAS_RESERVADAS /
       OPERADORES_MATEMATICOS / OPERADORES_RELACIONALES / SIGNOS de arriba
       (alfabeto FISIO original, seccion A): cada versión se presenta en
       su propio módulo de referencia.
       ====================================================================== */
    private static final List<Entrada> PALABRAS_RESERVADAS_RUST = List.of(
        new Entrada("fn", CategoriaToken.PR, "PR_01"),
        new Entrada("let", CategoriaToken.PR, "PR_02"),
        new Entrada("mut", CategoriaToken.PR, "PR_03"),
        new Entrada("if", CategoriaToken.PR, "PR_04"),
        new Entrada("else", CategoriaToken.PR, "PR_05"),
        new Entrada("while", CategoriaToken.PR, "PR_06"),
        new Entrada("for", CategoriaToken.PR, "PR_07"),
        new Entrada("in", CategoriaToken.PR, "PR_08"),
        new Entrada("loop", CategoriaToken.PR, "PR_09"),
        new Entrada("match", CategoriaToken.PR, "PR_10"),
        new Entrada("return", CategoriaToken.PR, "PR_11"),
        new Entrada("struct", CategoriaToken.PR, "PR_12"),
        new Entrada("enum", CategoriaToken.PR, "PR_13"),
        new Entrada("impl", CategoriaToken.PR, "PR_14"),
        new Entrada("trait", CategoriaToken.PR, "PR_15"),
        new Entrada("pub", CategoriaToken.PR, "PR_16"),
        new Entrada("use", CategoriaToken.PR, "PR_17"),
        new Entrada("true", CategoriaToken.PR, "PR_18"),
        new Entrada("false", CategoriaToken.PR, "PR_19"),
        new Entrada("type", CategoriaToken.PR, "PR_20"),
        new Entrada("const", CategoriaToken.PR, "PR_21"),
        new Entrada("static", CategoriaToken.PR, "PR_22"),
        new Entrada("as", CategoriaToken.PR, "PR_23"),
        new Entrada("mod", CategoriaToken.PR, "PR_24"),
        new Entrada("break", CategoriaToken.PR, "PR_25"),
        new Entrada("continue", CategoriaToken.PR, "PR_26"),
        new Entrada("where", CategoriaToken.PR, "PR_27"),
        new Entrada("ref", CategoriaToken.PR, "PR_28"),
        new Entrada("unsafe", CategoriaToken.PR, "PR_29")
    );

    private static final List<Entrada> OPERADORES_MATEMATICOS_RUST = List.of(
        new Entrada("+", CategoriaToken.OPM, "OPM_01"),
        new Entrada("-", CategoriaToken.OPM, "OPM_02"),
        new Entrada("*", CategoriaToken.OPM, "OPM_03"),
        new Entrada("/", CategoriaToken.OPM, "OPM_04"),
        new Entrada("%", CategoriaToken.OPM, "OPM_05")
    );

    private static final List<Entrada> OPERADORES_RELACIONALES_RUST = List.of(
        new Entrada("==", CategoriaToken.OPR, "OPR_01"),
        new Entrada("!=", CategoriaToken.OPR, "OPR_02"),
        new Entrada("<", CategoriaToken.OPR, "OPR_03"),
        new Entrada(">", CategoriaToken.OPR, "OPR_04"),
        new Entrada("<=", CategoriaToken.OPR, "OPR_05"),
        new Entrada(">=", CategoriaToken.OPR, "OPR_06")
    );

    // "&&"/"||"/"!" son categoria SIG en MapeoTokens (igual que "=" o los
    // signos de agrupacion) -- no se inventa una categoria "OPL" nueva solo
    // por agruparlos visualmente aqui.
    private static final List<Entrada> OPERADORES_LOGICOS_RUST = List.of(
        new Entrada("&&", CategoriaToken.SIG, "SIG_19"),
        new Entrada("||", CategoriaToken.SIG, "SIG_20"),
        new Entrada("!", CategoriaToken.SIG, "SIG_12")
    );

    private static final List<Entrada> ASIGNACION_RUST = List.of(
        new Entrada("=", CategoriaToken.SIG, "SIG_11"),
        new Entrada("+=", CategoriaToken.SIG, "SIG_21"),
        new Entrada("-=", CategoriaToken.SIG, "SIG_22"),
        new Entrada("*=", CategoriaToken.SIG, "SIG_23"),
        new Entrada("/=", CategoriaToken.SIG, "SIG_24"),
        new Entrada("%=", CategoriaToken.SIG, "SIG_25"),
        new Entrada("<<=", CategoriaToken.SIG, "SIG_27"),
        new Entrada(">>=", CategoriaToken.SIG, "SIG_28"),
        new Entrada("&=", CategoriaToken.SIG, "SIG_29"),
        new Entrada("|=", CategoriaToken.SIG, "SIG_30"),
        new Entrada("^=", CategoriaToken.SIG, "SIG_31")
    );

    private static final List<Entrada> RANGO_RUST = List.of(
        new Entrada("..", CategoriaToken.SIG, "SIG_26"),
        new Entrada("..=", CategoriaToken.SIG, "SIG_32")
    );

    // Sin ASIGNACION/AND/OR/NEGACION/PUNTO (ya cubiertos en sus propias
    // subsecciones arriba) para no duplicar filas.
    private static final List<Entrada> SIGNOS_RUST = List.of(
        new Entrada("(", CategoriaToken.SIG, "SIG_01"),
        new Entrada(")", CategoriaToken.SIG, "SIG_02"),
        new Entrada("[", CategoriaToken.SIG, "SIG_03"),
        new Entrada("]", CategoriaToken.SIG, "SIG_04"),
        new Entrada("{", CategoriaToken.SIG, "SIG_05"),
        new Entrada("}", CategoriaToken.SIG, "SIG_06"),
        new Entrada(",", CategoriaToken.SIG, "SIG_07"),
        new Entrada(".", CategoriaToken.SIG, "SIG_08"),
        new Entrada(";", CategoriaToken.SIG, "SIG_09"),
        new Entrada(":", CategoriaToken.SIG, "SIG_10"),
        new Entrada("&", CategoriaToken.SIG, "SIG_13"),
        new Entrada("|", CategoriaToken.SIG, "SIG_14"),
        new Entrada("->", CategoriaToken.SIG, "SIG_16"),
        new Entrada("=>", CategoriaToken.SIG, "SIG_17"),
        new Entrada("::", CategoriaToken.SIG, "SIG_18"),
        new Entrada("<<", CategoriaToken.SIG, "SIG_33"),
        new Entrada(">>", CategoriaToken.SIG, "SIG_34"),
        new Entrada("^", CategoriaToken.SIG, "SIG_35"),
        new Entrada("@", CategoriaToken.SIG, "SIG_36"),
        new Entrada("#", CategoriaToken.SIG, "SIG_37"),
        new Entrada("?", CategoriaToken.SIG, "SIG_38")
    );

    public enum Version { FISIO, RUST }
    private final StringBuilder contenido = new StringBuilder();
    private final JEditorPane documento = new JEditorPane();

    public PanelAlfabeto(Version version) {
        super(new BorderLayout());
        setBackground(TemaFisio.BG_PANEL);
        contenido.append("<html><head><style>body { font-family: sans-serif; font-size: 12pt; color: #cdd6f4; }")
            .append("h2 { font-size: 14pt; color: #89b4fa; } td, th { padding: 5px; text-align: left; }")
            .append("th { color: #89b4fa; } table { width: 100%; } </style></head><body>");
        if (version == Version.FISIO) poblarSeccionFisioOriginal();
        else poblarSeccionRustLike();
        agregarSeparador();
        agregarTitulo("Categorías de tokens", 12);
        agregarTablaTipos(List.of(
            new FilaTipo("PR", "Palabra reservada", TemaFisio.COL_PR),
            new FilaTipo("OPM", "Operador matemático", TemaFisio.COL_OPM),
            new FilaTipo("OPR", "Operador relacional", TemaFisio.COL_OPR),
            new FilaTipo("SIG", "Signo", TemaFisio.COL_SIG),
            new FilaTipo("ID", "Identificador", TemaFisio.COL_ID),
            new FilaTipo("NUM", "Número", TemaFisio.COL_NUM),
            new FilaTipo("ERROR", "Token inválido", TemaFisio.COL_ERROR)
        ));
        contenido.append("</body></html>");
        documento.setContentType("text/html");
        documento.setEditable(false);
        documento.setBackground(TemaFisio.BG_PANEL);
        documento.setBorder(BorderFactory.createEmptyBorder(0, 8, 8, 8));
        documento.setText(contenido.toString());
        documento.setCaretPosition(0);
        JScrollPane scroll = new JScrollPane(documento);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(20);
        add(scroll, BorderLayout.CENTER);
    }

    /** SECCION A (sin cambios de contenido, solo el titulo aclara que es la version anterior). */
    private void poblarSeccionFisioOriginal() {
        agregarTitulo("FISIO original · Lenguaje de física", 13);
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
    }

    /** SECCION B (nueva): alfabeto de la gramática Rust-like REAL (FISIO.jj actual). */
    private void poblarSeccionRustLike() {
        agregarTitulo("Rust-like · Gramática actual", 13);
        agregarSeparador();

        agregarTexto(
            "Subconjunto sintáctico académico inspirado en Rust: fn/let/mut, if/else, "
          + "while, for..in, loop, return, bloques { }, expresiones con precedencia "
          + "(||, &&, ==/!=, relacionales, +/-, */%, unario -/!), llamadas a función, "
          + "macros mínimas tipo println!(...) y rangos (a..b). Sin ownership, "
          + "borrowing, lifetimes, genéricos, traits reales, match, módulos ni "
          + "ejecución real. Esta referencia incluye tokens reconocidos por el lexer; no todos tienen soporte sintáctico o semántico.",
            TemaFisio.fuenteUi(Font.PLAIN, 10));
        agregarSeparador();

        agregarTitulo("Palabras Reservadas", 12);
        agregarTablaItems(PALABRAS_RESERVADAS_RUST, TemaFisio.COL_PR);
        agregarSeparador();

        agregarTitulo("Operadores Matemáticos", 12);
        agregarTablaItems(OPERADORES_MATEMATICOS_RUST, TemaFisio.COL_OPM);
        agregarSeparador();

        agregarTitulo("Operadores Relacionales", 12);
        agregarTablaItems(OPERADORES_RELACIONALES_RUST, TemaFisio.COL_OPR);
        agregarSeparador();

        agregarTitulo("Operadores Lógicos", 12);
        agregarTablaItems(OPERADORES_LOGICOS_RUST, TemaFisio.COL_SIG);
        agregarSeparador();

        agregarTitulo("Asignación", 12);
        agregarTablaItems(ASIGNACION_RUST, TemaFisio.COL_SIG);
        agregarSeparador();

        agregarTitulo("Rango", 12);
        agregarTablaItems(RANGO_RUST, TemaFisio.COL_SIG);
        agregarSeparador();

        agregarTitulo("Signos", 12);
        agregarTablaItems(SIGNOS_RUST, TemaFisio.COL_SIG);
        agregarSeparador();

        agregarTitulo("Comentarios", 12);
        agregarTexto(
            "// comentario de línea\n"
          + "/* comentario de bloque, admite anidamiento */\n"
          + "(se ignoran igual que los espacios: no generan token ni aparecen en la tabla)",
            TemaFisio.fuenteMono(Font.PLAIN, 10));
        agregarSeparador();

        agregarTitulo("Tokens Complejos", 12);
        agregarTablaTipos(List.of(
            new FilaTipo("ID", "Identificador ASCII: letra o _; después admite dígitos", TemaFisio.COL_ID),
            new FilaTipo("NUM", "Número decimal sin sufijos ni exponentes", TemaFisio.COL_NUM),
            new FilaTipo("STRING", "Cadena Unicode entre comillas; admite escapes básicos", TemaFisio.COL_STRING),
            new FilaTipo("ERROR", "Carácter no reconocido", Color.decode("#ff5555"))
        ));
    }

    private void agregarTitulo(String texto, int tamano) {
        contenido.append("<h2>").append(escapar(texto)).append("</h2>");
    }
    private void agregarSeparador() { contenido.append("<hr>"); }
    private void agregarTexto(String texto, Font fuente) {
        contenido.append("<p>").append(escapar(texto).replace("\n", "<br>")).append("</p>");
    }
    private void agregarTablaItems(List<Entrada> entradas, Color color) {
        contenido.append("<table cellspacing='0'><tr><th>Lexema</th><th>Tipo</th><th>ID Token</th></tr>");
        for (Entrada entrada : entradas) {
            contenido.append("<tr><td>").append(escapar(entrada.lexema())).append("</td><td>")
                .append(entrada.tipo().name()).append("</td><td>").append(entrada.idFisio()).append("</td></tr>");
        }
        contenido.append("</table>");
    }
    private void agregarTablaTipos(List<FilaTipo> filas) {
        contenido.append("<table cellspacing='0'><tr><th>Tipo</th><th>Descripción</th></tr>");
        for (FilaTipo fila : filas) contenido.append("<tr><td>").append(escapar(fila.sigla()))
            .append("</td><td>").append(escapar(fila.descripcion())).append("</td></tr>");
        contenido.append("</table>");
    }
    private static String escapar(String texto) {
        return texto.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
