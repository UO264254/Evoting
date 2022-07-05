package controller;

import java.io.File;
import java.util.ResourceBundle;

import db.DB;
import exceptions.PEException;
import model.AbstrModel;
import utils.Constants;
import utils.FileUtils;
import view.ViewInterface;

/**
 * Controller abstracto, adoperato semplicemente per fornire alle classi derivate
 * dei metodi comuni di comunicazione con la view (PrintWriter).
 * ------
 */
public class AbstrController {
	private ViewInterface view;

	public AbstrController(ViewInterface view) {
		this.view = view;
	}

	/**
	 * Funzione che verifica i dati inseriti nella schermata di login. Se almeno uno
	 * dei due campi non è stato compilato, allora stampa automaticamente un dialog
	 * di errore. Altrimenti, verifica i dati inseriti nel DB (tramite la funzione
	 * {@link DB#checkLoginData(String, String)}). <br>
	 * In caso di verifica positiva, memorizza i dati dell'utente loggato nel model
	 * e restituisce True. Se si verifica un qualunque errore, stampa un dialog
	 * d'errore e restituisce False.
	 * 
	 * @param model Model del terminale che intende effettuare login
	 * @param db    Interfaccia col DB del terminale che intende effettuare login
	 * @param user  Username dell'utente
	 * @param psw   Password dell'utente
	 * @return True se il login è verificato, false altrimenti
	 */
	public boolean checkLoginData(AbstrModel model, DB db, String user, String psw) {
		if (user == null || psw == null || user.length() == 0 || psw.length() == 0) {
			printError(ResourceBundle.getBundle("etiquetas").getString("etiqueta_error_login"),
					ResourceBundle.getBundle("etiquetas").getString("etiqueta_introduce_ambos"));
			return false;
		}

		model.logInfo(FileUtils.getResourceBundle("etiqueta_nuevo_usuario") + " " + user
				+ FileUtils.getResourceBundle("etiqueta_provando_login"));

		try {
			if (!db.checkLoginData(user, psw)) {
				printError(ResourceBundle.getBundle("etiquetas").getString("etiqueta_error_login"),
						ResourceBundle.getBundle("etiquetas").getString("etiqueta_info_no_correcta"));
				model.logError("L'utente [Username: " + user + "] ha inserito credenziali di login non valide.");
				return false;
			}

			model.setUsername(user);
			model.setPassword(psw);

			model.logSuccess(FileUtils.getResourceBundle("etiqueta_el_usuario") + " " + user
					+ FileUtils.getResourceBundle("etiqueta_operacion_login"));

			if (Constants.verbose)
				printSuccess(ResourceBundle.getBundle("etiquetas").getString("etiqueta_login_correcto"),
						ResourceBundle.getBundle("etiquetas").getString("etiqueta_login_acceso"));

			return true;

		} catch (PEException e) {
			printError(e);
			return false;
		}
	}

	public boolean confirmLogout(AbstrModel model) {

		if (printConfirmation(FileUtils.getResourceBundle("etiqueta_logout"),
				FileUtils.getResourceBundle("etiqueta_volver_login"))) {
			model.setUsername(null);
			model.setPassword(null);

			model.logWarning(FileUtils.getResourceBundle("etiqueta_el_usuario") + " " + model.getUsername()
					+ FileUtils.getResourceBundle("etiqueta_efectuar_logout"));
			return true;
		}

		return false;
	}

	/* Funzioni per comunicare con la view */
	/**
	 * Funzione adoperata mostrare un messaggio di controllo non pensato per il
	 * pubblico ma per il programmatore. Elimina il testo presente nel debugPane (se
	 * si usa una qualche Window) se presente.
	 * 
	 * @param message Il messaggio da mostrare.
	 */
	public void printMessage(String message) {
		if (view != null) {
			view.printMessage(message);
		}
	}

	/**
	 * Funzione adoperata per mandare un generico messaggio alla view. Stampa in
	 * coda al debugPane (se si usa una qualche Window).
	 * 
	 * @param message Il messaggio da mostrare.
	 */
	public void println(String message) {
		if (view != null) {
			view.println(message);
		}
	}

	/**
	 * Funzione adoperata per mandare un messaggio d'errore diretto all'utente. Se
	 * adoperata una qualche Window (WindowPostazione, WindowSeggio), farà apparire
	 * una finestra popup col messaggio di errore.
	 * 
	 * @param message Il messaggio d'errore da mostrare.
	 */
	public void printError(String message, String content) {
		if (view != null) {
			view.printError(message, content);
		}
	}

	public void printError(PEException e) {
		if (view != null) {
			view.printError(e);
		}
	}

	/**
	 * Funzione adoperata per mandare un messaggio diretto all'utente per indicare
	 * che una operazione ha avuto successo. Se adoperata una qualche Window
	 * (WindowPostazione, WindowSeggio), farà apparire una finestra popup col
	 * messaggio.
	 * 
	 * @param message Il messaggio da mostrare.
	 */
	public void printSuccess(String message, String content) {
		if (view != null) {
			/*
			 * message = message.replace("\n", Protocol.carriage);
			 * view.println(Protocol.success + message);
			 */
			view.printSuccess(message, content);
		}
	}

	public void printWarning(String msg, String content) {
		if (view != null)
			view.printWarning(msg, content);
	}

	public boolean printConfirmation(String msg, String content) {
		if (view != null)
			return view.printConfirmation(msg, content);

		return false;
	}

	/**
	 * Funzione adoperata per aggiornare la view, mandando un messaggio al
	 * ViewListener.
	 */
	public void updateView() {
		if (view != null) {
			// view.println(Protocol.updateView);
			view.update();
		}
	}

	/**
	 * Funzione adoperata per far terminare il thread di ascolto della view.
	 */
	void closeView() {
		if (view != null) {
			// view.close();
		}
	}

	public void shutDown() {
		// qualcosa da implementare qui?
	}
}