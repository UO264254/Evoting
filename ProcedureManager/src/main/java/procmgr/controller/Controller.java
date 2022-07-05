package procmgr.controller;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.security.KeyPair;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import java.util.ResourceBundle;

import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import controller.AbstrController;
import encryption.AES;
import encryption.Hash;
import encryption.KeyPairManager;
import encryption.RandStrGenerator;
import exceptions.DEVException;
import exceptions.PEException;
import model.EmptyBallot;
import model.Person;
import model.State;
import model.State.StatePM;
import procmgr.controller.csvparsers.BallotsParser;
import procmgr.controller.csvparsers.CandidatesParser;
import procmgr.controller.csvparsers.SessionsParser;
import procmgr.controller.csvparsers.VotersParser;
import procmgr.model.ProcedureManager;
import procmgr.model.ProcedurePM;
import procmgr.model.TipoStaff;
import procmgr.view.View;
import utils.CfgManager;
import utils.Constants;
import utils.FileUtils;

/**
 * Classe controller del modulo ProcedureManager. Contiene tutte le funzioni
 * necessarie ad inizializzare il modulo e ad interagire con view e model.
 */
public class Controller extends AbstrController {
	private final ProcedureManager pm;
	private final PMDB pmDB;
	private ProcedurePM newProcedure = null;

	private final String logDir = System.getProperty("user.dir") + "/logs/";

	/**
	 * Costruttore che inizializza tutti i dati richiesti dal controller: view,
	 * model ed interfaccia col DB. Inoltre, se è richiesta connessione SSL al DB,
	 * setta le proprietà di sistema necessarie al reperimento di TrustStore e
	 * KeyStore (<i>javax.net.ssl.keyStore</i>,
	 * <i>javax.net.ssl.keyStorePassword</i> ecc..).
	 * 
	 * @param view View di ProcedureManager
	 * @param pm   Model di ProcedureManager
	 * @param db   Oggetto per l'interazione col DB
	 * @throws PEException Se non trova i keystores o se non riesce a recuperare le
	 *                     password di questi dal file <i>psws.cfg</i>
	 */
	public Controller(View view, ProcedureManager pm, PMDB db) throws PEException {
		super(view);
		this.pm = pm;
		this.pmDB = db;

		if (Constants.dbSSL) {
			System.setProperty("javax.net.ssl.keyStore", "ssl/keystore.jks");
			System.setProperty("javax.net.ssl.keyStorePassword", CfgManager.getPassword("ks"));

			System.setProperty("javax.net.ssl.trustStore", "ssl/truststore.jks");
			System.setProperty("javax.net.ssl.trustStorePassword", CfgManager.getPassword("ts"));
		}

		File dir = new File(logDir);
		if (!dir.exists())
			dir.mkdir();
	}

	/***************************************************************
	 * Funzioni relative agli utenti e alla gestione del programma.*
	 ***************************************************************/

	/**
	 * Verifica l'esistenza di un utente "root" nel DB. In caso affermativo porta
	 * alla schermata di login; viceversa, porta alla schermata di creazione utente
	 * root. <br/>
	 * E' richiamata dalla view all'avvio del modulo.
	 */
	public void boot() {
		try {
			pm.setState(pmDB.existsRootUser() ? StatePM.LOGIN : StatePM.NO_ROOT);
			updateView();
		} catch (PEException e) {
			printError(e);
		}
	}

	/**
	 * Verifica i dati inseriti nella schermata di login richiamando
	 * {@link AbstrController#checkLoginData(model.AbstrModel, db.DB, String, String)}.
	 * Se il login è verificato, aggiorna lo stato del model e la view, per
	 * permettere il caricamento della nuova scena. <br/>
	 * Completato il login, il nuovo stato del model e la conseguente scena caricata
	 * dipendono dal ruolo dell'utente loggato.
	 * 
	 * @param user Username dell'utente
	 * @param psw  Password dell'utente
	 */
	public void login(String username, String password) {
		if (checkLoginData(pm, pmDB, username, password)) {
			try {
				StatePM state;
				String userType = pmDB.getUserType(username);

				switch (userType) {
				case "Root":
					state = StatePM.ROOT;
					break;

				case "Technic":
					state = StatePM.TECHNIC;
					break;

				case "Supervisor":
					state = StatePM.SUPERVISOR;
					break;

				default:
					throw DEVException.DEV_08(userType);
				}

				pm.setState(state);
				updateView();
			} catch (PEException e) {
				printError(e);
			}
		}
	}

