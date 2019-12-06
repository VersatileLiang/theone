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
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import core.*;
import routing.*;
import util.Tuple;

/**
 * Energy level-aware variant of Epidemic router.
 */
public class AngleRouter extends ActiveRouter 
		implements ModuleCommunicationListener{
	/** Initial units of energy -setting id ({@value}). Can be either a 
	 * single value, or a range of two values. In the latter case, the used
	 * value is a uniformly distributed random value between the two values. */
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

	private Map<DTNHost, Double> counts;
	private Map<DTNHost, Double> angle;
	private double lastlocation =0;
	/*for location updating*/
	double variationx=0;
	double variationy=0;
	public static final String NROF_COPIES = "nrofCopies";
	/** identifier for the binary-mode setting ({@value})*/ 
	public static final String BINARY_MODE = "binaryMode";
	/** SprayAndWait router's settings name space ({@value})*/ 
	public static final String ANGLE_NS = "AngleRouter";
	/** Message property key */
	public static final String MSG_COUNT_PROPERTY = ANGLE_NS + "." +
		"copies";
	
	protected int initialNrofCopies;
	private Set<String> ackedMessageIds;
	protected boolean isBinary;
	/**
	 * Constructor. Creates a new message router based on the settings in
	 * the given Settings object.
	 * @param s The settings object
	 */
	public AngleRouter(Settings s) {
		super(s);
		Settings snwSettings = new Settings(ANGLE_NS);
		this.initEnergy = s.getCsvDoubles(INIT_ENERGY_S);
		initialNrofCopies = snwSettings.getInt(NROF_COPIES);
		isBinary = snwSettings.getBoolean( BINARY_MODE);
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
	}
	
	/**
	 * Sets the current energy level into the given range using uniform 
	 * random distribution.
	 * @param range The min and max values of the range, or if only one value
	 * is given, that is used as the energy level
	 */
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
	
	/**
	 * Copy constructor.
	 * @param r The router prototype where setting values are copied from
	 */
	protected AngleRouter(AngleRouter r) {
		super(r);
		initPreds();
		this.initEnergy = r.initEnergy;
		this.initialNrofCopies=r.initialNrofCopies;
		this.isBinary = r.isBinary;
		setEnergy(this.initEnergy);
		this.scanEnergy = r.scanEnergy;
		this.transmitEnergy = r.transmitEnergy;
		this.scanInterval = r.scanInterval;
		this.warmupTime  = r.warmupTime;
		this.comBus = null;
		this.lastScanUpdate = 0;
		this.lastUpdate = 0;
	}
	
	@Override
	protected int checkReceiving(Message m,DTNHost from) {
		if (this.currentEnergy < 0) {
			return DENIED_UNSPECIFIED;
		}
		else {
			 return super.checkReceiving(m,from);
		}
	}
	
	public boolean createNewMessage(Message msg) {
		makeRoomForNewMessage(msg.getSize());
		msg.setTtl(this.msgTtl);
		msg.addProperty(MSG_COUNT_PROPERTY, new Integer(initialNrofCopies));
		addToMessages(msg, true);
		msg.updateMetNode(getHost());
		msg.delegation=(int) getcountFor(msg.getTo());
		return true;
	}
	
	/**
	 * Updates the current energy so that the given amount is reduced from it.
	 * If the energy level goes below zero, sets the level to zero.
	 * Does nothing if the warmup time has not passed.
	 * @param amount The amount of energy to reduce
	 */
	protected void reduceEnergy(double amount) {
		if (SimClock.getTime() < this.warmupTime) {
			return;
		}
		
		comBus.updateDouble(ENERGY_VALUE_ID, -amount);
		if (this.currentEnergy < 0) {
			comBus.updateProperty(ENERGY_VALUE_ID, 0.0);
		}
	}
	
	/**
	 * Reduces the energy reserve for the amount that is used by sending data
	 * and scanning for the other nodes. 
	 */
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
	
	private void deleteAckedMessages() {
		for (String id : this.ackedMessageIds) {
			if (this.hasMessage(id) && !isSending(id)) {
				this.deleteMessage(id, false);
			}
		}
	}
		
	@SuppressWarnings("unchecked")
	public void update() {
		super.update();
		reduceSendingAndScanningEnergy();
		updatelocation();
		
		double i=0;
		double j=0;

		if (!canStartTransfer() || isTransferring()) {
			return; // nothing to transfer or is currently transferring 
		}

		/* try messages that could be delivered to final recipient */
		if (exchangeDeliverableMessages() != null) {
			return;
		}
		
		Collection<Message> msgCollection = getMessageCollection();
		List<Message> epidemiclist = new ArrayList<Message>();
		List<Tuple<Message, Connection>> dellist = 
			new ArrayList<Tuple<Message, Connection>>(); 
		
		 for(Connection c : getConnections())
		    {
	    	DTNHost other = c.getOtherNode(getHost());
	        AngleRouter otherRouter = (AngleRouter)other.getRouter();
	        
	        
		       for (Message m : msgCollection)
		           {
			       if(otherRouter.hasMessage(m.getId()))
				   continue;
			       if(m.getTo()==other){
			    	   m.setResponseSize((int) (this.getBufferSize()*0.1));
			       }
			       updateangle(other);
			       i=getangle(other)/Math.PI;
			       j=Math.random();
			       double msgprobability=1-m.getSizeMetNode()/126;
			       if(i<=j)
			         {   
			         if(msgprobability>j)
			            epidemiclist.add(m);
			         }
			       
			        else if(i>j)
			               {if(m.delegation<otherRouter.getcountFor(m.getTo()))
			            	   dellist.add(new Tuple<Message, Connection>(m,c));
		 	               }
		             }           
	        }
		Collections.sort(epidemiclist, new EpidemicComparator());
		Collections.sort(dellist, new DelComparator());
		
				if(tryMessagesToConnections(epidemiclist, getConnections())==null)
				{
		        if(tryMessagesForConnected(dellist)!=null){}
		        }
        }
		
	@Override
	public AngleRouter replicate() {
		return new AngleRouter(this);
	}
	
	/**
	 * Called by the combus is the energy value is changed
	 * @param key The energy ID
	 * @param newValue The new energy value
	 */
	public void moduleValueChanged(String key, Object newValue) {
		this.currentEnergy = (Double)newValue;
	}

	private void updateangle(DTNHost host){
		   double encounterangle=0;
		   AngleRouter otherRouter = 
		   (AngleRouter)host.getRouter();
		   		   
		   if((getchangex()==0)&&(getchangey()==0)||(otherRouter.getchangex()==0)&&(otherRouter.getchangey()==0))
		//	   System.out.println("some node is stopped");
				   
		      if(((this.getchangex()* otherRouter.getchangex())<=0)
				&&((this.getchangey()* otherRouter.getchangey())>=0))
		    	  
			   {encounterangle=this.getsin(this.getchangex(),this.getchangey())+ 
		    	otherRouter.getsin(otherRouter.getchangex(),otherRouter.getchangey());
			   
			   if(encounterangle>Math.PI)
		//	   System.out.println("angle is larger than 180");
			   angle.put(host,encounterangle);
		       }
		      
		      else if(((this.getchangex()* otherRouter.getchangex())>=0)
					 &&((this.getchangey()* otherRouter.getchangey())>=0))
		    	  
				   {encounterangle=this.getcos(this.getchangex(),this.getchangey())- 
		    	    otherRouter.getcos(otherRouter.getchangex(),otherRouter.getchangey());
				   
				   if(encounterangle>Math.PI)
			//		   System.out.println("angle is larger than 180");  
			       angle.put (host,(Math.abs(encounterangle)));
			       }
		      
		      else if(((this.getchangex()* otherRouter.getchangex())<=0)
					  &&((this.getchangey()* otherRouter.getchangey())<=0))
				   {
	            	    encounterangle=this.getsin(this.getchangex(),this.getchangey())
	              	    +(0.5*Math.PI)+ 
		    	        otherRouter.getcos(otherRouter.getchangex(),otherRouter.getchangey());
		     
	            	    if (encounterangle>Math.PI)
	  	    	        {encounterangle=(2*Math.PI)- encounterangle;
	         			 
	         			 }
			            angle.put(host,encounterangle); 
			       }
				     
		      else if(((this.getchangex()* otherRouter.getchangex())>=0)
					  &&((this.getchangey()* otherRouter.getchangey())<=0))
		    	  
		    	     {encounterangle=this.getcos(this.getchangex(),this.getchangey())+ 
		    	     otherRouter.getcos(otherRouter.getchangex(),otherRouter.getchangey());
		    	     if(encounterangle>Math.PI)
		  //			   System.out.println("angle is larger than 180");
		    	     angle.put(host,encounterangle);    
		    	     }   
		   }

	   public double getangle(DTNHost host){
		   if(angle.containsKey(host))
			   return angle.get(host);
	   else
		   return 0;
	   }
	   
	   public void updatelocation() {
			double timeDiff = (SimClock.getTime() - this.lastlocation) ;
			
			
			if(timeDiff>1)
			{   
				double locationx=this.getHost().getLocation().getX();
			    double locationy=this.getHost().getLocation().getY();
			    /*update the change*/
			    this.updatechangex();
			    this.updatechangey();
			    /*then store the current location into the old*/
				this.getHost().getLocation().setOldLocation(locationx, locationy);
				/*then update the updating time*/
			    this.lastlocation = SimClock.getTime();  
			}
			
			else
				return;
		}

		public double getcos(double xchange, double ychange){
		    double x=Math.abs(xchange);
		    double y=Math.abs(ychange);
		    double z = Math.sqrt(x*x+y*y); 
		    return Math.acos(x/z);  
		}

		public double getsin(double xchange, double ychange){
			 
		   double x=Math.abs(xchange);
		   double y=Math.abs(ychange);
		   double z = Math.sqrt(x*x+y*y);
		   return Math.asin(x/z);
		 }
		
		public double getchangex()
		{return variationx;
		}
		
		public double getchangey()
		{return variationy;
		}
		
		@SuppressWarnings("unused")
		private void updatechangex(){
			variationx= this.getHost().getLocation().getX()-this.getHost().getLocation().getOldX();
		}

		@SuppressWarnings("unused")
		private void updatechangey(){
			variationy= this.getHost().getLocation().getY()-this.getHost().getLocation().getOldY();
		}

		private void updatecount(DTNHost host) {
			double oldValue = getcountFor(host);
			 double newValue = oldValue+1 ;
			 
			counts.put(host, newValue);
		}

		public double getcountFor(DTNHost host) {
			
			if (counts.containsKey(host)) 
				return counts.get(host);
			else 
				return 0;
		}
		
		public  void updatemsgdelegation(DTNHost host)
		{
			Collection<Message> c1 = this.getMessageCollection();
			
			AngleRouter otherRouter = 
		    (AngleRouter)host.getRouter();
			Collection<Message> c2 = otherRouter.getMessageCollection();
			
			for (Message m1:c1)
			    {
				for (Message m2:c2)
				    {
					double thisvalue=m1.delegation;
					double othervalue = m2.delegation;
					if(m1.getId()==m2.getId())
					  {   
						if(thisvalue<othervalue)
						  this.getMessage(m1.getId()).delegation=(int) othervalue;
					  }		
				    }
			   }
		}
		
		
		public  void updatemsgcount(DTNHost host){
			Collection<Message> c1 = this.getMessageCollection();
			
			AngleRouter otherRouter = 
		    (AngleRouter)host.getRouter();
			Collection<Message> c2 = otherRouter.getMessageCollection();
			
			for (Message m1:c1){
				for (Message m2:c2){
					if(m1.getId()==m2.getId())
					{
						for (DTNHost h: m2.MetNode)
							{this.getMessage(m1.getId()).updateMetNode(h);}
						for (DTNHost h: m1.MetNode)
							{otherRouter.getMessage(m2.getId()).updateMetNode(h);}
					}		
				}
			}
		}
		
		public void changedConnection(Connection con) {
			if (con.isUp()) 
			   {
				DTNHost otherHost = con.getOtherNode(getHost());
				AngleRouter otherRouter = 
				(AngleRouter)otherHost.getRouter();
		
				updatecount(otherHost);
				updatemsgcount(otherHost);
				updatemsgdelegation(otherHost);
				
				this.ackedMessageIds.addAll(otherRouter.ackedMessageIds);
				otherRouter.ackedMessageIds.addAll(this.ackedMessageIds);
				deleteAckedMessages();
				otherRouter.deleteAckedMessages();
			    }
			else if (!con.isUp())
				angle.clear();
		}
		
		private void initPreds() {
			this.counts = new HashMap<DTNHost, Double>();
			this.angle= new HashMap<DTNHost, Double>();
			this.ackedMessageIds = new HashSet<String>();
		}
	
		protected void transferDone(Connection con) {
		
			String msgId = con.getMessage().getId();
			/* get this router's copy of the message */
			Message msg = getMessage(msgId);
			DTNHost other = con.getOtherNode(getHost());
			AngleRouter othRouter = (AngleRouter)other.getRouter();
		
			if (msg == null)  // message has been dropped from the buffer after..
				return; // ..start of transfer -> no need to reduce amount of copies
			
			  if (msg.getTo() == con.getOtherNode(getHost())) { 
					this.ackedMessageIds.add(msg.getId()); // yes, add to ACKed messages
					this.deleteMessage(msg.getId(), false); // delete from buffer
				}
			    Integer nrofCopies;
						
				/* reduce the amount of copies left */
				nrofCopies = (Integer)msg.getProperty(MSG_COUNT_PROPERTY);
				if (isBinary) { 
					nrofCopies /= 2;
				}
				else {
					nrofCopies--;
				}
				msg.updateProperty(MSG_COUNT_PROPERTY, nrofCopies);
		}
		
		public Message messageTransferred(String id, DTNHost from) {
			Message m = super.messageTransferred(id, from);
			
			if (isDeliveredMessage(m)) {
				this.ackedMessageIds.add(id);
			}
			Integer nrofCopies = (Integer)m.getProperty(MSG_COUNT_PROPERTY);
			
			assert nrofCopies != null : "Not a SnW message: " + m;
			
			if (isBinary) {
				/* in binary S'n'W the receiving node gets ceil(n/2) copies */
				nrofCopies = (int)Math.ceil(nrofCopies/2.0);
			}
			else {
				/* in standard S'n'W the receiving node gets only single copy */
				nrofCopies = 1;
			}
			
			m.updateProperty(MSG_COUNT_PROPERTY, nrofCopies);
			m.updateMetNode(getHost());
			return m;
		}
		
		private class EpidemicComparator implements Comparator<Message> {
	
			public   EpidemicComparator()
			{
			}
			
			public int compare(Message msg1, Message msg2) {
				double p1, p2;

				p1=msg1.getSizeMetNode();
				p2=msg2.getSizeMetNode();
				
	            if (p1==p2){
	            	if (msg1.getTtl()<msg2.getTtl())
	            		return -1;
	            	else 
	            		return 1;
	            	}
	            
				if (p1<p2) 
					return -1;
				else
					return 1;	
			}		
		}

		private class DelComparator implements Comparator 
		<Tuple<Message, Connection>> {

		public int compare(Tuple<Message, Connection> tuple1,
				Tuple<Message, Connection> tuple2) {
			// delivery probability of tuple1's message with tuple1's connection
			double p1 = ((AngleRouter)tuple1.getValue().
					getOtherNode(getHost()).getRouter()).getcountFor(
					tuple1.getKey().getTo());
			// -"- tuple2...
			double p2 = ((AngleRouter)tuple2.getValue().
					getOtherNode(getHost()).getRouter()).getcountFor(
					tuple2.getKey().getTo());
			
			double p3 = tuple1.getKey().delegation;
			double p4 = tuple2.getKey().delegation;
			
			double p5 = p1-p3;
			double p6 = p2-p4;

			
			  if (p5==p6){
	            	if (tuple1.getKey().getTtl()<tuple2.getKey().getTtl())
	            		return -1;
	            	else 
	            		return 1;
	            	}
	            
				if (p5>p6) 
					return -1;
				else
					return 1;
		}
	}
		
		
		private class DropComparator implements Comparator<Message> {

			public   DropComparator()
			{
			
			}
			
			public int compare(Message msg1, Message msg2) {
				double p1, p2,p3,p4;
				
				double drop1,drop2;

				p1=msg1.delegation;
				p2=msg2.delegation;
				p3=msg1.getSizeMetNode();
				p4=msg2.getSizeMetNode();
				
				drop1=p1/p3;
				drop2=p2/p4;
				
	            if (drop1==drop2){
	            	if (msg1.getTtl()<msg2.getTtl())
	            		return -1;
	            	else 
	            		return 1;
	            	}
	            
				if (drop1>drop2) 
					return -1;
				else
					return 1;	
			}		
		}
		
		protected Message getOldestMessage(boolean excludeMsgBeingSent) {
			Collection<Message> messages = this.getMessageCollection();
			List<Message> singleMessages = new ArrayList<Message>();
			for (Message m : messages) {	
				if (excludeMsgBeingSent && isSending(m.getId())) {
					continue; // skip the message(s) that router is sending
				}
				
					singleMessages.add(m);	
			}
			 Collections.sort(singleMessages,new DropComparator());
				 return singleMessages.get(singleMessages.size()-1); 
				 // return last message
		}
		
	@Override
	public String toString() {
		return super.toString() + " energy level = " + this.currentEnergy;
	}	
}