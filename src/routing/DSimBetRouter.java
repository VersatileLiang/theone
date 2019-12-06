/* 
 * Copyright 2010 CSU/NetLab
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package routing;

import java.util.Vector;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import core.Connection;
import core.DTNHost;
import core.Message;
import core.Settings;
import core.SimClock;
import routing.util.RoutingInfo;
//import core.Tuple;
import util.Tuple;

/**
 * Implementation of SimBet router as described in 
 * <I>Social Network Analysis for Routing in Disconnected Delay-Tolerant MANETs</I> by
 * Elizabeth Daly et al.
 */
public class DSimBetRouter extends ActiveRouter {
	/** default weighted value for utility of similarity */
	public static final double DEFAULT_ALPHA = 0.5;
	/** default weighted value for utility of betweeness */
	public static final double DEFAULT_BETA = 0.5;
	private Map<DTNHost, Set<String>> sentMessages;
	public double initialNrofCopies;
    public Map<DTNHost,Double>Priority;
    public static final String NROF_COPIES = "nrofCopies";
	public Map<DTNHost, Double>Ttdt;
	public static String UtilityEARouter_NS="UtilityEARouter";
	public static final String MSG_COUNT_PROPERTY = UtilityEARouter_NS + "." +
			"copies";
	
	/** SimBet router's setting namespace ({@value})*/ 
	public static final String SIMBET_NS = "DSimBetRouter";
	
	public static final String ALPHA_S = "alpha";
	public static final String BETA_S = "beta";

	private double alpha;
	private double beta;

	/** delivery utilities */
//	private Map<DTNHost, Double> simBetUtils;
	private double betweeness = 0;
	private double similarity = 0;
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
	private Map<DTNHost,Double>Encounter;
	private int dimension;
	private double lastAgeUpdate;
		
