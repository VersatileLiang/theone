/* 
 * Copyright 2010 Aalto University, ComNet


 * Released under GPLv3. See LICENSE.txt for details. 
 */
package routing;

import java.util.ArrayList;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import core.Connection;
import core.DTNHost;
import core.Message;
import core.ModuleCommunicationBus;
import core.ModuleCommunicationListener;
import core.NetworkInterface;
import core.Settings;
import core.SettingsError;
import core.SimClock;
import core.SimScenario;
import routing.*;
import routing.util.RoutingInfo;


public class ASWRouter extends ActiveRouter implements ModuleCommunicationListener {
	/** identifier for the initial number of copies setting ({@value})*/ 
	public static final String NROF_COPIES = "nrofCopies";
	/** identifier for the binary-mode setting ({@value})*/ 
	/** SprayAndWait router's settings name space ({@value})*/ 
	public static final String SPRAYANDPROPHET_NS = "ASWRouter";
	/** Message property key */
	public static final String MSG_COUNT_PROPERTY = SPRAYANDPROPHET_NS + "." +
		"copies";

	private Map<DTNHost, Double> encounterduration;
	private Map<DTNHost, Double> encountercount;
	private Map<DTNHost, Double> recentencountertime;
	private Map<DTNHost, Double> intermeetingtime;
	private Map<DTNHost, Double> newvalue;
	private Map<DTNHost, Double> utility;
	private Map<DTNHost, Map<DTNHost, Double>> neighbvalue;
	private Map<DTNHost, Double> oldvalue;
	
	protected int initialNrofCopies;
    private Set<String> ackedMessageIds;
    
    public double contactfintime=0;
	public double contactstarttime=0;

	/*--------------energy configuration-------------------*/
	public static final String INIT_ENERGY_S = "intialEnergy";
	/** Energy usage per scanning -setting id ({@value}). */
	public static final String SCAN_ENERGY_S = "scanEnergy";
	/** Energy usage per second when sending -setting id ({@value}). */
	public static final String TRANSMIT_ENERGY_S = "transmitEnergy";
	/** Energy update warmup period -setting id ({@value}). Defines the 
	 * simulation time after which the energy level starts to decrease due to 
	 * scanning, transmissions, etc. Default value = 0. If value of "-1" is 
	 * defined, uses the value from the report warmup setting 
	 * {@link report.Report#WARMUP_S} from the namespace 
	 * {@value report.Report#REPORT_NS}. */
	public static final String WARMUP_S = "energyWarmup";

	/** {@link ModuleCommunicationBus} identifier for the "current amount of 
	 * energy left" variable. Value type: double */
	public static final String ENERGY_VALUE_ID = "Energy.value";
	
	private final double[] initEnergy;
	private double warmupTime;
	private double currentEnergy;
	/** energy usage per scan */
	private double scanEnergy;
	private double transmitEnergy;
	private double lastScanUpdate;
	private double lastUpdate;
	private double scanInterval;	
	private ModuleCommunicationBus comBus;
	private static Random rng = null;

	/*----------------------------------*/
	

	public ASWRouter(Settings s) {
		super(s);
		Settings snpSettings = new Settings(SPRAYANDPROPHET_NS);

		
		initialNrofCopies = snpSettings.getInt(NROF_COPIES);
		/*------------------Energy Configuration----------------------*/
        this.initEnergy = s.getCsvDoubles(INIT_ENERGY_S);
		
		if (this.initEnergy.length != 1 && this.initEnergy.length != 2) {
			throw new SettingsError(INIT_ENERGY_S + " setting must have " + 
					"either a single value or two comma separated values");
		}
		
		this.scanEnergy = s.getDouble(SCAN_ENERGY_S);
		this.transmitEnergy = s.getDouble(TRANSMIT_ENERGY_S);
		this.scanInterval  = s.getDouble(SimScenario.SCAN_INTERVAL_S);
		
		if (s.contains(WARMUP_S)) {
			this.warmupTime = s.getInt(WARMUP_S);
			if (this.warmupTime == -1) {
				this.warmupTime = new Settings(report.Report.REPORT_NS).
					getInt(report.Report.WARMUP_S);
			}
		}
		else {
			this.warmupTime = 0;
		}
		/*-----------------------------------------------------------*/
	}
	
