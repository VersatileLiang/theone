/* 
 * Copyright 2010 CSU/NetLab
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package routing;

import java.util.Vector;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import core.Connection;
import core.DTNHost;
import core.Message;
import core.Settings;
import core.SimClock;
import util.Tuple;

/**
 * Implementation of SimBet router as described in 
 * <I>Social Network Analysis for Routing in Disconnected Delay-Tolerant MANETs</I> by
 * Elizabeth Daly et al.
 */
public class SimBetRouter extends ActiveRouter {
	/** default weighted value for utility of similarity */
	public static final double DEFAULT_ALPHA = 0.5;
	/** default weighted value for utility of betweeness */
	public static final double DEFAULT_BETA = 0.5;

	
	/** SimBet router's setting namespace ({@value})*/ 
	public static final String SIMBET_NS = "SimBetRouter";
	
	public static final String ALPHA_S = "alpha";
	public static final String BETA_S = "beta";

	private double alpha;
	private double beta;

	/** delivery utilities */
//	private Map<DTNHost, Double> simBetUtils;
	private double betweeness = 0;
	private double similarity = 0;
	private double quancentra = 0;//定量度中心性
	private Map<DTNHost,Double>RationCentra;
	private Set<DTNHost>Encounter;
	private Map<DTNHost,Double>NumEncounter;
	/** last delivery utilities update (sim)time */
	
	/** host(s) that are met recently */
	private Vector encounterHost;
	/** host(s) that are met recently in the neibor, for temporal use */
	private Vector encounterHostInNeighbor;
	/** host(s) that are Indirect met in neighbor */
	private Vector indirectEncounterHost;
	
	private int [][] newAdjacencyMatrix;
	private int [][] oldAdjacencyMatrix;
	
	private int [][] newIndirectMatrix;
	private int [][] oldIndirectMatrix;
	
	private int dimension;
	private double lastAgeUpdate;
	private Set<DTNHost> Community;
	/**
	 * Constructor. Creates a new message router based on the settings in
	 * the given Settings object.
	 * @param s The settings object
	 */
	public SimBetRouter(Settings s) {
		super(s);
		Settings simBetSettings = new Settings(SIMBET_NS);
		
		if (simBetSettings.contains(ALPHA_S)) {
			beta = simBetSettings.getDouble(ALPHA_S);
		}            
		else {
			beta = DEFAULT_ALPHA;
		}
		if (simBetSettings.contains(BETA_S)) {
			beta = simBetSettings.getDouble(BETA_S);
		}
		else {
			beta = DEFAULT_BETA;
		}

		encounterHost = new Vector();
		encounterHostInNeighbor = new Vector();
		indirectEncounterHost = new Vector();
//		initUtils();
	}

	/**
	 * Copyconstructor.
	 * @param r The router prototype where setting values are copied from
	 */
	protected SimBetRouter(SimBetRouter r) {
		super(r);
		
		this.alpha = r.alpha;
		this.beta = r.beta;
		encounterHost = new Vector();
		encounterHostInNeighbor = new Vector();
		indirectEncounterHost = new Vector();
		RationCentra=new HashMap<DTNHost,Double>();
		Encounter=new HashSet<DTNHost>();
		NumEncounter=new HashMap<DTNHost,Double>();
		Community=new HashSet<DTNHost>();
//		initUtils();
	}
	
	/**
	 * Initializes utility hash
	 */
	
	@Override
	public void changedConnection(Connection con) {
		if (con.isUp()) {
			DTNHost otherHost = con.getOtherNode(getHost());
			updateEncounterHostList(otherHost);
			EncounterHostList(otherHost);
			updateEncounter(otherHost);
			getEncounterHostList(otherHost);
			updateRationCentra(otherHost);
			updateIndirectEncounterHostList(otherHost);
		}
	}
	
