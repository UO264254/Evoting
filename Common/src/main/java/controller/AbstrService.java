package controller;

import java.net.InetAddress;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;



/**
 * Service astratto, classe base da cui derivano tutti i differenti tipi di Service (uno per tipologia di terminale).
 */
public abstract class AbstrService extends Thread{
	protected TerminalController controller;
	protected Link link;
	protected InetAddress ip;
	
	private static final Logger logger = LogManager.getLogger(AbstrService.class);

	public AbstrService(TerminalController controller, Link link, String name){
		super(name);
		this.controller = controller;
		this.link = link;
		this.ip = link.getIp();
	}

	@Override
	public void run() {
		logger.debug("AbstrService run {} ", getName() );
		
		
		if(link.hasNextLine()){
		
			execute();
		}
		
		link.close();
	}
	
	/**
	 * Funzione in cui il service gestisce la richiesta da parte del mittente.
	 */
	protected abstract void execute();
}