	/**
	 * Constructor. Creates a new message router based on the settings in
	 * the given Settings object.
	 * @param s The settings object
	 */
	public DSimBetRouter(Settings s) {
		super(s);
		Settings simBetSettings = new Settings(SIMBET_NS);
		initialNrofCopies = simBetSettings.getDouble(NROF_COPIES);
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
	protected DSimBetRouter(DSimBetRouter r) {
		super(r);
		
		this.alpha = r.alpha;
		this.beta = r.beta;
		encounterHost = new Vector();
		encounterHostInNeighbor = new Vector();
		indirectEncounterHost = new Vector();
		Encounter=new HashMap<DTNHost,Double>();
		this.sentMessages=new HashMap<DTNHost, Set<String>>();
	}
	
	/**
	 * Initializes utility hash
	 */

	@Override
	public void changedConnection(Connection con) {
		if (con.isUp()) {
			DTNHost otherHost = con.getOtherNode(getHost());
			updateEncounterHostList(otherHost);
			updateEncounter(otherHost);
			updateTransEncounter(otherHost);
			getEncounterHostList(otherHost);
			updateIndirectEncounterHostList(otherHost);
			
		}
	}
	
	private double updateTransEncounter(DTNHost host) {
		// TODO  MessageRouter otherRouter = host.getRouter();
		MessageRouter otherRouter=host.getRouter();
	  //  double pForHost = getEncounter(host); //P(b,a)
	    Map<DTNHost, Double> othersPreds = ((DSimBetRouter)otherRouter).getTransEncounter();
	    double value=0;
	    for (Map.Entry<DTNHost, Double> e : othersPreds.entrySet()) {
	        if (e.getKey() == getHost()) { //此时getHost()是指节点a
	            continue; 
	        }
	        value += getEncounter(e.getKey());
//          double pOld = getEncounter(e.getKey()); //P(a,c)_old, 实为节点a的getPredFor，相当于this.getPredFor
//	        double pNew = pOld + ( 1 - pOld) * e.getValue(); //e.getValue为P(b, c)
//	        Encounter.put(e.getKey(), pNew);
	    }
	    return value;
	} 

	private Map<DTNHost, Double> getTransEncounter() {
		ageDeliveryEncounter(); // make sure the aging is done
		return this.Encounter;
	}
	
	private void updateEncounter(DTNHost host){
		double oldvalue=getEncounter(host);
		double newvalue=oldvalue+1;
		Encounter.put(host, newvalue);
		
	}
	
	private double getEncounter(DTNHost host){
		if(this.Encounter.containsKey(host))
			return this.Encounter.get(host);
		else
			return 0;
	}
	
	/**
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
	
			//
			dimension = encounterHost.size() + 1;
			newAdjacencyMatrix = new int [dimension][dimension];
		
			//
			if (encounterHost.size() > 1) {
				for (int i=0; i<newAdjacencyMatrix.length - 1; i++) {
					for (int j=0; j<newAdjacencyMatrix.length - 1; j++) {
						newAdjacencyMatrix[i][j] = oldAdjacencyMatrix[i][j];
					}
				}
			}
			
			//
			for (int i=0; i<encounterHost.size(); i++) {
				if (encounterHostInNeighbor.contains(encounterHost.get(i))) {
					newAdjacencyMatrix[newAdjacencyMatrix.length-1][i+1] = 1;
					newAdjacencyMatrix[i+1][newAdjacencyMatrix.length-1] = 1;
				}
			}
			//???? 
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
	private void getEncounterHostList(DTNHost host) {
		DSimBetRouter othRouter = (DSimBetRouter)host.getRouter();
		for (int i = 0; i < encounterHost.size(); i++) {	
			if (othRouter.encounterHost.contains(encounterHost.get(i))) {
				encounterHostInNeighbor.add(encounterHost.get(i));
			}
		}				
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
		DSimBetRouter othRouter = (DSimBetRouter)host.getRouter();
		boolean isNewEncounterHost = true;	
		//
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
						row = encounterHost.size() + 1; //+1??????????
						column = indirectEncounterHost.size() + 1;//+1??????????1??
						newIndirectMatrix = new int [row][column];
						if (indirectEncounterHost.size() > 1) {
							for (int k = 0; k < (oldIndirectMatrix.length - 1); k++) {
								for (int j=0; j<(oldIndirectMatrix[k].length - 1); j++) {//????
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
		
		//
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
						row = encounterHost.size() + 1; //
						column = indirectEncounterHost.size() + 1;//
						newIndirectMatrix = new int [row][column];
						for (int k=0; k<(oldIndirectMatrix.length - 1); k++) {
							for (int j=0; j<(oldIndirectMatrix[k].length - 1); j++) {//
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
	
	private double calculateLocalSimUtil(DTNHost host) {//
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
	public boolean createNewMessage(Message msg) {
		makeRoomForNewMessage(msg.getSize());
		msg.setTtl(this.msgTtl);
		
		msg.addProperty(MSG_COUNT_PROPERTY, new Double(initialNrofCopies));
		addToMessages(msg, true);
		return true;
	}
	
	@Override
	protected void transferDone(Connection con) {
		Message m = con.getMessage();
		String id = m.getId();
		DTNHost recipient = con.getOtherNode(getHost());
		Set<String> sentMsgIds = this.sentMessages.get(recipient);
		/* update the map of where each message is already sent */
		if (sentMsgIds == null) {
			sentMsgIds = new HashSet<String>();
			this.sentMessages.put(recipient, sentMsgIds);
		}		
		sentMsgIds.add(id);
		
		double nrofCopies;
		String msgId = con.getMessage().getId();
		/* get this router's copy of the message */
		Message msg = getMessage(msgId);
      
		if (msg == null) { // message has been dropped from the buffer after..
			return; // ..start of transfer -> no need to reduce amount of copies
		}		
		/* reduce the amount of copies left */
		nrofCopies = (double)msg.getProperty(MSG_COUNT_PROPERTY);
		if (true) { 
			nrofCopies /= 2;	
		}
		msg.updateProperty(MSG_COUNT_PROPERTY, nrofCopies);
	} 
		   
