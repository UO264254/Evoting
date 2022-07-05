package urna;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.fail;

import java.net.InetAddress;
import java.security.KeyPair;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

import org.junit.Before;
import org.junit.Test;

import common.Internet;
import common.Settings;
import common.TestView;
import controller.Link;
import db.ConnectionManager;
import db.DBMS;
import encryption.AES;
import encryption.Hash;
import encryption.KeyPairManager;
import encryption.RandStrGenerator;
import exceptions.DBException;
import exceptions.FLRException;
import exceptions.PEException;
import model.ElectoralList;
import model.EmptyBallot;
import model.Person;
import model.WrittenBallot;
import urna.controller.UrnDB;
import urna.model.Urn;
import utils.CfgManager;

/**
 * Unit test for simple App.
 */
public class AppTest {
	Internet internet = Internet.getInstance();

	private Urn u;
	private ControllerU c;
	private TestDB db;

	private InetAddress[] ipPosts;
	private InetAddress[] ipSubStations;
	private InetAddress ipStation;
	private InetAddress ipUrn;

	private final String host = "localhost";
	private final String port = "3306";
	private final String schema = "secureBallot";
	private DBMS manager;

	private final Person voterTest = new Person("voter", "test", "VT00", null, false);

	private TestView view = null;

	@Before
	public void setup() throws Exception {
		manager = new DBMS(host, port, schema, "Test");

		view = new TestView(Settings.viewBehaviour);

		ipPosts = new InetAddress[1];
		ipPosts[0] = InetAddress.getByName("127.0.0.1");

		ipSubStations = new InetAddress[0];

		ipStation = InetAddress.getByName("127.0.0.5");

		ipUrn = InetAddress.getByName("127.0.0.22");

		ArrayList<String> stationSessionKeyes = new ArrayList<>();
		stationSessionKeyes.add(RandStrGenerator.genSessionKey());

		ArrayList<String> postsSessionKeyes = new ArrayList<>();
		postsSessionKeyes.add(RandStrGenerator.genSessionKey());

		db = new TestDB(ipStation, ipPosts, ipSubStations);
		db.setSessionKeyes(stationSessionKeyes, null, postsSessionKeyes);

		u = new Urn(0, 5);
		c = new ControllerU(view, u, db, ipUrn);

		populateDB(true);

		c.start();
	}

	@Test
	public void unitTestingRealDBTest() throws Exception {
		if (Settings.printTestName) {
			System.out.println("\nrealDBTest");
		}

		if (Settings.testDB) {

			// Se necesita leer del archivo psws.cfg, las claves ks y ts
			System.setProperty("javax.net.ssl.keyStore", "ssl/keystore.jks");
			System.setProperty("javax.net.ssl.keyStorePassword", CfgManager.getPassword("ks"));

			System.setProperty("javax.net.ssl.trustStore", "ssl/truststore.jks");
			System.setProperty("javax.net.ssl.trustStorePassword", CfgManager.getPassword("ts"));

			// se vacía la base de datos actual
			emptyRealDB();

			// se crea una nueva
			populateRealDB();

			// Se crea la base de datos de la urna
			UrnDB uDB = new UrnDB(host, port, schema, "Test");

			// Se crean dos ballots
			WrittenBallot wb0 = new WrittenBallot("Votable Ballot", 0, 1);
			WrittenBallot wb1 = new WrittenBallot("Non Votable Ballot", 1, 1);

			WrittenBallot[] wbs0 = { wb0 };

			// IpStation
			InetAddress ipStation = InetAddress.getByName("127.0.0.1");

			// IpPost
			InetAddress ipPost = InetAddress.getByName("127.0.0.2");

			int procedureCode = 0;
			int sessionCode = 0;

			String fakeID = "fakeID";

			String fail = null;

			fail = extracted(uDB, wbs0, ipStation, ipPost, procedureCode, sessionCode, fakeID, fail);

			insertSessionInRealDB(procedureCode, sessionCode);

			fakeID = "fakeID";

			fail = extracted2(uDB, wbs0, ipStation, ipPost, procedureCode, sessionCode, fakeID, fail);

			fail = extracted3(uDB, wbs0, ipStation, ipPost, procedureCode, sessionCode, fail);

			insertTerminalsInRealDB(procedureCode, sessionCode, 0, ipStation, ipPost);

			InetAddress ipSecondStation = InetAddress.getByName("127.0.0.3");
			InetAddress ipSecondPost = InetAddress.getByName("127.0.0.4");

			extracted4(ipStation, ipPost, ipSecondStation, ipSecondPost);

			extracted5(uDB, wbs0, ipStation, procedureCode, sessionCode, fail, ipSecondPost);

			insertTerminalsInRealDB(procedureCode, sessionCode, 2, ipSecondStation, ipSecondPost);

			extracted6(uDB, wbs0, ipStation, procedureCode, sessionCode, fail, ipSecondPost);

			Person voterTest = new Person("voter", "test", "VT00", null, false);
			WrittenBallot[] wbs1 = { wb1 };

			extracted7(uDB, ipStation, ipPost, procedureCode, sessionCode, voterTest, wbs1);

			try {
				uDB.verifyVoteData(procedureCode, sessionCode, voterTest.getID(), wbs0, ipStation.getHostAddress(),
						ipPost.getHostAddress());
			} catch (PEException e) {
				fail();
			}

			try {
				voterTest.setDocumentType("Sin Documentación");
				uDB.storeVotes(procedureCode, sessionCode, voterTest, wbs0, ipStation, ipPost);
			} catch (PEException e) {
				fail();
			}

			fail = FLRException.FLR_11(voterTest, 0).getMessage();
			String res = null;

			extracted8(uDB, wbs0, ipStation, ipPost, procedureCode, sessionCode, fail, voterTest, res);

			// Se borra la BD
			emptyRealDB();
		}
	}