	/**;
	 * Updates encounter host list for a host.(Not including indirectEncounter)
	 * @param host The host we just met
	 */
	@SuppressWarnings("unchecked")
	private void updateEncounterHostList(DTNHost host) {
		boolean isNewEncounterHost = true;
			
		if (encounterHost.contains(host)) {
			isNewEncounterHost = false;
		}
		
		if (isNewEncounterHost == true) {
			encounterHost.add(host);
	
			//锟斤拷锟斤拷锟铰碉拷锟节接撅拷锟斤拷
			dimension = encounterHost.size() + 1;
			newAdjacencyMatrix = new int [dimension][dimension];
		
			//锟斤拷原锟斤拷锟斤拷锟节接撅拷锟斤拷锟斤拷锟诫到锟铰碉拷锟节接撅拷锟斤拷
			if (encounterHost.size() > 1) {
				for (int i=0; i<newAdjacencyMatrix.length - 1; i++) {
					for (int j=0; j<newAdjacencyMatrix.length - 1; j++) {
						newAdjacencyMatrix[i][j] = oldAdjacencyMatrix[i][j];
					}
				}
			}		
			//锟铰的节碉拷锟诫本锟斤拷锟斤拷锟斤拷锟节碉拷锟斤拷械谋冉希锟饺伙拷锟斤拷锟斤拷锟斤拷锟揭伙拷锟揭伙拷锟�
			for (int i=0; i<encounterHost.size(); i++) {
				if (encounterHostInNeighbor.contains(encounterHost.get(i))) {
					newAdjacencyMatrix[newAdjacencyMatrix.length-1][i+1] = 1;
					newAdjacencyMatrix[i+1][newAdjacencyMatrix.length-1] = 1;
				}
			}
			//
			newAdjacencyMatrix[0][newAdjacencyMatrix.length-1] = 1;
			newAdjacencyMatrix[newAdjacencyMatrix.length-1][0] = 1;
		
			oldAdjacencyMatrix = newAdjacencyMatrix;
		}
				
		return;
	}
	
	/**
	 * Gets encounter host list for a host.
	 * @param host The host we just met
	 */
	private void EncounterHostList(DTNHost host) {
		SimBetRouter othRouter = (SimBetRouter)host.getRouter();
		for (int i = 0; i < encounterHost.size(); i++) {	
			if (othRouter.encounterHost.contains(encounterHost.get(i))) {
				encounterHostInNeighbor.add(encounterHost.get(i));
			}
		}				
	}

	private double getEncounter(DTNHost host){
		if(this.NumEncounter.containsKey(host))
			return this.NumEncounter.get(host);
		else
			return 0;
	}
	
	private void updateEncounter(DTNHost host){
		double oldvalue=getEncounter(host);
		double newvalue=oldvalue+1;
		NumEncounter.put(host,newvalue);
		if(this.Encounter.contains(host))
			return;
		else
			Encounter.add(host);
	}
	
	private double getEncounterHostList(DTNHost host){
          if(this.RationCentra.containsKey(host))
        	  return this.RationCentra.get(host);
          else
        	  return 0;
	}
	
	private void agePriod() {
		// TODO Auto-generated method stub
		double secondsInTimeUnit = 30;
		double timeDiff = (SimClock.getTime() - this.lastAgeUpdate) / 
				secondsInTimeUnit;
			
			if (timeDiff == 0) {
				return;
			}
			this.lastAgeUpdate = SimClock.getTime();
	}

	public void updateRationCentra(DTNHost host){
        agePriod();
        double v1=getEncounter(host);
        double v2=Encounter.size();
        double value=(double)(v1/v2);
        System.out.println(value);
		RationCentra.put(host, value);
	}
	
	public double getRationCentra(DTNHost host){
		if(this.RationCentra.containsKey(host))
			return this.RationCentra.get(host);
		else
			return 0;
	}
	
	private double calculateLocalBetUtil() {
		double sum = 0.0;
		for (int i=0; i < newAdjacencyMatrix.length; i++) {
			for (int j=0; j < newAdjacencyMatrix.length; j++) {
				if ((i<j) && (newAdjacencyMatrix[i][j] == 0)) {
					for (int k = 0; k < newAdjacencyMatrix.length; k++) {
						sum = sum + newAdjacencyMatrix[i][k] * newAdjacencyMatrix[k][j];
					}	
					betweeness = betweeness + (1/sum);	
					sum = 0.0;				
				}
			}
	    }
	    return betweeness;
	}
	
	/**
	 * Updates encounter host list for a host.
	 * @param host The host we just met
	 */
	
