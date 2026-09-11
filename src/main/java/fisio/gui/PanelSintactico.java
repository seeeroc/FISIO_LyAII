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
        texto.setFont(TemaFisio.fuenteMono(Font.PLAIN, 12)); // Letra un poco más grande
        texto.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));

        JScrollPane scroll = new JScrollPane(texto);
        scroll.getViewport().setBackground(TemaFisio.BG_PANEL);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        add(scroll, BorderLayout.CENTER);

        StyledDocument doc = texto.getStyledDocument();

        estiloTitulo = doc.addStyle("titulo", null);
        StyleConstants.setForeground(estiloTitulo, TemaFisio.ACCENT);
        StyleConstants.setFontFamily(estiloTitulo, TemaFisio.fuenteMono(Font.BOLD, 12).getFamily());
        StyleConstants.setBold(estiloTitulo, true);

        estiloOk = doc.addStyle("ok", null);
        StyleConstants.setForeground(estiloOk, TemaFisio.OK_GREEN);
        StyleConstants.setBold(estiloOk, true);

        estiloError = doc.addStyle("error", null);
        StyleConstants.setForeground(estiloError, TemaFisio.COL_ERROR);

        estiloDim = doc.addStyle("dim", null);
        StyleConstants.setForeground(estiloDim, TemaFisio.FG_MAIN);
    }

    public void poblar(NodoPrograma ast, List<ErrorSintactico> errores) {
        StyledDocument doc = texto.getStyledDocument();
        try {
            doc.remove(0, doc.getLength());

            doc.insertString(doc.getLength(), "============ REPORTE DE ANÁLISIS SINTÁCTICO ============\n\n", estiloTitulo);

            // 1. Mostrar TODOS los errores encontrados al mismo tiempo
            if (!errores.isEmpty()) {
                doc.insertString(doc.getLength(), "❌ SE ENCONTRARON " + errores.size() + " ERROR(ES):\n", estiloError);
                for (ErrorSintactico err : errores) {
                    String lexema = lexemaError(err);
                    String linea = String.format(
                        "   [Línea %d, Columna %d] Error cerca de '%s' -> %s\n", 
                        err.linea(), err.columna(), lexema, err.mensaje());
                    doc.insertString(doc.getLength(), linea, estiloError);
                }
                doc.insertString(doc.getLength(), "\n--- Estructuras que sí lograron validarse (Recuperación) ---\n\n", estiloTitulo);
            } else {
                doc.insertString(doc.getLength(), "✅ ANÁLISIS SINTÁCTICO PERFECTO. 0 Errores.\n\n", estiloOk);
                doc.insertString(doc.getLength(), "Estructuras validadas exitosamente:\n", estiloTitulo);
            }

            // 2. Recorrer el AST para imprimir el checklist en lugar del árbol
            recorrerYValidar(ast, doc);

        } catch (BadLocationException e) {
            throw new AssertionError(e);
        }
    }

    // Método mágico con Reflexión para recorrer cualquier nodo sin importar sus getters
    private void recorrerYValidar(Object obj, StyledDocument doc) throws BadLocationException {
        if (obj == null) return;
        
        // Si es una lista, iteramos sobre sus elementos
        if (obj instanceof List) {
            for (Object item : (List<?>) obj) {
                recorrerYValidar(item, doc);
            }
            return;
        }

        // Checar a qué estructura pertenece
        String clase = obj.getClass().getSimpleName();
        String mensaje = "";

        switch (clase) {
            case "NodoFuncion": mensaje = "Estructura de Función (fn)"; break;
            case "NodoDeclaracion": mensaje = "Declaración de variable (let)"; break;
            case "NodoAsignacion": mensaje = "Operación de Asignación (=, +=, -=)"; break;
            case "NodoIf": mensaje = "Estructura condicional (if / else)"; break;
            case "NodoWhile": mensaje = "Ciclo (while)"; break;
            case "NodoFor": mensaje = "Ciclo (for .. in)"; break;
            case "NodoInvocacionMacro": mensaje = "Invocación de Macro (ej. println!)"; break;
            case "NodoLlamadaFuncion": mensaje = "Llamada a una función"; break;
            case "NodoReturn": mensaje = "Sentencia de retorno (return)"; break;
        }

        if (!mensaje.isEmpty()) {
            doc.insertString(doc.getLength(), "  ✔️ Se validó correctamente: " + mensaje + "\n", estiloDim);
        }

        // Profundizar en los hijos del nodo automáticamente
        for (java.lang.reflect.Field field : obj.getClass().getDeclaredFields()) {
            field.setAccessible(true);
            try {
                Object hijo = field.get(obj);
                recorrerYValidar(hijo, doc);
            } catch (Exception ignored) {}
        }
    }

    private static String lexemaError(ErrorSintactico err) {
        String lexema = err.token().lexema();
        return lexema.isEmpty() ? "EOF" : lexema;
    }

    public void limpiar() {
        texto.setText("");
    }
}