	/**
	 * Chiede la conferma per eseguire il logout richiamando
	 * {@link AbstrController#confirmLogout(model.AbstrModel)} (a cui passa il
	 * proprio model). Se confermato, il model viene riportato allo stato LOGIN e
	 * viene aggiorna di conseguenza la view.
	 */
	public void logout() {
		if (confirmLogout(pm)) {
			pm.setState(State.StatePM.LOGIN);
			updateView();
		}
	}

	/**
	 * Restituisce tutti i supervisori presenti nel DB; è richiamata dalla view nel
	 * momento in cui un tecnico (o root) completa il login. <br/>
	 * E' utilizzata per permettere di scegliere un supervisore da assegnare ad una
	 * nuova procedura.
	 * 
	 * @return La lista di tutti gli utenti con ruolo "supervisore", o null
	 */
	public ArrayList<String> getAllSupervisors() {
		ArrayList<String> usernames = null;

		try {
			usernames = pmDB.getAllSupervisors();
		} catch (PEException e) {
			e.printStackTrace();
			printError(e);
		}

		return usernames;
	}

	/**
	 * Effettua tutte le operazioni preliminari richieste per la creazione di un
	 * nuovo utente. Verifica che l'operazione sia consentita per l'utente
	 * attualmente loggato e che tutti i dati richiesti siano stati inseriti. <br/>
	 * A seconda della tipologia di utente creato invoca funzioni diverse. In
	 * particolare:
	 * <ul>
	 * <li>Per un supervisore, richiama la funzione
	 * {@link #createSupervisor(String, String)}</li>
	 * <li>Per tecnici o root, richiama la funzione
	 * {@link #createUser(String, String, String, byte[], byte[], byte[], byte[])}</li>
	 * <li>Solo per root, aggiorna lo stato del model e forza l'aggiornamento della
	 * view</li>
	 * </ul>
	 * 
	 * @param type       Tipo di utente da creare ("Supervisore", "Tecnico" o
	 *                   "Root")
	 * @param username   Username dell'utente da creare
	 * @param psw        Password dell'utente da creare
	 * @param confirmPsw Conferma della password dell'utente da creare
	 * @return True se la creazione dell'utente va a buone fine, false altrimenti.
	 */
	public boolean startUserCreation(TipoStaff type, String username, String psw, String confirmPsw) {
		if (type == null) {
			printError(FileUtils.getResourceBundle("etiqueta_creacion_usuario_fallo"),
					FileUtils.getResourceBundle("etiqueta_selecciona_tipo_usuario"));
			return false;
		}

		// La operación solo está permitida si estamos creando supervisor/técnico y
		// estamos logueados como root, o estamos creando root porque no hay
		// L'operazione è consentita solo se stiamo creando supervisore/tecnico e siamo
		// loggati come Root, o stiamo creando root perchè non c'è
		StatePM state = pm.getState();
		boolean operationAllowed = ((TipoStaff.Supervisor.equals(type)) || TipoStaff.Technic.equals(type))
				&& state == StatePM.ROOT || (TipoStaff.Root.equals(type) && state == StatePM.NO_ROOT);

		// TODO: inserire DEVExcepion specifica
		if (!operationAllowed) {

			printError(FileUtils.getResourceBundle("etiqueta_creacion_usuario_fallo"),
					"Impossibile creare l'utente. Verifica di avere i permessi necessari.");
			return false;
		}

		// TODO: aggiungere restrizioni sulla lunghezza/caratteri del nome e della
		// password(?)
		if (username == null || username.isEmpty()) {
			printError(FileUtils.getResourceBundle("etiqueta_creacion_usuario_fallo"),
					FileUtils.getResourceBundle("etiqueta_introduce_nombre_usuario"));
			return false;
		}

		if (psw == null || psw.isEmpty()) {
			printError(FileUtils.getResourceBundle("etiqueta_creacion_usuario_fallo"),
					FileUtils.getResourceBundle("etiqueta_introduce_contrasena"));
			return false;
		}

		if (confirmPsw == null || confirmPsw.isEmpty()) {
			printError(FileUtils.getResourceBundle("etiqueta_creacion_usuario_fallo"),
					FileUtils.getResourceBundle("etiqueta_introduce_conf_contrasena"));
			return false;
		}

		if (!psw.equals(confirmPsw)) {
			printError(FileUtils.getResourceBundle("etiqueta_creacion_usuario_fallo"),
					FileUtils.getResourceBundle("etiqueta_contrasena_conf_contrasena_no_coinciden"));
			return false;
		}

		if (!printConfirmation(FileUtils.getResourceBundle("etiqueta_proceder_creacion_usuario"),
				FileUtils.getResourceBundle("etiqueta_crea_usuario") + " " + username + " "
						+ FileUtils.getResourceBundle("etiqueta_de_tipo") + " " + type)) {
			return false;
		}

		boolean success = false;

		switch (type) {
		case Supervisor:
			success = createSupervisor(username, psw);
			break;

		case Technic:
			success = createUser(username, psw, type, null, null, null, null);
			break;

		case Root:
			if (success = createUser(username, psw, type, null, null, null, null)) {
				if (pm.getState().equals(StatePM.NO_ROOT))
					pm.setState(StatePM.LOGIN);
				updateView();
			}
			break;

		default:
			printError("Creazione Utente Non Riuscita", "Il tipo selezionato per l'utente non è corretto.");
			return false;
		}

		return success;
	}