	private void updateIndirectEncounterHostList(DTNHost host) {
		SimBetRouter othRouter = (SimBetRouter)host.getRouter();
		boolean isNewEncounterHost = true;
			
		//锟斤拷锟斤拷一锟斤拷锟斤拷锟节居节碉拷
		if (encounterHost.contains(host)) {
			int column = 0;
			int row = 0;
			isNewEncounterHost = false;
			for (int i=0; i < (othRouter.encounterHost.size()); i++) {
				if (encounterHost.contains(othRouter.encounterHost.get(i)))	{
					continue;
				} else {
					if (indirectEncounterHost.contains(othRouter.encounterHost.get(i))){
						row = encounterHost.indexOf(host);
						column = indirectEncounterHost.indexOf((othRouter.encounterHost.get(i)));
						oldIndirectMatrix[row][column] = 1;
					} else {
						row = encounterHost.size() + 1; //+1锟斤拷锟斤拷为要锟斤拷诘锟�
						column = indirectEncounterHost.size() + 1;//+1锟斤拷锟斤拷为要锟斤拷锟斤拷1锟斤拷
						newIndirectMatrix = new int [row][column];
						if (indirectEncounterHost.size() > 1) {
							for (int k = 0; k < (oldIndirectMatrix.length - 1); k++) {
								for (int j=0; j<(oldIndirectMatrix[k].length - 1); j++) {//锟角对筹拷
									newIndirectMatrix[k][j] = oldIndirectMatrix[k][j];
								}
							}
						}
						for (int s = 0; s < (row - 1); s++) {
							newIndirectMatrix[s][column - 1] = 0;
						}
						newIndirectMatrix[row - 1][column - 1] = 1;
						oldIndirectMatrix = newIndirectMatrix;					
					}
				}
			}		
		}
		
		//锟斤拷锟斤拷一锟斤拷锟斤拷锟节居节点。锟斤拷锟絛irect锟斤拷Indirect锟街匡拷写锟斤拷锟铰碉拷indirect锟斤拷锟斤拷要锟斤拷锟斤拷锟叫ｏ拷direct锟斤拷锟斤拷锟窖撅拷锟斤拷锟接癸拷锟斤拷
		if (isNewEncounterHost == true) {
			int column = 0;
			int row = 0;

			if (indirectEncounterHost.contains(host)) {
				row = encounterHost.size() + 1;
				column = indirectEncounterHost.size() - 1;
				newIndirectMatrix = new int[row][column];
				for (int i=0; i < encounterHost.size(); i++){
					for (int j=0; j < indirectEncounterHost.size(); j++){
						if (j < indirectEncounterHost.indexOf(host)){
							newIndirectMatrix[i][j] = oldIndirectMatrix[i][j];
						}
						if (j > indirectEncounterHost.indexOf(host)){
							newIndirectMatrix[i][j-1] = oldIndirectMatrix[i][j];
						}
					}					
				}
				
				oldIndirectMatrix = newIndirectMatrix;
				indirectEncounterHost.removeElement(host);
				
			}
						
			for (int i=0; i < othRouter.encounterHost.size(); i++) {
				if (encounterHost.contains(othRouter.encounterHost.get(i)))	{
					continue;
				} 

                                     else {
					if (indirectEncounterHost.contains(othRouter.encounterHost.get(i))){
						row = encounterHost.indexOf(host);
						column = indirectEncounterHost.indexOf((othRouter.encounterHost.get(i)));
						oldIndirectMatrix[row][column] = 1;
					} 
                                       else {
						row = encounterHost.size() + 1; //+1锟斤拷锟斤拷为要锟斤拷诘锟�
						column = indirectEncounterHost.size() + 1;//+1锟斤拷锟斤拷为要锟斤拷锟斤拷1锟斤拷
						newIndirectMatrix = new int [row][column];
						for (int k=0; k<(oldIndirectMatrix.length - 1); k++) {
							for (int j=0; j<(oldIndirectMatrix[k].length - 1); j++) {//锟角对筹拷
								newIndirectMatrix[k][j] = oldIndirectMatrix[k][j];
							}
						}
						for (int s = 0; s < (row - 1); s++) {
							newIndirectMatrix[s][column - 1] = 0;
						}
						newIndirectMatrix[row - 1][column - 1] = 1;
						oldIndirectMatrix = newIndirectMatrix;					
					}
				}
			}		
			
		}		
		
	}
	