	private void extracted8(UrnDB uDB, WrittenBallot[] wbs0, InetAddress ipStation, InetAddress ipPost,
			int procedureCode, int sessionCode, String fail, Person voterTest, String res) {
		try {
			uDB.verifyVoteData(procedureCode, sessionCode, voterTest.getID(), wbs0, ipStation.getHostAddress(),
					ipPost.getHostAddress());
		} catch (PEException e) {
			res = e.getMessage();
		}

		assertEquals(fail, res);
	}

	private void extracted7(UrnDB uDB, InetAddress ipStation, InetAddress ipPost, int procedureCode, int sessionCode,
			Person voterTest, WrittenBallot[] wbs1) {
		String fail;
		fail = FLRException.FLR_11(voterTest, 2).getMessage();

		String res = null;
		try {
			uDB.verifyVoteData(procedureCode, sessionCode, voterTest.getID(), wbs1, ipStation.getHostAddress(),
					ipPost.getHostAddress());
		} catch (PEException e) {
			res = e.getMessage();
		}

		assertEquals(fail, res);
	}

	private void extracted6(UrnDB uDB, WrittenBallot[] wbs0, InetAddress ipStation, int procedureCode, int sessionCode,
			String fail, InetAddress ipSecondPost) {
		try {
			uDB.verifyVoteData(procedureCode, sessionCode, voterTest.getID(), wbs0, ipStation.getHostAddress(),
					ipSecondPost.getHostAddress());
		} catch (PEException e) {
			fail = e.getMessage();
		}

		String postDoNotBelongToStation = DBException.DB_09(ipSecondPost.getHostAddress(), ipStation.getHostAddress())
				.getMessage();
		assertEquals(postDoNotBelongToStation, fail);
	}

	private void extracted5(UrnDB uDB, WrittenBallot[] wbs0, InetAddress ipStation, int procedureCode, int sessionCode,
			String fail, InetAddress ipSecondPost) {
		try {
			uDB.verifyVoteData(procedureCode, sessionCode, voterTest.getID(), wbs0, ipStation.getHostAddress(),
					ipSecondPost.getHostAddress());

		} catch (PEException e) {
			fail = e.getMessage();
		}

		String noSuchPost = DBException.DB_08(2, ipSecondPost.getHostAddress()).getMessage();
		assertEquals(noSuchPost, fail);
	}

	private void extracted4(InetAddress ipStation, InetAddress ipPost, InetAddress ipSecondStation,
			InetAddress ipSecondPost) {
		assertNotEquals(ipPost, ipSecondPost);
		assertNotEquals(ipStation, ipSecondStation);
	}