	/**
	 * Gestisce la creazione di un supervisore. Utilizza le funzioni del package
	 * encryption del modulo Common per generare due coppie di chiavi asimmetriche,
	 * quindi richiama
	 * {@link #createUser(String, String, String, byte[], byte[], byte[], byte[])}
	 * per finalizzare la creazione del supervisore e l'inserimento dei suoi dati
	 * nel DB. <br/>
	 * La prima coppia di chiavi è utilizzata per cifrare/decifrare i pacchetti di
	 * voto, mentre la seconda coppia serve ad apporre una firma digitale su ogni
	 * pacchetto, per garantire l'integrità dello stesso. <br/>
	 * Le chiavi private vengono cifrate tramite AES, utilizzando come chiave la
	 * password dell'utente, prima di essere memorizzate nel DB.
	 * 
	 * @param username Username dell'utente da creare
	 * @param password Password dell'utente da creare
	 * @return True se la creazione dell'utente va a buon fine, false altrimenti
	 */
	private boolean createSupervisor(String username, String password) {
		// Creiamo le 2 coppie di chiavi asimmetriche necessarie ai responsabili di
		// procedimento
		byte[] publicKey1, encryptedPrivateKey1, publicKey2, encryptedPrivateKey2;
		KeyPair newPair;

		try {
			newPair = KeyPairManager.genKeyPair();
			publicKey1 = newPair.getPublic().getEncoded();
			encryptedPrivateKey1 = AES.encryptPrivateKey(newPair.getPrivate().getEncoded(), password);

			newPair = KeyPairManager.genKeyPair();
			publicKey2 = newPair.getPublic().getEncoded();
			encryptedPrivateKey2 = AES.encryptPrivateKey(newPair.getPrivate().getEncoded(), password);
		} catch (PEException e) {
			printError(e);
			return false;
		}

		// Tutta la logica viene eseguita da createUser
		return createUser(username, password, TipoStaff.Supervisor, publicKey1, encryptedPrivateKey1, publicKey2,
				encryptedPrivateKey2);
	}