	private double calculateLocalSimUtil(DTNHost host) {//锟斤拷锟斤拷host为锟斤拷息锟斤拷目锟侥节碉拷
		int row = 0;
		int column = 0;
		similarity = 0;
		if (encounterHost.contains(host)) {
			row = encounterHost.indexOf(host) + 1;
			for (int j=0; j < (encounterHost.size() + 1); j++) {
				 similarity += oldAdjacencyMatrix[0][j] * oldAdjacencyMatrix[row][j];
			}
			return similarity;
		}
		if (indirectEncounterHost.contains(host)) {
			column = indirectEncounterHost.indexOf(host);
			for (int i=0; i < (encounterHost.size() + 1); i++) {
				similarity += oldIndirectMatrix[i][column];
			}
			return similarity;
		}
		return similarity;
	}
	
	/**
	 * Returns the current SimBetUtil value for a host or 0 if entry for
	 * the host doesn't exist.
	 * @param host The host to look the P for
	 * @return the current P value
	 */		
	
	@Override
	public void update() {
		super.update();
		if (!canStartTransfer() ||isTransferring()) {
			return; // nothing to transfer or is currently transferring 
		}
	
		if (exchangeDeliverableMessages() != null) {
			return;
		}
		
		tryOtherMessages();		
	}
	
	/**
	 * Tries to send all other messages to all connected hosts ordered by
	 * their delivery probability
	 * @return The return value of {@link #tryMessagesForConnected(List)}
	 */
	private Tuple<Message, Connection> tryOtherMessages() {
		List<Tuple<Message, Connection>> messages = 
			new ArrayList<Tuple<Message, Connection>>(); 
	
		Collection<Message> msgCollection = getMessageCollection();
		
		/* for all connected hosts collect all messages that have a higher
		   probability of delivery by the other host */
		for (Connection con : getConnections()) {
			DTNHost other = con.getOtherNode(getHost());
			SimBetRouter othRouter = (SimBetRouter)other.getRouter();
			double simUtil = 0.0;
			double betUtil = 0.0;
			double simBetUtil = 0.0;
			double othSimUtil = 0.0;
			double othBetUtil = 0.0;
			double othSimBetUtil = 0.0;
			double simcosUtil=0.0;
			double betcosUtil=0.0;
			if (othRouter.isTransferring()) {
				continue; // skip hosts that are transferring
			}
			
			for (Message m : msgCollection) {
				if (othRouter.hasMessage(m.getId())) {
					continue; // skip messages that the other one has
				}
				
				simcosUtil=calculateLocalSimUtil(m.getTo()) / (Math.sqrt(calculateLocalSimUtil(m.getTo()))*Math.sqrt(othRouter.calculateLocalSimUtil(m.getTo())));
				
			//	simUtil = calculateLocalSimUtil(m.getTo()) / (calculateLocalSimUtil(m.getTo()) + othRouter.calculateLocalSimUtil(m.getTo()));
				betUtil = calculateLocalBetUtil() / (calculateLocalBetUtil() + othRouter.calculateLocalBetUtil());
				simBetUtil = alpha * simcosUtil + beta * betUtil;
			    betcosUtil=othRouter.calculateLocalSimUtil(m.getTo()) / (Math.sqrt(calculateLocalSimUtil(m.getTo()))*Math.sqrt(othRouter.calculateLocalSimUtil(m.getTo())));	
			//	othSimUtil = othRouter.calculateLocalSimUtil(m.getTo()) / (calculateLocalSimUtil(m.getTo()) + othRouter.calculateLocalSimUtil(m.getTo()));
				othBetUtil = othRouter.calculateLocalBetUtil() / (calculateLocalBetUtil() + othRouter.calculateLocalBetUtil());		
				othSimBetUtil = alpha * betcosUtil + beta * othBetUtil;
				if (othSimBetUtil > simBetUtil) {
					// the other node has higher probability of delivery
					messages.add(new Tuple<Message, Connection>(m,con));

				}
			}			
		}
		
		if (messages.size() == 0) {
			return null;
		}
		
		return tryMessagesForConnected(messages);	// try to send messages
	}
	
	@Override
	protected void transferDone(Connection con) {
		/* don't leave a copy for the sender */
		this.deleteMessage(con.getMessage().getId(), false);
	}
	@Override
	public MessageRouter replicate() {
		SimBetRouter r = new SimBetRouter(this);
		return r;
	}

}