	private String extracted3(UrnDB uDB, WrittenBallot[] wbs0, InetAddress ipStation, InetAddress ipPost,
			int procedureCode, int sessionCode, String fail) throws PEException {
		try {
			uDB.verifyVoteData(procedureCode, sessionCode, voterTest.getID(), wbs0, ipStation.getHostAddress(),
					ipPost.getHostAddress());

		} catch (PEException e) {
			fail = e.getMessage();
		}

		String noSuchStation = DBException.DB_08(0, ipStation.getHostAddress()).getMessage();
		assertEquals(noSuchStation, fail);

		return fail;
	}

	private String extracted2(UrnDB uDB, WrittenBallot[] wbs0, InetAddress ipStation, InetAddress ipPost,
			int procedureCode, int sessionCode, String fakeID, String fail) {
		try {
			uDB.verifyVoteData(procedureCode, sessionCode, fakeID, wbs0, ipStation.getHostAddress(),
					ipPost.getHostAddress());
		} catch (PEException e) {
			fail = e.getMessage();
		}

		assertEquals("Non risulta alcun votante con ID:" + fakeID, fail);
		return fail;
	}

	private String extracted(UrnDB uDB, WrittenBallot[] wbs0, InetAddress ipStation, InetAddress ipPost,
			int procedureCode, int sessionCode, String fakeID, String fail) {
		try {
			uDB.verifyVoteData(procedureCode, sessionCode, fakeID, wbs0, ipStation.getHostAddress(),
					ipPost.getHostAddress());
			fail();
		} catch (PEException e) {
			fail = e.getMessage();
		}

		assertEquals("Non esiste alcuna sessione 0 relativa alla procedura 0", fail);
		return fail;
	}

	private void populateRealDB() throws Exception {

		try (ConnectionManager cManager = manager.getConnectionManager()) {
			String voterID = voterTest.getID();
			String supervisor = "supervisor";
			String password = "12345";

			String root = "root";
			String passwordRoot = "12345";

			int procedureCode = 0;

			insertSupervisorInRealDB(cManager, supervisor, password);
			insertRootInRealDB(cManager, root, passwordRoot);
			insertProcedureInRealDB(cManager, procedureCode, supervisor);

			insertVoterInRealDB(cManager, procedureCode);
			insertBallotsInRealDB(cManager, voterID, procedureCode, supervisor);

		} catch (SQLException e) {
			e.printStackTrace();
			fail();
		}
	}

	private void insertVoterInRealDB(ConnectionManager cManager, int procedureCode) throws SQLException, PEException {

		String query = "INSERT INTO secureBallot.Voter(FirstName, LastName, ProcedureCode, ID) VALUES(?, ?, ?, ?) ; ";

		cManager.executeUpdate(query, voterTest.getFirstName(), voterTest.getLastName(), procedureCode,
				voterTest.getID());

		query = "SELECT * " + "FROM secureBallot.Voter AS V ;";

		ResultSet rs = cManager.executeQuery(query);

		boolean empty = true;
		while (rs.next()) {
			empty = false;
			assertEquals(rs.getString("V.FirstName"), voterTest.getFirstName());
			assertEquals(rs.getString("V.LastName"), voterTest.getLastName());
			assertEquals(rs.getString("V.ID"), voterTest.getID());

		}
		assertFalse(empty);
	}

	private void insertSessionInRealDB(int procedureCode, int sessionCode) throws PEException {

		ConnectionManager cManager = manager.getConnectionManager();

		String update = "INSERT INTO secureBallot.Session(ProcedureCode, Code, StartsAt, EndsAt) "
				+ "VALUES(?, ?, DATE_SUB(NOW(), INTERVAL 10 HOUR), DATE_ADD(NOW(), INTERVAL 10 HOUR)) ;";

		cManager.executeUpdate(update, procedureCode, sessionCode);
		cManager.close();

	}

	private void insertRootInRealDB(ConnectionManager cManager, String username, String password) throws Exception {

		byte[] hashedPassword = Hash.computeHash(password, 16, "password");

		byte[] publicKey, pr;
		KeyPair pair = KeyPairManager.genKeyPair();
		publicKey = pair.getPublic().getEncoded();
		pr = pair.getPrivate().getEncoded();

		byte[] encryptedPrivateKey = AES.encryptPrivateKey(pr, password);

		String update = "INSERT INTO Staff(UserName, Type, HashedPassword, PublicKey1, EncryptedPrivateKey1, PublicKey2, EncryptedPrivateKey2) VALUES(?, ?, ?, ?, ?, ?, ?)";

		cManager.executeUpdate(update, username, "Root", hashedPassword, publicKey, encryptedPrivateKey, publicKey,
				encryptedPrivateKey);

	}

