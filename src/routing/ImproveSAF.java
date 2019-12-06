package routing;

import java.util.*;
import core.Connection.*;

import core.*;
import util.Tuple;

/**
 * An implementation of Spray and Focus DTN routing as described in 
 * <em>Spray and Focus: Efficient Mobility-Assisted Routing for Heterogeneous
 * and Correlated Mobility</em> by Thrasyvoulos Spyropoulos et al.
 * 
 * @author PJ Dillon, University of Pittsburgh
 */
public class ImproveSAF extends ActiveRouter 
{
	/** SprayAndFocus router's settings name space ({@value})*/ 
	public static final String IMPROVESAF_NS = "ImproveSAF";
	/** identifier for the initial number of copies setting ({@value})*/ 
	public static final String NROF_COPIES_S = "nrofCopies";
	/** identifier for the difference in timer values needed to forward on a message copy */
	public static final String TIMER_THRESHOLD_S = "transitivityTimerThreshold";
	/** Message property key for the remaining available copies of a message */
	public static final String MSG_COUNT_PROP = "ImproveSAF.copies";
	/** Message property key for summary vector messages exchanged between direct peers */
	public static final String SUMMARY_XCHG_PROP = "ImproveSAF.protoXchg";
	public Map<DTNHost,Double>props;
	public Map<DTNHost,Double>Encouter;
	protected static final String SUMMARY_XCHG_IDPREFIX = "summary";
	protected static final double defaultTransitivityThreshold = 60.0;
	protected static int protocolMsgIdx = 0;
	public static double secondsInTimeUnit;
	protected int initialNrofCopies;
	protected double transitivityTimerThreshold;
	public static final String Second_InTime_Unit = "secondsInTimeUnit";
	/** Stores information about nodes with which this host has come in contact */
	protected Map<DTNHost, EncounterInfo> recentEncounters;
	protected Map<DTNHost, Map<DTNHost, EncounterInfo>> neighborEncounters;
	private double lastAgeUpdate;
	public static double encouter;
	public double beta=0.0;
	public ImproveSAF(Settings s)
	{
		super(s);
		Settings snf = new Settings(IMPROVESAF_NS);
		initialNrofCopies = snf.getInt(NROF_COPIES_S);
		
		if(snf.contains(TIMER_THRESHOLD_S))
			transitivityTimerThreshold = snf.getDouble(TIMER_THRESHOLD_S);
		else
			transitivityTimerThreshold = defaultTransitivityThreshold;
		if(snf.contains(Second_InTime_Unit))
			secondsInTimeUnit=snf.getDouble(routing.ImproveSAF.Second_InTime_Unit);
		else
			secondsInTimeUnit=30.0;
		
		Encouter=new HashMap<DTNHost,Double>();
		props=new HashMap<DTNHost,Double>();
		recentEncounters = new HashMap<DTNHost, EncounterInfo>();
		neighborEncounters = new HashMap<DTNHost, Map<DTNHost, EncounterInfo>>();
	}
	
	/**
	 * Copy Constructor.
	 * 
	 * @param r The router from which settings should be copied
	 */
	public ImproveSAF(ImproveSAF r)
	{
		super(r);
		this.initialNrofCopies = r.initialNrofCopies;
		this.secondsInTimeUnit=r.secondsInTimeUnit;
		Encouter=new HashMap<DTNHost,Double>();
		props=new HashMap<DTNHost,Double>();
		recentEncounters = new HashMap<DTNHost, EncounterInfo>();
		neighborEncounters = new HashMap<DTNHost, Map<DTNHost, EncounterInfo>>();
	}
	
	@Override
	public MessageRouter replicate() 
	{
		return new ImproveSAF(this);
	}

