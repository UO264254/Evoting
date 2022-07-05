package postazione.controller;

import java.sql.ResultSet;
import java.sql.SQLException;

import db.ConnectionManager;
import db.DB;
import db.DBMS;
import exceptions.DBException;
import exceptions.PEException;

public class PostaDB extends DB {
	
	public PostaDB(String host, String port, String schema) throws PEException {
		dbms = new DBMS(host, port, schema, "Postazione");
	}

	public boolean esBadgeMail(String newBadge) {
		try {
			return findBadgeMail(newBadge);
		} catch (PEException e) {
			e.printStackTrace();
		}
		return false;
		
	}

	private boolean findBadgeMail(String newBadge) throws PEException {
		ConnectionManager cManager = dbms.getConnectionManager();

		final String query = "SELECT * " + "FROM Token " + "WHERE badge = ? ;";

		try {
			ResultSet rs = cManager.executeQuery(query, newBadge);
			boolean resultado = rs.next();
			System.out.println("existe= "+resultado);
			return resultado ;
		} catch(SQLException e) {
			throw DBException.DB_0(e);
		} finally {
			cManager.close();
		}

	}



}