	private void insertSupervisorInRealDB(ConnectionManager cManager, String username, String password)
			throws Exception {

		byte[] hashedPassword = Hash.computeHash(password, 16, "password");

		byte[] publicKey, pr;
		KeyPair pair = KeyPairManager.genKeyPair();
		publicKey = pair.getPublic().getEncoded();
		pr = pair.getPrivate().getEncoded();

		byte[] encryptedPrivateKey = AES.encryptPrivateKey(pr, password);

		String update = "INSERT INTO Staff(UserName, Type, HashedPassword, PublicKey1, EncryptedPrivateKey1) VALUES(?, ?, ?, ?, ?)";

		cManager.executeUpdate(update, username, "Supervisor", hashedPassword, publicKey, encryptedPrivateKey);

	}

	private void insertProcedureInRealDB(ConnectionManager cManager, int procedureCode, String supervisor)
			throws PEException {

		String query = "INSERT INTO secureBallot.Procedure(Code, Supervisor, Name, Starts, Ends) VALUES(?, ?, 'Test Procedure', DATE_SUB(NOW(), INTERVAL 2 HOUR), DATE_ADD(NOW(), INTERVAL 2 HOUR)) ; ";
		cManager.executeUpdate(query, procedureCode, supervisor);
	}

	private void insertBallotsInRealDB(ConnectionManager cManager, String ID, int procedureCode, String supervisor)
			throws SQLException, PEException {
		/*
		 * String query =
		 * "INSERT INTO secureBallot.Procedure(Code, Supervisor, Name, Starts, Ends) VALUES(?, ?, 'Test Procedure', DATE_SUB(NOW(), INTERVAL 2 HOUR), DATE_ADD(NOW(), INTERVAL 2 HOUR)) ; "
		 * ;
		 * 
		 * cManager.executeUpdate(query, procedureCode, supervisor);
		 */

		String query = "INSERT INTO secureBallot.Ballot(ProcedureCode, Code, Name, Description, MaxPreferences) VALUES(?, ?, ?, ?, ?) ; ";

		String[] name = { "Votable Ballot", "Non Votable Ballot" };
		String[] description = { "A test ballot that can be voted by the single test user.",
				"A test ballot that cannot be voted by the single test user." };
		int preferences = 1;

		for (int i = 0; i < 2; i++) {
			cManager.executeUpdate(query, procedureCode, i, name[i], description[i], preferences);
		}

		query = "INSERT INTO secureBallot.VoterBallotsList(ProcedureCode, BallotCode, VoterID) VALUES(?, ?, ?) ;";

		cManager.executeUpdate(query, procedureCode, 0, ID);

		query = "INSERT INTO secureBallot.ReferendumOption(ProcedureCode, BallotCode, Text) VALUES(?, ?, ?) ;";
		String[] texts = { "Yes", "No" };

		for (int i = 0; i < 2; i++) {

			for (String text : texts) {
				cManager.executeUpdate(query, procedureCode, i, text);
			}

		}

		query = "select * from secureBallot.Ballot as B;";
		ResultSet rs = cManager.executeQuery(query);

		int i = 0;
		while (rs.next()) {
			assertEquals(rs.getString("B.Name"), name[i]);
			assertEquals(rs.getString("B.Description"), description[i]);
			assertEquals(rs.getInt("B.MaxPreferences"), preferences);
			assertEquals(rs.getInt("B.Code"), i);

			i++;
		}
	}

	private void insertTerminalsInRealDB(int procedureCode, int sessionCode, int id, InetAddress ipStation,
			InetAddress ipPost) throws PEException {
		ConnectionManager cManager = manager.getConnectionManager();

		String query = "INSERT INTO secureBallot.Terminal(ProcedureCode, SessionCode, ID, IPAddress, Type) VALUES(?, ?, ?, ?, 'Station'), (?, ?, ?, ?, 'Post')";

		cManager.executeUpdate(query, procedureCode, sessionCode, id, ipStation.getHostAddress(), procedureCode,
				sessionCode, id + 1, ipPost.getHostAddress());

		query = "INSERT INTO secureBallot.IsStationOf(ProcedureCode, SessionCode, Station, Terminal) VALUES(?, ?, ?, ?)";

		cManager.executeUpdate(query, procedureCode, sessionCode, id, id + 1);
		cManager.close();
	}