	@Override
	public Message messageTransferred(String id, DTNHost from) {
		Message msg = super.messageTransferred(id, from);
		Double nrofCopies = (double)msg.getProperty(MSG_COUNT_PROPERTY);
		assert nrofCopies != null : "Not a UtilityEA message: " + msg;
		if (true) {
			/* in binary UEAR the receiving node gets ceil(n/2) copies */
			nrofCopies = (double)Math.ceil(nrofCopies/2);			
		}
		msg.updateProperty(MSG_COUNT_PROPERTY, nrofCopies);
		return msg;
	}
	
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
	    double nrofcopies;
		Collection<Message> msgCollection = getMessageCollection();
		
		/* for all connected hosts collect all messages that have a higher
		   probability of delivery by the other host */
		for (Connection con : getConnections()) {
			DTNHost other = con.getOtherNode(getHost());
			DSimBetRouter othRouter = (DSimBetRouter)other.getRouter();
			double newsimUtil=0.0;
			double oldsimUtil=0.0;
			double simUtil = 0.0;
			double betUtil = 0.0;
			double simBetUtil = 0.0;
			double othSimUtil = 0.0;
			double othBetUtil = 0.0;
			double othUtil=0.0;
			double othnewUtil=0.0;
			double othSimBetUtil = 0.0;
			
			if (othRouter.isTransferring()) {
				continue; // skip hosts that are transferring
			}
			
			for (Message m : msgCollection) {
				if (othRouter.hasMessage(m.getId())) {
					continue; // skip messages that the other one has
				}
				newsimUtil = (updateTransEncounter(m.getTo())+updateTransEncounter(getHost()))/(updateTransEncounter(m.getTo())*updateTransEncounter(getHost()));
				oldsimUtil = (updateTransEncounter(m.getTo())+updateTransEncounter(other))/(updateTransEncounter(m.getTo())*updateTransEncounter(other));
				
				simUtil = newsimUtil/(newsimUtil + oldsimUtil);
				betUtil = calculateLocalBetUtil() / (calculateLocalBetUtil() + othRouter.calculateLocalBetUtil());
				simBetUtil = alpha * simUtil + beta * betUtil;
				
				othSimUtil = (othRouter.updateTransEncounter(m.getTo())+othRouter.updateTransEncounter(getHost()))/(othRouter.updateTransEncounter(m.getTo())*othRouter.updateTransEncounter(getHost()));
				othBetUtil = (othRouter.updateTransEncounter(m.getTo())+othRouter.updateTransEncounter(other))/(othRouter.updateTransEncounter(m.getTo())*othRouter.updateTransEncounter(other));	
				othUtil= othSimUtil/(othSimUtil+othBetUtil);
				othnewUtil = othRouter.calculateLocalBetUtil() / (calculateLocalBetUtil() + othRouter.calculateLocalBetUtil());		
				othSimBetUtil = alpha * othUtil + beta * othnewUtil;						
							
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
	
	private void ageDeliveryEncounter() {
		double secondsInTimeUnit=50;
		double timeDiff = (SimClock.getTime() - this.lastAgeUpdate) / 
			secondsInTimeUnit;
		
		if (timeDiff == 0) {
			return;
		}
		
		this.lastAgeUpdate = SimClock.getTime();
	}
	
	@Override
	public RoutingInfo getRoutingInfo() {
		ageDeliveryEncounter();
		RoutingInfo top = super.getRoutingInfo();
		RoutingInfo ri = new RoutingInfo(Encounter.size() + 
				" delivery prediction(s)");
		
		for (Map.Entry<DTNHost, Double> e : Encounter.entrySet()) {
			DTNHost host = e.getKey();
			Double value = e.getValue();
			
			ri.addMoreInfo(new RoutingInfo(String.format("%s : %.6f", 
					host, value)));
		}
		
		top.addMoreInfo(ri);
		return top;
	}
	
	@Override
	public MessageRouter replicate() {
		DSimBetRouter r = new DSimBetRouter(this);
		return r;
	}

}