	/**
	 * Called whenever a connection goes up or comes down.
	 * @param otherHost 
	 * @param otherHost 
	 * @param DTNHost 
	 * @param DTNHost 
	 */
	@Override
	public void changedConnection(Connection con)
	{
		super.changedConnection(con);
		
		/*
		 * The paper for this router describes Message summary vectors 
		 * (from the original Epidemic paper), which
		 * are exchanged between hosts when a connection is established. This
		 * functionality is already handled by the simulator in the protocol
		 * implemented in startTransfer() and receiveMessage().
		 * 
		 * Below we need to implement sending the corresponding message.
		 */
		DTNHost thisHost = getHost();
		DTNHost peer = con.getOtherNode(thisHost);
		
		//do this when con is up and goes down (might have been up for awhile)
		if(recentEncounters.containsKey(peer))
		{ 
			EncounterInfo info = recentEncounters.get(peer);
			info.updateEncounterTime(SimClock.getTime());
		}
		else
		{
			recentEncounters.put(peer, new EncounterInfo(SimClock.getTime()));
		}
		if(con.isUp()){
			updateDeliveryPredFor(peer);
			updateTransitivePreds(peer);
		}
		
		if(!con.isUp())
		{
			neighborEncounters.remove(peer);
			return;
		}
		/*
		 * For this simulator, we just need a way to give the other node in this connection
		 * access to the peers we recently encountered; so we duplicate the recentEncounters
		 * Map and attach it to a message.
		 */
		int msgSize = recentEncounters.size() * 64 + getMessageCollection().size() * 8;
		Message newMsg = new Message(thisHost, peer, SUMMARY_XCHG_IDPREFIX + protocolMsgIdx++, msgSize);
		newMsg.addProperty(SUMMARY_XCHG_PROP, /*new HashMap<DTNHost, EncounterInfo>(*/recentEncounters);
		
		createNewMessage(newMsg);
	}

	@Override
	public boolean createNewMessage(Message m)
	{
		makeRoomForNewMessage(m.getSize());

		m.addProperty(MSG_COUNT_PROP, new Integer(initialNrofCopies));
		addToMessages(m, true);
		return true;
	}

	
    public void updateEncouter(DTNHost host){
    	double oldvalue=getEncouter(host);
    	double newvalue=oldvalue+1;
    	Encouter.put(host, newvalue);
    	
    }
	private double getEncouter(DTNHost host) {
		if(this.Encouter.containsKey(host))
		    return Encouter.get(host);
		else
			return 0.0;
	}

	private void updateDeliveryPredFor(DTNHost host) {
		MessageRouter otherRouter = host.getRouter();
		assert otherRouter instanceof ImproveSAF : "PRoPHET only works " + 
			" with other routers of same type";
		double pForHost = getPredFor(host); // P(a,b)
		Map<DTNHost, Double> othersEncouter = 
			((ImproveSAF)otherRouter).getEncouter_2();
		
		for (Map.Entry<DTNHost, Double> e : othersEncouter.entrySet()) {
			if (e.getKey() == getHost()) {
				continue; // don't add yourself
			}
			System.out.println(e.getValue());
		}
		
		double oldValue = getPredFor(host);
		double newValue = oldValue + (1 - oldValue) ;
		props.put(host, newValue);
	}
	
	/**
	 * Returns the current prediction (P) value for a host or 0 if entry for
	 * the host doesn't exist.
	 * @param host The host to look the P for
	 * @return the current P value
	 */
	
	public double getPredFor(DTNHost host) {
		ageDeliveryPreds(); // make sure preds are updated before getting
		if (props.containsKey(host)) {
			return props.get(host);
		}
		else {
			return 0;
		}
	}
	
	/**
	 * Updates transitive (A->B->C) delivery predictions.
	 * <CODE>P(a,c) = P(a,c)_old + (1 - P(a,c)_old) * P(a,b) * P(b,c) * BETA
	 * </CODE>
	 * @param host The B host who we just met
	 */
	private void updateTransitivePreds(DTNHost host) {
		MessageRouter otherRouter = host.getRouter();
		assert otherRouter instanceof ImproveSAF : "PRoPHET only works " + 
			" with other routers of same type";
		double pForHost = getPredFor(host); // P(a,b)
		Map<DTNHost, Double> othersPreds = 
			((ImproveSAF)otherRouter).getDeliveryPreds();
		
		for (Map.Entry<DTNHost, Double> e : othersPreds.entrySet()) {
			if (e.getKey() == getHost()) {
				continue; // don't add yourself
			}
			double pOld = getPredFor(e.getKey()); // P(a,c)_old
			double pNew = pOld + ( 1 - pOld) * pForHost * e.getValue();
			props.put(e.getKey(), pNew);
		}
	}