	private void emptyRealDB() throws PEException {

		try (ConnectionManager cManager = manager.getConnectionManager()) {
			// Relazioni

			String query = "DELETE FROM secureBallot.IsStationOf ;";
			cManager.executeUpdate(query);

			query = "DELETE FROM secureBallot.VoterBallotsList ;";
			cManager.executeUpdate(query);

			query = "DELETE FROM secureBallot.HasVoted ;";
			cManager.executeUpdate(query);

			query = "DELETE FROM secureBallot.ReferendumOption ;";
			cManager.executeUpdate(query);

			query = "DELETE FROM secureBallot.Member ;";
			cManager.executeUpdate(query);

			query = "DELETE FROM secureBallot.ElectoralList ;";
			cManager.executeUpdate(query);

			query = "DELETE FROM secureBallot.Running ;";
			cManager.executeUpdate(query);

			query = "DELETE FROM secureBallot.IsStationOf ;";
			cManager.executeUpdate(query);

			query = "DELETE FROM secureBallot.SessionKey ;";
			cManager.executeUpdate(query);

			// Entità

			query = "DELETE FROM secureBallot.Voter ;";
			cManager.executeUpdate(query);

			query = "DELETE FROM secureBallot.Vote ;";
			cManager.executeUpdate(query);

			query = "DELETE FROM secureBallot.Candidate ;";
			cManager.executeUpdate(query);

			query = "DELETE FROM secureBallot.Ballot ;";
			cManager.executeUpdate(query);

			query = "DELETE FROM secureBallot.SessionKey ;";
			cManager.executeUpdate(query);

			query = "DELETE FROM secureBallot.Terminal";
			cManager.executeUpdate(query);

			query = "DELETE FROM secureBallot.Session ;";
			cManager.executeUpdate(query);

			query = "DELETE FROM secureBallot.Procedure ;";
			cManager.executeUpdate(query);

			query = "DELETE FROM secureBallot.Staff ;";
			cManager.executeUpdate(query);
		}
	}

	private void populateDB(boolean well) {
		Person p00 = new Person("P", "00", null, "p00");
		Person p01 = new Person("P", "01", null, "p01");
		Person p02 = new Person("P", "02", null, "p02");

		Person p10 = new Person("P", "10", null, "p10");
		Person p11 = new Person("P", "11", null, "p11");
		Person p12 = new Person("P", "12", null, "p12");

		Person p20 = new Person("P", "20", null, "p20");
		Person p21 = new Person("P", "21", null, "p21");
		Person p22 = new Person("P", "22", null, "p22");

		ArrayList<EmptyBallot> schede = new ArrayList<>();
		schede.add(new EmptyBallot("Scheda 0", 0, null, 1).addList(new ElectoralList("Lista 0").addPerson(p00).end())
				.addList(new ElectoralList("Lista 1").addPerson(p01).end())
				.addList(new ElectoralList("Lista 2").addPerson(p02).end()));

		schede.add(new EmptyBallot("Scheda 1", 0, null, 1).addList(new ElectoralList("Lista 0").addPerson(p10).end())
				.addList(new ElectoralList("Lista 1").addPerson(p11).end())
				.addList(new ElectoralList("Lista 2").addPerson(p12).end()));

		schede.add(new EmptyBallot("Scheda 2", 0, null, 1).addList(new ElectoralList("Lista 0").addPerson(p20).end())
				.addList(new ElectoralList("Lista 1").addPerson(p21).end())
				.addList(new ElectoralList("Lista 2").addPerson(p22).end()));

		if (!well) {
			schede.add(new EmptyBallot("Scheda erronea", 0, null, 1)
					.addList(new ElectoralList("Lista 0").addPerson(p22).addPerson(p22)));
		}

		ArrayList<Person> voters = new ArrayList<>();

		db.setBallots(schede, voters);

		u.setProcedureBallots(db.getEmptyBallots(0));
	}

}