	/**
	 * Finalizza la creazione di un nuovo utente richiamando le funzioni del DB
	 * necessarie all'inserimento dei dati. Inoltre, effettua ulteriori verifiche
	 * sull'unicità dell'username scelto, sul ruolo per il nuovo utente e sulla
	 * consistenza dei dati (i supervisori devono avere 2 coppie di chiavi
	 * asimmetriche non nulle). <br/>
	 * Richiama
	 * {@link PMDB#insertSupervisor(String, String, byte[], byte[], byte[], byte[], byte[])}
	 * per l'inserimento di un supervisore,
	 * {@link PMDB#insertUser(String, String, byte[])} per l'inserimento di un
	 * tecnico o di root.
	 * 
	 * @param username  Username dell'utente da creare
	 * @param password  Password dell'utente da creare
	 * @param role      Ruolo dell'utente da creare ("Root", "Technic",
	 *                  "Supervisor")
	 * @param pubKey1   Chiave pubblica della prima coppia di chiavi asimmetriche di
	 *                  un supervisore, o null
	 * @param encPrKey1 Chiave privata, cifrata, della prima coppia di chiavi
	 *                  asimmetriche di un supervisore, o null
	 * @param pubKey2   Chiave pubblica della seconda coppia di chiavi asimmetriche
	 *                  di un supervisore, o null
	 * @param encPrKey2 Chiave privata, cifrata, della prima seconda di chiavi
	 *                  asimmetriche di un supervisore, o null
	 * @return True se la creazione dell'utente va a buon fine, false altrimenti
	 */
	private boolean createUser(String username, String password, TipoStaff role, byte[] pubKey1, byte[] encPrKey1,
			byte[] pubKey2, byte[] encPrKey2) {

		if (!List.of(TipoStaff.values()).contains(role)) {
			// if (!List.of("Root", "Technic", "Supervisor").contains(role)) {
			printError("Creazione Utente Non Riuscita", "Il tipo selezionato per l'utente non è corretto.");
			return false;
		}

		if (TipoStaff.Supervisor.equals(role)
				&& (pubKey1 == null || encPrKey1 == null || pubKey2 == null || encPrKey2 == null)) {
			// TODO: Sostituire con una PEException
			printError("Creazione Utente Non Riuscita",
					"Richieste chiavi asimmetriche per creare un utente di tipo supervisor.");
			return false;
		}

		boolean success;
		try {
			// Se comprueba que el nombre de usuario está libre
			// Si verifica che l'username sia libero
			if (pmDB.existsUsername(username)) {
				printError(FileUtils.getResourceBundle("etiqueta_creacion_usuario_fallo"),
						FileUtils.getResourceBundle("etiqueta_el_usuario") + " " + username + " "
								+ FileUtils.getResourceBundle("eiqueta_ya_existe"));
				return false;
			}

			// Si calcola l'hash della password da memorizzare nel DB
			int hashSize = 16;
			byte[] hashedPassword = Hash.computeHash(password, hashSize, "password");

			// Se stiamo creando un supervisor adoperiamo la funzione che aggiunge le chiavi
			if (TipoStaff.Supervisor.equals(role))
				success = pmDB.insertSupervisor(username, role.toString(), hashedPassword, pubKey1, encPrKey1, pubKey2,
						encPrKey2);
			else
				success = pmDB.insertUser(username, role.toString(), hashedPassword);

			if (success)
				printSuccess(FileUtils.getResourceBundle("etiqueta_creacion_usuario_exito"),
						FileUtils.getResourceBundle("etiqueta_el_usuario") + " " + username + " "
								+ FileUtils.getResourceBundle("etiqueta_creacion_correcta"));
			else
				printError(FileUtils.getResourceBundle("etiqueta_creacion_usuario_fallo"),
						"Si è verificato un errore durante l'inserimento dei dati dell'utente nel database.");

			return success;
		} catch (PEException e) {
			printError(e);
			return false;
		}
	}

	/****************************************************
	 * Funzioni relative alla creazione delle Procedure *
	 ****************************************************/