	/**
	 * Ages all entries in the delivery predictions.
	 * <CODE>P(a,b) = P(a,b)_old * (GAMMA ^ k)</CODE>, where k is number of
	 * time units that have elapsed since the last time the metric was aged.
	 * @see #SECONDS_IN_UNIT_S
	 */
	private void ageDeliveryPreds() {
		double timeDiff = (SimClock.getTime() - this.lastAgeUpdate) / 
			secondsInTimeUnit;
		
		if (timeDiff == 0) {
			return;
		}
		for (Map.Entry<DTNHost, Double> e : props.entrySet()) {
			e.setValue(e.getValue());
		}
		
		this.lastAgeUpdate = SimClock.getTime();
	}
	
	/**
	 * Returns a map of this router's delivery predictions
	 * @return a map of this router's delivery predictions
	 */
	private Map<DTNHost, Double> getDeliveryPreds() {
		ageDeliveryPreds(); // make sure the aging is done
		return this.props;
	}
	public Map<DTNHost,Double> getEncouter_2(){
		return this.Encouter;
	}
	@Override
	public Message messageTransferred(String id, DTNHost from)
	{
		Message m = super.messageTransferred(id, from);
		
		/*
		 * Here we update our last encounter times based on the information sent
		 * from our peer. 
		 */
		Map<DTNHost, EncounterInfo> peerEncounters = (Map<DTNHost, EncounterInfo>)m.getProperty(SUMMARY_XCHG_PROP);
		if(isDeliveredMessage(m) && peerEncounters != null)
		{
			double distTo = getHost().getLocation().distance(from.getLocation());
			double speed = from.getPath() == null ? 0 : from.getPath().getSpeed();
			
			if(speed == 0.0) return m;
			
			double timediff = distTo/speed;
			
			/*
			 * We save the peer info for the utility based forwarding decisions, which are
			 * implemented in update()
			 */
			neighborEncounters.put(from, peerEncounters); 
			
			for(Map.Entry<DTNHost, EncounterInfo> entry : peerEncounters.entrySet())
			{
				DTNHost h = entry.getKey();
				if(h == getHost()) continue;
				
				EncounterInfo peerEncounter = entry.getValue();
				EncounterInfo info = recentEncounters.get(h);
				
				/*
				 * We set our timestamp for some node, h, with whom our peer has come in contact
				 * if our peer has a newer timestamp beyond some threshold.
				 * 
				 * The paper describes timers that count up from the time of contact. We use
				 * fixed timestamps here to accomplish the same effect, but the computations
				 * here are consequently a little different from the paper. 
				 */
				if(!recentEncounters.containsKey(h))
				{
					info = new EncounterInfo(peerEncounter.getLastSeenTime() - timediff);
					recentEncounters.put(h, info);
					continue;
				}
				
				
				if(info.getLastSeenTime() + timediff < peerEncounter.getLastSeenTime())
				{
					recentEncounters.get(h).updateEncounterTime(peerEncounter.getLastSeenTime() - 
							timediff);
				}
			}
			return m;
		}
		
		//Normal message beyond here
		
		Integer nrofCopies = (Integer)m.getProperty(MSG_COUNT_PROP);
		
		nrofCopies = (int)Math.ceil(nrofCopies/2.0);
		
		m.updateProperty(MSG_COUNT_PROP, nrofCopies);
		
		return m;
	}