	/**
	 * Copy constructor.
	 * @param r The router prototype where setting values are copied from
	 */
	protected ASWRouter(ASWRouter r) {
		super(r);
		this.initialNrofCopies = r.initialNrofCopies;
	
		this.ackedMessageIds = new HashSet<String>();
		initPreds();
		/*----------Energy configuration-------------*/
		this.initEnergy = r.initEnergy;
		setEnergy(this.initEnergy);
		this.scanEnergy = r.scanEnergy;
		this.transmitEnergy = r.transmitEnergy;
		this.scanInterval = r.scanInterval;
		this.warmupTime  = r.warmupTime;
		this.comBus = null;
		this.lastScanUpdate = 0;
		this.lastUpdate = 0;
		/*--------------------------------------------*/
	}
	
	
	protected void setEnergy(double range[]) {
		if (range.length == 1) {
			this.currentEnergy = range[0];
		}
		else {
			if (rng == null) {
				rng = new Random((int)(range[0] + range[1]));
			}
			this.currentEnergy = range[0] + 
				rng.nextDouble() * (range[1] - range[0]);
		}
	}
	
	protected int checkReceiving(Message m, DTNHost from) {
		if (this.currentEnergy < 0) {
			return DENIED_UNSPECIFIED;
		}
		else {
			 return super.checkReceiving(m,from);
		}
	}
	
	protected void reduceEnergy(double amount) {
		if (SimClock.getTime() < this.warmupTime) {
			return;
		}
		
		comBus.updateDouble(ENERGY_VALUE_ID, -amount);
		if (this.currentEnergy < 0) {
			comBus.updateProperty(ENERGY_VALUE_ID, 0.0);
		}
	}
	
	protected void reduceSendingAndScanningEnergy() {
		double simTime = SimClock.getTime();
		
		if (this.comBus == null) {
			this.comBus = getHost().getComBus();
			this.comBus.addProperty(ENERGY_VALUE_ID, this.currentEnergy);
			this.comBus.subscribe(ENERGY_VALUE_ID, this);
		}
		
		if (this.currentEnergy <= 0) {
			/* turn radio off */
			this.comBus.updateProperty(NetworkInterface.RANGE_ID, 0.0);
			return; /* no more energy to start new transfers */
		}
		
		if (simTime > this.lastUpdate && sendingConnections.size() > 0) {
			/* sending data */
			reduceEnergy((simTime - this.lastUpdate) * this.transmitEnergy);
		}
		this.lastUpdate = simTime;
		
		if (simTime > this.lastScanUpdate + this.scanInterval) {
			/* scanning at this update round */
			reduceEnergy(this.scanEnergy);
			this.lastScanUpdate = simTime;
		}
	}
	
	private void initPreds() {
		this.encounterduration = new HashMap<DTNHost, Double>();
		this.encountercount = new HashMap<DTNHost, Double>();
		this.recentencountertime =new HashMap<DTNHost, Double>();
		this.intermeetingtime = new HashMap<DTNHost, Double>();
		this.newvalue= new HashMap<DTNHost, Double>();
		this.neighbvalue = new HashMap<DTNHost, Map<DTNHost, Double>>();
		this.utility=new HashMap<DTNHost, Double>();
		this.oldvalue=new HashMap<DTNHost, Double>();
	}
	
	@Override
	public int receiveMessage(Message m, DTNHost from) {
		return super.receiveMessage(m, from);
	}
	