	/**
	 * Si occupa di creare un template valido per ogni file CSV richiesto per la
	 * creazione di una nuova procedura: schede, candidati (e liste elettorali),
	 * sessioni (e terminali), votanti. <br/>
	 * Inserisce in ogni file il titolo, una spiegazione della formattazione e dei
	 * campi attesi ed un piccolo insieme di esempi validi. <br/>
	 * Avvisa l'utente se riscontra errori nella creazione dei file richiesti.
	 */
	public void createValidSampleFiles() {
		String sampleCsvDir = System.getProperty("user.dir") + "/sample_csv/";

		File dir = new File(sampleCsvDir);
		if (!dir.exists())
			dir.mkdir();

		ArrayList<String> nonGeneratedFiles = new ArrayList<>();
		try (FileWriter fw = new FileWriter(sampleCsvDir + "template_schede.csv")) {
			fw.write("# SCHEDE\n");
			fw.write(
					"#C; Codice; Titolo; Descrizione; Num. Preferenze; Cod. Candidato 1 [: Cod. Lista]; [...] ; Cod. Candidato N [: Cod. Lista]\n");
			fw.write("#O; Codice; Titolo; Descrizione; Num. Preferenze; Opzione 1; [...] ; Opzione N\n\n");
			fw.write("# ESEMPIO\n");
			fw.write("#C; 1; Scheda 1; Scheda 1; 3; ABC00; ABC01 : 1; ABC02 : 2\n");
			fw.write("#C; 2; Scheda 2; Descrizione; 1; CF314 : 3\n");
			fw.write("#O; 3; Scheda 3; Referendum; 1; Sì; No\n");
			fw.write("#------------------------------------------------------\n\n");
		} catch (IOException e) {
			nonGeneratedFiles.add("template_schede");
			e.printStackTrace();
		}

		try (FileWriter fw = new FileWriter(sampleCsvDir + "template_candidati.csv")) {
			fw.write("# CANDIDATI E LISTE ELETTORALI\n");
			fw.write("#C; Codice Candidato; Nome; Cognome; Data di Nascita (Formato: gg/mm/aaaa o NULL)\n");
			fw.write("#L; Codice Lista; Nome\n\n");
			fw.write("# ESEMPIO\n");
			fw.write("#C; ABC00; Nome1; Cognome1; 01/01/1970\n");
			fw.write("#C; ABC01; Nome2; Cognome2; NULL\n");
			fw.write("#C; CF314; Pi; Greco; 14/03/1970\n");
			fw.write("#L; Nome Lista Elettorale\n");
			fw.write("#------------------------------------------------------\n\n");
		} catch (IOException e) {
			nonGeneratedFiles.add("template_candidati");
			e.printStackTrace();
		}

		try (FileWriter fw = new FileWriter(sampleCsvDir + "template_sessioni.csv")) {
			fw.write("#SESSIONI E TERMINALI\n");
			fw.write("#Codice Sessione (temporaneo); Timestamp Inizio (Formato: gg/mm/aaa hh:mm:ss); Timestamp Fine\n");
			fw.write(
					"#Codice Sessione; IP Seggio; IP Postazione 1 : ... : IP Postazione N; IP Seggio Ausiliario 1 : ... : IP Seggio Ausiliario N\n\n");
			fw.write("# ESEMPIO\n");
			fw.write("#1; 01/01/2020 00:00:01; 31/12/2020 23:59:59\n");
			fw.write("#1; 192.168.1.1; 192.168.1.2 : 192.168.1.3; 192.168.1.3 : 192.168.1.4;\n");
			fw.write("#1; 192.168.1.25; 192.168.1.26; 192.168.1.27\n");
			fw.write("#------------------------------------------------------\n\n");
		} catch (IOException e) {
			nonGeneratedFiles.add("template_sessioni");
			e.printStackTrace();
		}

		try (FileWriter fw = new FileWriter(sampleCsvDir + "template_votanti.csv")) {
			fw.write("#VOTANTI\n");
			fw.write(
					"#ID Votante; Nome; Cognome; Codice Scheda 1, ..., Codice Scheda N; Data di Nascita (Formato: gg/mm/aaaa o NULL)\n\n");
			fw.write("# ESEMPIO\n");
			fw.write("#ABC00; Nome1; Cognome1; 1,2; 01/01/1970\n");
			fw.write("#ABC01; Nome2; Cognome2; 3; NULL\n");
			fw.write("#CF314; Pi; Greco; 1,3; 14/03/1970\n");
			fw.write("#------------------------------------------------------\n\n");
		} catch (IOException e) {
			nonGeneratedFiles.add("template_votanti");
			e.printStackTrace();
		}

		int errors = nonGeneratedFiles.size();
		if (errors == 0)
			printSuccess(FileUtils.getResourceBundle("etiqueta_plantilla_terminada"),
					FileUtils.getResourceBundle("etiqueta_visualizacion_carpeta") + " " + sampleCsvDir);
		else if (errors < 4) {
			String errDesc = FileUtils.getResourceBundle("etiqueta_generado_archivos");
			for (String file : nonGeneratedFiles)
				errDesc += file + ", ";
			errDesc.substring(0, errDesc.length() - 2);

			printWarning(FileUtils.getResourceBundle("etiqueta_plantilla_terminada"), errDesc);
		} else {
			printError(FileUtils.getResourceBundle("etiqueta_plantilla_terminada"),
					FileUtils.getResourceBundle("etiqueta_no_creacion_plantillas"));
		}
	}

	/**
	 * Verifica i dati inseriti per la creazione di una nuova procedura, inizializza
	 * l'oggetto newProcedure (di tipo {@link ProcedurePM}) e ne setta i parametri
	 * iniziali.
	 * 
	 * @param name          Nome della procedura
	 * @param strStarts     Timestamp di inizio procedura, DEVE essere nel formato
	 *                      "dd/MM/yyyy hh:mm:ss".
	 * @param strEnds       Timestamp di fine procedura, DEVE essere nel formato
	 *                      "dd/MM/yyyy hh:mm:ss".
	 * @param strNumBallots Numero di schede previsto (stringa)
	 * @param supervisor    Username del supervisore a cui assegnare la procedura
	 * @return True se la creazione della procedura va a buon fine, false altrimenti
	 */

