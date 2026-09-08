package fisio.analysis;

import fisio.FisioParser; // Clase generada por JavaCC
import fisio.Token;       // Token nativo generado por JavaCC
import fisio.token.MapeoTokens;
import fisio.token.TokenFisio; // Tu clase orientada a la GUI
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import fisio.token.ErrorLexico;
import fisio.token.CategoriaToken;



public class FisioLexerRunner {
    
    // Cambiamos el nombre a "tokenize" y el retorno a "ResultadoLexico"
    public static ResultadoLexico tokenize(String codigoFuente) {
        List<TokenFisio> listaTokens = new ArrayList<>();
        List<ErrorLexico> listaErrores = new ArrayList<>();
        
        FisioParser parser = new FisioParser(new StringReader(codigoFuente));
        
        try {
            Token tokenJavaCC = parser.getNextToken();
            
            while (tokenJavaCC.kind != fisio.FisioParserConstants.EOF) {
                
                MapeoTokens.InfoToken info = MapeoTokens.obtenerInfo(tokenJavaCC.kind);
                
                // Separamos la lógica: si es un error, va a la lista de errores
                if (info.categoria == CategoriaToken.ERROR) {
                    listaErrores.add(new ErrorLexico(
                        tokenJavaCC.image, 
                        "Caracter no reconocido", 
                        tokenJavaCC.beginLine, 
                        tokenJavaCC.beginColumn
                    ));
                } else {
                    // Si es válido, se empaqueta para la tabla de la GUI
                    TokenFisio tokenParaGUI = new TokenFisio(
                        tokenJavaCC.image,      
                        info.categoria,         
                        info.idTok,             
                        tokenJavaCC.beginLine,  
                        tokenJavaCC.beginColumn 
                    );
                    listaTokens.add(tokenParaGUI);
                }
                
                tokenJavaCC = parser.getNextToken();
            }
        } catch (fisio.TokenMgrError | Exception e) {
            e.printStackTrace();
        }
        
        // Empaquetamos ambas listas en el objeto que espera la GUI
        return new ResultadoLexico(listaTokens, listaErrores);
    }
}