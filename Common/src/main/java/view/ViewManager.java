package view;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

import controller.AbstrController;
import exceptions.PEException;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import utils.FileUtils;

public abstract class ViewManager implements ViewInterface {
	protected Stage stage;
	protected AbstrController mainController;

	public ViewManager(Stage stage) {
		this.stage = stage;
	}

	public void setControllerAndShowStage(AbstrController controller) {
		this.mainController = controller;
		stage.show();
	}

	@Override
	public void update() {
		Platform.runLater(new Runnable() {
			@Override
			public void run() {
				updateFromView();
			}
		});
	}

	@Override
	public void println(String msg) {
		Dialogs.printSuccess(FileUtils.getResourceBundle("etiqueta_operacion_exitosa"), "Messaggio Generico", msg);
	}

	@Override
	public void printMessage(String message) {
		System.out.println(message);
	}

	public void printSuccess(String message, String content) {
		Dialogs.printSuccess(FileUtils.getResourceBundle("etiqueta_operacion_exitosa"), message, content);
	}

	@Override
	public void printError(String message, String content) {
		Dialogs.printError(FileUtils.getResourceBundle("etiqueta_operacion_fallida"), message, content);
	}

	@Override
	public void printError(PEException pee) {
		Dialogs.printException(pee);
	}

	@Override
	public void printWarning(String message, String content) {
		Dialogs.printWarning("Attenzione", message, content);
	}

	@Override
	public boolean printConfirmation(String msg, String content) {
		return Dialogs.printConfirmation(FileUtils.getResourceBundle("etiqueta_confirmar_operacion"), msg, content);
	}

	protected ViewAbstrController loadScene(URL xmlUrl, AbstrController controller) {
		ResourceBundle bundle = ResourceBundle.getBundle("etiquetas");
		FXMLLoader loader = new FXMLLoader(xmlUrl, bundle);
		

		try {
			Parent sceneRoot = loader.load();
			ViewAbstrController viewController = loader.getController();
			viewController.setMainController(controller);
			setScene(sceneRoot);

			return viewController;
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	}

	private void setScene(Parent root) {
		if (stage.getScene() == null) {
			Scene scene = new Scene(root);
			stage.setScene(scene);
		} else {
			stage.getScene().setRoot(root);
		}
	}
}