	public boolean createNewProcedure(String name, String strStarts, String strEnds, String strNumBallots,
			String supervisor) {
		// Si effettuano controlli su tutti i parametri settati dalla GUI.
		if (name == null || name.isEmpty()) {
			// TODO: mettere altre restrizioni oltre al non essere vuoto?
			printError(FileUtils.getResourceBundle("etiqueta_datos_insertados_no_validos"),
					FileUtils.getResourceBundle("etiqueta_rellena_campo_nombre"));
			return false;
		}

		if (supervisor == null || supervisor.isEmpty()) {
			// TODO: mettere altre restrizioni oltre al non essere vuoto?
			printError(FileUtils.getResourceBundle("etiqueta_datos_insertados_no_validos"),
					FileUtils.getResourceBundle("etiqueta_elige_supervisor"));
			return false;
		}

		LocalDateTime start = FileUtils.dateStringToLocalDateTime(strStarts);
		;
		if (start == null) {
			printError(FileUtils.getResourceBundle("etiqueta_datos_insertados_no_validos"),
					FileUtils.getResourceBundle("etiqueta_fecha_inicio_procedimiento") + " " + (" + strStarts + ") + " "
							+ FileUtils.getResourceBundle("etiqueta_no_valida"));
			return false;
		}

		LocalDateTime end = FileUtils.dateStringToLocalDateTime(strEnds);
		if (end == null) {
			printError(FileUtils.getResourceBundle("etiqueta_datos_insertados_no_validos"),
					FileUtils.getResourceBundle("etiqueta_fecha_fin_procedimiento") + " " + (" + strStarts + ") + " "
							+ FileUtils.getResourceBundle("etiqueta_no_valida"));
			return false;
		}

		int numBallots;
		try {
			numBallots = Integer.parseInt(strNumBallots);
		} catch (NumberFormatException e) {
			e.printStackTrace();
			numBallots = -1;
		}

		if (numBallots <= 0) {
			printError(FileUtils.getResourceBundle("etiqueta_datos_insertados_no_validos"),
					strNumBallots + FileUtils.getResourceBundle("etiqueta_no_numero_valido_tarjetas"));
			return false;
		}

		try {
			// Se busca en la base de datos el único parámetro que queda: el primer entero
			// disponible para usar como código de procedimiento

			// Si cerca nel DB l'unico parametro rimasto: il primo intero disponibile da
			// usare come codice di procedura
			// TODO: Usare auto_increment anzichè cercare il primo intero libero
			int code = pmDB.findNextFreeProcedureCode();
			newProcedure = new ProcedurePM(code, name, start, end, numBallots, supervisor);
			return true;

		} catch (PEException e) {
			printError(FileUtils.getResourceBundle("etiqueta_imposible_crear_procedimiento"),
					FileUtils.getResourceBundle("etiqueta_fecha_inicio_posterior_fecha_finalizacion"));
			e.printStackTrace();
		}

		newProcedure = null;
		return false;
	}

	/*********************************************
	 * Funzioni relative al parsing dei file CSV *
	 *********************************************/

	/**
	 * Controlla che il file delle sessioni e delle postazioni di voto sia valido
	 * rispetto alla formattazione e ai dati attesi. Inoltre, controlla che lo stato
	 * sia corretto e che la procedura sia stata inizializzata. <br/>
	 * Istanzia un oggetto {@link SessionsParser} e ne richiama il metodo
	 * {@link SessionsParser#checkSessionsAndVotingPlaces(String)
	 * checkSessionsAndVotingTerminals(String)} per effettuare il controllo del file
	 * passato a parametro.
	 * 
	 * @param pathToSessionsFile Percorso del file delle sessioni
	 * @return True se il file è valido, false altrimenti
	 */
	public boolean checkSessionsAndVotingPlaces(String pathToSessionsFile) {
		if (!checkStateAndProcedure())
			return false;

		return SessionsParser.checkSessionsAndVotingPlaces(this, newProcedure, pathToSessionsFile);
	}