	@Override
	protected void transferDone(Connection con) 
	{
		Integer nrofCopies;
		String msgId = con.getMessage().getId();
		/* get this router's copy of the message */
		Message msg = getMessage(msgId);

		if (msg == null) { // message has been dropped from the buffer after..
			return; // ..start of transfer -> no need to reduce amount of copies
		}
		
		if(msg.getProperty(SUMMARY_XCHG_PROP) != null)
		{
			deleteMessage(msgId, false);
			return;
		}
		
		/* 
		 * reduce the amount of copies left. If the number of copies was at 1 and
		 * we apparently just transferred the msg (focus phase), then we should
		 * delete it. 
		 */
		nrofCopies = (Integer)msg.getProperty(MSG_COUNT_PROP);
		if(nrofCopies > 1)
			nrofCopies /= 2;
		else
			deleteMessage(msgId, false);
		
		msg.updateProperty(MSG_COUNT_PROP, nrofCopies);
	}
	
	

	@Override
	public void update()
	{
		super.update();
		if (!canStartTransfer() || isTransferring()) {
			return; // nothing to transfer or is currently transferring 
		}

		/* try messages that could be delivered to final recipient */
		if (exchangeDeliverableMessages() != null) {
			return;
		}
		
		List<Message> spraylist = new ArrayList<Message>();
		List<Tuple<Message,Connection>> focuslist = new LinkedList<Tuple<Message,Connection>>();

		for (Message m : getMessageCollection())
		{
			if(m.getProperty(SUMMARY_XCHG_PROP) != null) continue;
			
			Integer nrofCopies = (Integer)m.getProperty(MSG_COUNT_PROP);
			assert nrofCopies != null : "SnF message " + m + " didn't have " + 
				"nrof copies property!";
			if (nrofCopies > 1)
			{
				spraylist.add(m);
			}
			else
			{
				/*
				 * Here we implement the single copy utility-based forwarding scheme.
				 * The utility function is the last encounter time of the msg's 
				 * destination node. If our peer has a newer time (beyond the threshold),
				 * we forward the msg on to it. 
				 */
				DTNHost dest = m.getTo();
				Connection toSend = null;
				double maxPeerLastSeen = 0.0; //beginning of time (simulation time)
				
				//Get the timestamp of the last time this Host saw the destination
				double thisLastSeen = getLastEncounterTimeForHost(dest);
				
				for(Connection c : getConnections())
				{
					DTNHost peer = c.getOtherNode(getHost());
					Map<DTNHost, EncounterInfo> peerEncounters = neighborEncounters.get(peer);
					double peerLastSeen = 0.0;
					
					if(peerEncounters != null && peerEncounters.containsKey(dest))
						peerLastSeen = neighborEncounters.get(peer).get(dest).getLastSeenTime();
					
					/*
					 * We need to pick only one peer to send the copy on to; so lets find the
					 * one with the newest encounter time.
					 */
					
						if(peerLastSeen > maxPeerLastSeen)
						{
							toSend = c;
							maxPeerLastSeen = peerLastSeen;
						}
							
				}
				if (toSend != null && maxPeerLastSeen > thisLastSeen + transitivityTimerThreshold)
				{
					focuslist.add(new Tuple<Message, Connection>(m, toSend));
				}
			}
		}
		
		//arbitrarily favor spraying
		if(tryMessagesToConnections(spraylist, getConnections()) == null)
		{
			if(tryMessagesForConnected(focuslist) != null)
			{
				
			}
		}
	}

	protected double getLastEncounterTimeForHost(DTNHost host)
	{
		if(recentEncounters.containsKey(host))
			return recentEncounters.get(host).getLastSeenTime();
		else
			return 0.0;
	}
	
	/**
	 * Stores all necessary info about encounters made by this host to some other host.
	 * At the moment, all that's needed is the timestamp of the last time these two hosts
	 * met.
	 * 
	 * @author PJ Dillon, University of Pittsburgh
	 */
	protected class EncounterInfo
	{
		protected double seenAtTime;
		
		public EncounterInfo(double atTime)
		{
			this.seenAtTime = atTime;
		}
		
		public void updateEncounterTime(double atTime)
		{
			this.seenAtTime = atTime;
		}
		
		public double getLastSeenTime()
		{
			return seenAtTime;
		}
		
		public void updateLastSeenTime(double atTime)
		{
			this.seenAtTime = atTime;
		}
	}
}