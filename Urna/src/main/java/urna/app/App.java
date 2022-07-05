package urna.app;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import exceptions.PEException;
import javafx.application.Application;
import javafx.stage.Stage;
import urna.controller.Controller;
import urna.controller.UrnDB;
import urna.model.Urn;
import urna.view.View;
import utils.Constants;
import utils.FileUtils;
import view.Dialogs;
import view.JavaFXApp;

public class App extends JavaFXApp {
	
	private static final Logger logger = LogManager.getLogger(App.class);
	
	public static void main(String[] args) throws PEException {
		Application.launch(args);
	}
	
	@Override
	public void start(Stage stage) throws Exception {
		stageTitle = FileUtils.getResourceBundle("etiqueta_urna");
		logger.info("Starting {} ========================== ", stageTitle);
		super.start(stage);
		
		//Creo la classe Model
		int numConnections = 5;
		Urn urna = new Urn(Constants.portUrn, numConnections);
		
		//Creo la classe View
		View view = new View(stage, urna);
		
		try{
			//Creo il controller e la classe per interagire col DB
			UrnDB db = new UrnDB("localhost", "3306", "secureBallot", "Urna");

			//Passo la view, il model e il DB al controller
			Controller controller = new Controller(view, urna, db);
			this.controller = controller;

			//Passo il controller alla view
			view.setControllerAndShowStage(controller);

			//Il controller viene avviato.
			controller.start();

			view.update();
		}
		catch(PEException pee) {
			//controller.shutDown();
			Dialogs.printException(pee);
		}
	}
}