	/**
	 * Controlla che il file dei candidati e delle liste elettorali sia valido
	 * rispetto alla formattazione e ai dati attesi. Inoltre, controlla che lo stato
	 * sia corretto e che la procedura sia stata inizializzata. <br/>
	 * Istanzia un oggetto {@link CandidatesParser} e ne richiama il metodo
	 * {@link CandidatesParser#checkCandidatesAndLists(String)
	 * checkCandidatesAndLists(String)} per effettuare il controllo del file passato
	 * a parametro.
	 * 
	 * @param pathToCandidatesFile Percorso del file dei candidati
	 * @return True se il file è valido, false altrimenti
	 */
	public boolean checkCandidatesAndLists(String pathToCandidatesFile) {
		if (!checkStateAndProcedure())
			return false;

		return CandidatesParser.checkCandidatesAndLists(this, newProcedure, pathToCandidatesFile);
	}

	/**
	 * Controlla che il file delle schede sia valido rispetto alla formattazione e
	 * ai dati attesi. Inoltre, controlla che lo stato sia corretto e che la
	 * procedura sia stata inizializzata. <br/>
	 * Istanzia un oggetto {@link BallotsParser} e ne richiama il metodo
	 * {@link BallotsParser#checkBallots(String) checkBallots(String)} per
	 * effettuare il controllo del file passato a parametro.
	 * 
	 * @param pathToBallotsFile Percorso del file delle schede
	 * @return True se il file è valido, false altrimenti
	 */
	public boolean checkBallots(String pathToBallotsFile) {
		if (!checkStateAndProcedure())
			return false;

		return BallotsParser.checkBallots(this, newProcedure, pathToBallotsFile);
	}

	/**
	 * Controlla che il file dei votanti sia valido rispetto alla formattazione e ai
	 * dati attesi. Inoltre, controlla che lo stato sia corretto e che la procedura
	 * sia stata inizializzata. <br/>
	 * Istanzia un oggetto {@link VotersParser} e ne richiama il metodo
	 * {@link VotersParser#checkVoters(String) checkVoters(String)} per effettuare
	 * il controllo del file passato a parametro.
	 * 
	 * @param pathToVotersFile Percorso del file dei votanti
	 * @return True se il file è valido, false altrimenti
	 */
	public boolean checkVoters(String pathToVotersFile) {
		if (!checkStateAndProcedure())
			return false;

		return VotersParser.checkVoters(this, newProcedure, pathToVotersFile);
	}

	/*****************************************************
	 * Funzione che carica i dati della procedura nel DB *
	 *****************************************************/

	/**
	 * Effettua il caricamento della nuova procedura nel DB, dopo aver effettuato le
	 * ultime opportune verifiche. <br/>
	 * Verifica se lo stato è valido, se la procedura è stata inizializzata e se
	 * tutti i file richiesti sono stati inseriti. Quindi, richiama
	 * {@link ProcedurePM#checkProcedureReadyAndGetBallots(ArrayList)} per
	 * assicurarsi che la procedura sia pronta ad essere inserita nel DB. <br/>
	 * Infine, richiama
	 * {@link PMDB#uploadProcedure(ProcedurePM, String, EmptyBallot[])} per inserire
	 * la procedura nel DB.
	 * 
	 * @param sessionsFile   Percorso del file delle sessioni
	 * @param candidatesFile Percorso del file dei candidati
	 * @param ballotsFile    Percorso del file delle schede
	 * @param votersFile     Percorso del file dei votanti
	 * @return True se l'inserimento della procedura nel DB va a buon fine, false
	 *         altrimenti
	 */
	public boolean uploadProcedure(String sessionsFile, String candidatesFile, String ballotsFile, String votersFile) {
		// Eseguiamo i controlli preventivi
		if (!checkStateAndProcedure())
			return false;

		if (sessionsFile == null || sessionsFile.isEmpty() || candidatesFile == null || candidatesFile.isEmpty()
				|| ballotsFile == null || ballotsFile.isEmpty() || votersFile == null || votersFile.isEmpty()) {
			printError(FileUtils.getResourceBundle("etiqueta_imposible_crear_procedimiento"),
					FileUtils.getResourceBundle("etiqueta_asegurarse_subir_todos_ficheros"));
			return false;
		}

		ArrayList<String> errors = new ArrayList<>();
		EmptyBallot[] procedureBallots = newProcedure.checkProcedureReadyAndGetBallots(errors);
		if (procedureBallots == null || !errors.isEmpty()) {
			writeLogFile("creazione_procedura", errors);
			return false;
		}

		try {
			pmDB.uploadProcedure(newProcedure, procedureBallots);
			printSuccess(FileUtils.getResourceBundle("etiqueta_procedimiento_completado"),
					FileUtils.getResourceBundle("etiqueta_procedimiento_anadido_exito") + " " + newProcedure.getCode());

//			sendMail(newProcedure);

			return true;

		} catch (PEException e) {
			printError(e);
		}

		return false;
	}