	public void changedConnection(Connection con) {
		if (con.isUp()) {
			
			/*the connection up time*/
			contactstarttime = SimClock.getTime();	
			
			DTNHost otherHost = con.getOtherNode(getHost());
			DTNHost thisHost = this.getHost();
			
			//System.out.println(speed+" "+otherHost.name);
			updateencountercount(otherHost);

			double s= contactstarttime-getrecenterencountertime(otherHost);
			updateintermeetingtime(otherHost, s);
			updaterecentencountertime(otherHost, contactstarttime);
			updatenewvalue(otherHost);
			
			MessageRouter mRouter = otherHost.getRouter();
			ASWRouter otherRouter = (ASWRouter)mRouter;
			
			Map<DTNHost,Double> othermap=new HashMap<DTNHost, Double>();
			Map<DTNHost, Double> thismap=new HashMap<DTNHost, Double>();
			
			for (Map.Entry<DTNHost, Double> e : otherRouter.newvalue.entrySet()) {
				othermap.put(e.getKey(), e.getValue().doubleValue());
			}
			
			for (Map.Entry<DTNHost, Double> e : this.newvalue.entrySet()) {
				thismap.put(e.getKey(), e.getValue().doubleValue());
			}
			
			neighbvalue.put(otherHost, othermap);
			otherRouter.neighbvalue.put(thisHost, thismap);	
			
			updateutility(otherHost);

			this.ackedMessageIds.addAll(otherRouter.ackedMessageIds);
			otherRouter.ackedMessageIds.addAll(this.ackedMessageIds);
			
			deleteAckedMessages();
			otherRouter.deleteAckedMessages();
			
		}
		
		else if (!con.isUp())
		{
			/*the disconnect time*/
			contactfintime= SimClock.getTime();
			DTNHost peer = con.getOtherNode(getHost());
			/*calculate the contact duration*/
		    double c = saveconduration(contactstarttime, contactfintime);
		    /*update the contact duration with this peer*/
		    updateencounterduration(peer,c);
		}
		
	}
	
	
	
	private void deleteAckedMessages() 
	{
		for (String id : this.ackedMessageIds) 
		{
			if (this.hasMessage(id) && !isSending(id)) 
			{
				this.deleteMessage(id, false);
			}
		}
	}
	
	
	public Message messageTransferred(String id, DTNHost from) {
		Message msg = super.messageTransferred(id, from);

		Integer nrofCopies = (Integer)msg.getProperty(MSG_COUNT_PROPERTY);
		assert nrofCopies != null : "Not a SnW message: " + msg;
		
		if (isDeliveredMessage(msg)) 
		{
		   this.ackedMessageIds.add(id);
		}
		
		 if(msg.drop!=-1)
	       {nrofCopies=(int)msg.drop;
		   msg.updateProperty(MSG_COUNT_PROPERTY, nrofCopies);
		   msg.delegation=-1;
		   msg.drop=-1;
	       }	
		return msg;
	}
	
	protected void transferAborted(Connection con) 
	{ String msgId = con.getMessage().getId();
	  /* get this router's copy of the message */
	  Message msg = getMessage(msgId);

	  if (msg == null)  // message has been dropped from the buffer after..
		return; // ..start of transfer -> no need to reduce amount of copies
	  
	  msg.delegation=-1;
	  msg.drop=-1;
	}
	
	@Override 
	public boolean createNewMessage(Message msg) 
	{
		makeRoomForNewMessage(msg.getSize());
		msg.setTtl(this.msgTtl);
		msg.addProperty(MSG_COUNT_PROPERTY, new Integer(initialNrofCopies));
		addToMessages(msg, true);
		msg.delegation=-1;
		msg.drop=-1;
		return true;
	}
	
	private void changeRange(DTNHost host) {
		// TODO Auto-generated method stub
//		ASWRouter ar=(ASWRouter) host.getRouter();
//		List<NetworkInterface> net = host.getInterfaces();
//		
//		int[] a=new int[net.size()];
//		double p=0;
//		for(int i=0;i<net.size();i++){
//			a[i]=(int) net.get(i).getTransmitRange();
//		}
//		for(int j=0;j<a.length;j++){
//			p+=a[j];
//		}
//	    p=p/net.size();
//	    
//		if(ar.currentEnergy<=0.25*this.initEnergy[0]){
//			/* turn radio off */
//			//System.out.println(p*ar.currentEnergy/this.initEnergy[0]);
//			this.comBus.updateProperty(NetworkInterface.RANGE_ID, 35.0);
//			return; /* no more energy to start new transfers */
//		}
//		if(host.name.startsWith("h")){
//			this.comBus.updateProperty(NetworkInterface.RANGE_ID, 50.0);
//		}
	}
	
