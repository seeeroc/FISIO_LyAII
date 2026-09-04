package fisio.gui;

import java.awt.BorderLayout;
import java.awt.Font;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.text.BadLocationException;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

import fisio.analysis.ErrorSintactico;
import fisio.ast.NodoPrograma;

/**
 * Panel de resultado sintactico (AST/errores), equivalente a
 * PanelSintactico (gui.py).
 *
 * Desde el Paso 6D, poblar(ast, errores) reproduce exactamente
 * PanelSintactico.poblar()/_formatear_ast() de gui.py: encabezado,
 * estado exitoso/con errores, lista de errores sintacticos, y el AST (o
 * AST parcial) formateado como texto con sangria -- via FormateadorAst,
 * que hace el trabajo de traducir los 12 tipos de nodo.
 */
public final class PanelSintactico extends JPanel {

    private final JTextPane texto;
    private final Style estiloTitulo;
    private final Style estiloOk;
    private final Style estiloError;
    private final Style estiloDim;

    public PanelSintactico() {
        super(new BorderLayout());
        setBackground(TemaFisio.BG_PANEL);

        texto = new JTextPane();
        texto.setEditable(false);
        texto.setBackground(TemaFisio.BG_PANEL);
        texto.setForeground(TemaFisio.FG_MAIN);
        texto.setCaretColor(TemaFisio.ACCENT);
        texto.setSelectionColor(TemaFisio.BORDER);
        texto.setFont(TemaFisio.fuenteMono(Font.PLAIN, 10));
        texto.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));

        JScrollPane scroll = new JScrollPane(texto);
        scroll.getViewport().setBackground(TemaFisio.BG_PANEL);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        add(scroll, BorderLayout.CENTER);

        StyledDocument doc = texto.getStyledDocument();

        // Mismos 4 tags que gui.py: "titulo", "ok", "error", "dim".
        // "error"/"dim" no fijan fuente -> heredan la fuente base del
        // JTextPane (Consolas 10 plain), igual que en Tkinter.
        estiloTitulo = doc.addStyle("titulo", null);
        StyleConstants.setForeground(estiloTitulo, TemaFisio.ACCENT);
        StyleConstants.setFontFamily(estiloTitulo, TemaFisio.fuenteMono(Font.PLAIN, 11).getFamily());
        StyleConstants.setFontSize(estiloTitulo, 11);
        StyleConstants.setBold(estiloTitulo, true);

        estiloOk = doc.addStyle("ok", null);
        StyleConstants.setForeground(estiloOk, TemaFisio.OK_GREEN);
        StyleConstants.setFontFamily(estiloOk, TemaFisio.fuenteMono(Font.PLAIN, 10).getFamily());
        StyleConstants.setFontSize(estiloOk, 10);
        StyleConstants.setBold(estiloOk, true);

        estiloError = doc.addStyle("error", null);
        StyleConstants.setForeground(estiloError, TemaFisio.COL_ERROR);

        estiloDim = doc.addStyle("dim", null);
        StyleConstants.setForeground(estiloDim, TemaFisio.FG_DIM);
    }

    /**
     * Equivalente a PanelSintactico.poblar(ast, errores) en gui.py.
     * "ast" nunca es null en FisioParserRunner (siempre hay al menos un
     * NodoPrograma, completo o parcial) pero FormateadorAst tambien
     * reproduce la rama "&lt;sin AST&gt;" de gui.py por fidelidad, para
     * cualquier hijo que pudiera venir null.
     */
    public void poblar(NodoPrograma ast, List<ErrorSintactico> errores) {
        StyledDocument doc = texto.getStyledDocument();
        try {
            doc.remove(0, doc.getLength());

            doc.insertString(doc.getLength(), "ANALISIS SINTACTICO\n", estiloTitulo);
            doc.insertString(doc.getLength(), "\n", null);

            if (!errores.isEmpty()) {
                doc.insertString(doc.getLength(), "CON ERRORES (" + errores.size() + ")\n\n", estiloError);
                doc.insertString(doc.getLength(), "Errores detectados:\n", estiloTitulo);
                for (ErrorSintactico err : errores) {
                    String lexema = lexemaError(err);
                    String linea = String.format(
                        "  Linea %3d, Col %3d | '%s' | %s\n", err.linea(), err.columna(), lexema, err.mensaje());
                    doc.insertString(doc.getLength(), linea, estiloError);
                }
                doc.insertString(doc.getLength(), "\nAST parcial:\n", estiloTitulo);
            } else {
                doc.insertString(doc.getLength(), "ANALISIS EXITOSO\n\n", estiloOk);
                doc.insertString(doc.getLength(), "AST:\n", estiloTitulo);
            }

            for (String linea : FormateadorAst.formatear(ast)) {
                doc.insertString(doc.getLength(), linea + "\n", estiloDim);
            }
        } catch (BadLocationException e) {
            // No deberia ocurrir nunca: siempre se inserta en doc.getLength().
            throw new AssertionError(e);
        }
    }

    /** Igual que _lexema_error en gui.py: "" (EOF) se muestra como "EOF". */
    private static String lexemaError(ErrorSintactico err) {
        String lexema = err.token().lexema();
        return lexema.isEmpty() ? "EOF" : lexema;
    }

    /** Deja el area sin texto. */
    public void limpiar() {
        texto.setText("");
    }
}