	/***********************
	 * Funzioni di utility.*
	 ***********************/

	/*private void sendMail(ProcedurePM proc) {

		HashMap<String, Person> voters = newProcedure.getVoters();
		for (String voterID : voters.keySet()) {
			Person voter = voters.get(voterID);

			if (!voter.getEmail().isEmpty()) {
				
				// Email information such as from, to, subject and contents.
				String mailFrom = "email";
				String mailTo = voter.getEmail();
				String mailSubject = "Token";
				String mailText = RandStrGenerator.genActivationToken();

				Properties properties = new Properties();

				properties.put("mail.smtp.auth", "true");
				properties.put("mail.smtp.host", "smtp.gmail.com");
				properties.put("mail.smtp.port", "587");
				properties.put("mail.smtp.starttls.enable", "true");
				properties.put("mail.smtp.ssl.protocols", "TLSv1.2");
				properties.put("mail.smtp.ssl.trust", "smtp.gmail.com");


				// Creates a mail session. We need to supply username and
				// password for Gmail authentication.
				Session session = Session.getInstance(properties, new Authenticator() {
					@Override
					protected PasswordAuthentication getPasswordAuthentication() {
						return new PasswordAuthentication(mailFrom, "password");
					}
				});

				try {

					// Creates email message
					Message message = new MimeMessage(session);
					message.setSentDate(new Date());
					message.setFrom(new InternetAddress(mailFrom));
					message.setRecipient(Message.RecipientType.TO, new InternetAddress(mailTo));
					message.setSubject(mailSubject);
					message.setText(mailText);

					// Send a message
					Transport.send(message);

					System.out.println("Success");

				} catch (MessagingException ex) {
					ex.printStackTrace();
				}
			}

		}

	}*/

	/**
	 * Esegue dei controlli preventivi (permessi utente e stato di avanzamento della
	 * procedura) prima di eseguire qualunque operazione legata alla procedura.
	 * 
	 * @return True se i controlli vengono superati, false altrimenti
	 */
	private boolean checkStateAndProcedure() {
		// Para realizar una operación es necesario haber iniciado sesión como técnico o
		// como root
		// Per eseguire una operazione bisogna avere effettuato l'accesso come tecnico o
		// come root
		StatePM state = pm.getState();
		if ((state != StatePM.ROOT && state != StatePM.TECHNIC)) {
			printError(ResourceBundle.getBundle("etiquetas").getString("etiqueta_operacion_fallida"),
					"Impossibile procedere, effettuare login come tecnico o tecnico root.");
			return false;
		}

		// Para realizar la operación el procedimiento debe haber sido inicializado
		// Per eseguire l'operazione la procedura deve essere stata inizializzata
		if (newProcedure == null) {
			printError(ResourceBundle.getBundle("etiquetas").getString("etiqueta_operacion_fallida"),
					"Settare i parametri iniziali.");
			return false;
		}

		return true;
	}

	/**
	 * Scrive in un file di log eventuali errori che si sono verificati durante la
	 * lettura di un file o durante la verifica finale sullo stato della procedura
	 * da creare. <br/>
	 * Prepende al nome del file un timestamp completo, in modo che i log siano
	 * automaticamente ordinati per data.
	 * 
	 * @param file   Nome del file
	 * @param errors Array contenente gli errori da stampare
	 */
	public void writeLogFile(String file, ArrayList<String> errors) {
		DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyyMMdd HHmmss");
		String fileName = dtf.format(LocalDateTime.now()).replaceAll(" ", "T") + "_" + file + ".log";
		String separator = "\n\n===========================================\n\n";

		try (FileWriter fw = new FileWriter(logDir + fileName)) {
			fw.write("ERRORI:\n\n");

			for (String error : errors) {
				fw.write(error);
				fw.write(separator);
			}

			printError("Errore Durante la Lettura del File",
					"Per ulteriori informazioni controllare il file " + fileName);

		} catch (IOException e) {
			e.printStackTrace();
			printError("Errore Durante la Lettura del File", errors.toString());
		}
	}
}