	@Override
	public void update() {
		super.update();
		reduceSendingAndScanningEnergy();
		//changeRange(getHost());
		if (!canStartTransfer() || isTransferring()) 
		{
			return; // nothing to transfer or is currently transferring 
		}

		/* try messages that could be delivered to final recipient */
		if (exchangeDeliverableMessages() != null) 
		{
			return;
		}
		
		Collection<Message> msgCollection = getMessageCollection();
		List<Message> spraylist = new ArrayList<Message>();
		
		for (Connection con : getConnections()) 
		    {
		    for (Message m : msgCollection)
		        {   
		    	 DTNHost other = con.getOtherNode(getHost());
				 ASWRouter otherRouter = (ASWRouter)other.getRouter();
				 
		         double distTo = getHost().getLocation().distance(other.getLocation());
		         double dd = Math.pow(distTo/10,2);
		         double acc = 1-dd;
	
			     Integer nrofCopies = (Integer)m.getProperty(MSG_COUNT_PROPERTY);
		        
		         if (nrofCopies > 1)
					{   double i=getutility(m.getTo());
					    double j = otherRouter.getutility(m.getTo());
					    double ran = Math.random();
			        	if(acc>ran)
			        	{
			        		if(nrofCopies>2)
			        		{
			        			if(i<j)
			        			{ 
								float ratio= (float)((j)/(i+j));
								m.delegation=(nrofCopies/2)-(int)Math.round(ratio);
								m.drop=(nrofCopies/2)+(int)Math.round(ratio);
			        			}

			        			else
			        			{   
								float ratio= (float)((i)/(i+j));
								m.delegation=(nrofCopies/2)+(int)Math.round(ratio);
								m.drop=(nrofCopies/2)-(int)Math.round(ratio);	
			        			}
			        		}
			        		else if((nrofCopies==2)||((i==0)&&(j==0)))
			        		{
							m.delegation=(nrofCopies/2);
							m.drop=(nrofCopies/2);
			        		}
				            spraylist.add(m);
			        	}
					}
		        }
		    }
		       Collections.sort(spraylist,new MaxPropComparator());
			   tryMessagesToConnections(spraylist, getConnections());
	              }
		    
	@Override
	protected void transferDone(Connection con) {
		Integer nrofCopies;
		String msgId = con.getMessage().getId();
		/* get this router's copy of the message */
		Message msg = getMessage(msgId);

		if (msg == null) { // message has been dropped from the buffer after..
			return; // ..start of transfer -> no need to reduce amount of copies
		}

		if (msg.getTo() == con.getOtherNode(getHost())) 
		{ 
			this.ackedMessageIds.add(msg.getId()); // yes, add to ACKed messages
			this.deleteMessage(msg.getId(), false); // delete from buffer
		}
		
		nrofCopies = (Integer)msg.getProperty(MSG_COUNT_PROPERTY);
       if(msg.delegation!=-1)
       {nrofCopies=(int)msg.delegation;
       if(nrofCopies==0)
    	   nrofCopies=1;
	   msg.updateProperty(MSG_COUNT_PROPERTY, nrofCopies);
	   msg.delegation=-1;
	   msg.drop=-1;
       }
	}
	

	/*duration*/
	public double getencounterduration(DTNHost host) 
	{
		if (encounterduration.containsKey(host)) 
			return encounterduration.get(host);	
		else 
			return 0;
	}


	public double saveconduration(double i, double j)
	{
	double e=j-i;
	return e;
	}

	private void updateencounterduration(DTNHost host, double m) 
	{
	double newValue = m;
	encounterduration.put(host, newValue);
	}

/*--------------------------------------*/

/*Contact Numbers*/

	private void updateencountercount(DTNHost host) 
	{
	 double oldValue = getencountercount(host);
	 double newValue = oldValue+1 ;
	 encountercount.put(host, newValue);
	}

	public double getencountercount(DTNHost host) 
	{
	if (encountercount.containsKey(host)) 
		return encountercount.get(host);
	else 
		return 0;
	}


	public void updatenewvalue (DTNHost host)
	{
    
		double encounterduration = getencounterduration(host);
		double encountercount = getencountercount(host);
		double meetingtime = getintermeetingtime(host);
		double d1=0;
		double d2=0;
		double d3;

		if(encountercount>1)
		{
    	d1 = (encounterduration/meetingtime);
        d2 = (d1+getoldvalue(host));
        d3 = d2/(encountercount-1);
		} 

		else 
    	d3 =0;

		updateoldvalue(host, d2);
		newvalue.put(host,d3);
	}

	public double getnewvalue(DTNHost host)
	{ 
		if (newvalue.containsKey(host))
			return newvalue.get(host);
	          
		else
			return 0;
	}

