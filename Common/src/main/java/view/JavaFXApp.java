package view;

import java.util.List;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import controller.AbstrController;
import controller.TerminalController;
import javafx.application.Application;
import javafx.stage.Stage;
import utils.Constants;
import utils.FileUtils;

public class JavaFXApp extends Application {
	//Este terminals no me sirve porque los títulos están internacionalizados
	//¿Es necesario?
	private List<String> terminals = List.of(
			FileUtils.getResourceBundle("etiqueta_puesto"),
			FileUtils.getResourceBundle("etiqueta_mesa"), //Seggio", 
			FileUtils.getResourceBundle("etiqueta_urna"),//Urna", 
			FileUtils.getResourceBundle("etiqueta_mesa_aux")); //"SeggioAusiliario");
	protected AbstrController controller = null;
	protected String stageTitle = "";
	private static final Logger logger = LogManager.getLogger(JavaFXApp.class);
	
	@Override
	public void start(Stage stage) throws Exception {
		stage.setOnCloseRequest(event -> {
			if(!Constants.devMode) {
				String psw = Dialogs.printTextConfirmation("Conferma Chiusura", "Password Richiesta", "Inserisci la password");
	    		
				if (psw.equals("<<abort>>")) {
	    			event.consume();
	    			return;
				}
				
	    		if (!psw.equals(Constants.exitCode)) {
	    			Dialogs.printError("Password Errata", "La password inserita non è corretta", "Per favore, riprova.");
					event.consume();
					return;
				}
			}
			logger.debug("Cerrando {} ", stageTitle);
			if (terminals.contains(stageTitle))
				((TerminalController) controller).shutDown();
		});

		stage.setTitle(stageTitle);

		stage.setMinWidth(720);
		stage.setWidth(840);

		stage.setMinHeight(510);
		stage.setHeight(590);

		stage.setResizable(true);
	}
}