	public double getoldvalue(DTNHost host)
	{
	
		if (oldvalue.containsKey(host))
			return oldvalue.get(host);
	
		else 
			return 0;
	}


	public void updateoldvalue(DTNHost host, double value)
	{
	oldvalue.put(host, value);
	}
	
	public void updateutility(DTNHost host)
	{
	int number=1;
	double sum = 0;
	double difference=0;
	double value = 0;
	DTNHost thishost =  getHost();

	Iterator<DTNHost> it = neighbvalue.keySet().iterator();   
	while (it.hasNext())
	{   
	      DTNHost key;   
	      key=(DTNHost)it.next();   
	      if ((key != host)&&(key!=thishost))
	      {
	    	  Map<DTNHost, Double> map = neighbvalue.get(key);
	          if (map.containsKey(host))
	          { 
	        	  value = map.get(host);
	        	  number=number+1;
	        	  sum=sum+value;
	          }

	  if (newvalue.containsKey(host))
	  sum= sum+getnewvalue(host);
      difference = sum/number;
	  utility.put(host, difference);
	  }
	 }
	}

	public double getutility(DTNHost host)
	{
	if(utility.containsKey(host))
		return utility.get(host);
	else 
		return 0;
	}

	private void updaterecentencountertime(DTNHost host, double m)
	{
	recentencountertime.put(host, m);
	}

	private double getrecenterencountertime (DTNHost host)
	{
		if (recentencountertime.containsKey(host))
			return recentencountertime.get(host);
		else
			return 0;
	}

	private void updateintermeetingtime(DTNHost host, double t)
	{
	intermeetingtime.put(host,t);
	}

	private double getintermeetingtime(DTNHost host)
	{
	if (intermeetingtime.containsKey(host)) 
		return intermeetingtime.get(host);
	else 
		return 0;
	}

	protected Message getOldestMessage(boolean excludeMsgBeingSent) {
		Collection<Message> messages = this.getMessageCollection();
		List<Message> copyMessages = new ArrayList<Message>();
		for (Message m : messages) {	
			if (excludeMsgBeingSent && isSending(m.getId())) {
				continue; // skip the message(s) that router is sending
			}
			
			copyMessages.add(m);
				
		}
		
		     Collections.sort(copyMessages,new MaxPropComparator());
			 return copyMessages.get(copyMessages.size()-1); 
	}
	
	
	
	private class MaxPropComparator implements Comparator<Message> {
		private DTNHost from1;
		private DTNHost from2;
		
		public  MaxPropComparator()
		{
			
		this.from1 = this.from2 = getHost();
		}
		
		public int compare(Message msg1, Message msg2) {
			double p1, p2;
			Integer nrofCopies1 = (Integer)msg1.getProperty(MSG_COUNT_PROPERTY);
			Integer nrofCopies2 = (Integer)msg2.getProperty(MSG_COUNT_PROPERTY);
			p1=getCost(from1,msg1.getTo())*nrofCopies1;
			p2=getCost(from2,msg2.getTo())*nrofCopies2;
			
            if (p1==p2)
            	return-1;
            
			if (p1>p2) 
				return -1;
			else
				return 1;	
		    }
	
	}

	public double getCost(DTNHost from, DTNHost to){
		MessageRouter mRouter = from.getRouter();
		ASWRouter Router = (ASWRouter)mRouter;
		double value;
		value = Router.getutility(to);
		return value;
	}
	
	@Override
	public RoutingInfo getRoutingInfo() {
	
		RoutingInfo top = super.getRoutingInfo();
		RoutingInfo ri = new RoutingInfo(utility.size() + 
				" delivery prediction(s)");
		
		for (Map.Entry<DTNHost, Double> e : utility.entrySet()) {
			DTNHost host = e.getKey();
			Double value = e.getValue();
			
			
			ri.addMoreInfo(new RoutingInfo(String.format("%s : %.6f", 
					host, value)));
		}
		
		top.addMoreInfo(ri);
		return top;
	}
	
	public void moduleValueChanged(String key, Object newValue) {
		this.currentEnergy = (Double)newValue;
	}
	
	@Override
	public String toString() {
		return super.toString() + " energy level = " + this.currentEnergy;
	}	
	@Override
	public MessageRouter replicate() {
		return new ASWRouter(this);
	}
}