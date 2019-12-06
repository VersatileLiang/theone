package routing;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.Map.Entry;

import util.Tuple;



import routing.EBSRRouter.HighDropComparator;
import core.*;

/*My IEEE Sensors Journal*/
public class EBRRRouter extends ActiveRouter implements ModuleCommunicationListener
{   
	public static final String HERRPRouter_NS = "EBRRRouter";
	public static final String MaxNode_NS = "MaxNode";
	
	public Map<DTNHost, Double> EncounterTime;
	public Map<DTNHost, Double> LastEncounterTime;
	public Map<DTNHost, Double> EncounterDuration;
	public Map<DTNHost, Double> EncounterCount;
	public Map<DTNHost, Double> Utility;
	public Map<DTNHost, Double> PreviousValue;
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
    public  Map<DTNHost,Double>energy;
    public  Map<DTNHost,Double>CurrentEnergy;
	private ModuleCommunicationBus comBus;
	private static Random rng = null;
	public Set<String> ackedMessageIds;
	public double MaxNode=0;
	
	public EBRRRouter(Settings s) 
	{
		super(s);
this.initEnergy = s.getCsvDoubles(INIT_ENERGY_S);
		
		if (this.initEnergy.length != 1 && this.initEnergy.length != 2) {
			throw new SettingsError(INIT_ENERGY_S + " setting must have " + 
					"either a single value or two comma separated values");
		}
		this.energy=new HashMap<DTNHost,Double>();
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
		Settings VDFSettings = new Settings (HERRPRouter_NS);
		MaxNode = VDFSettings.getDouble(MaxNode_NS);
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
	
	protected EBRRRouter(EBRRRouter r) 
	{
		super(r);
		initPreds();
		this.MaxNode=r.MaxNode;
		this.initEnergy = r.initEnergy;
		setEnergy(this.initEnergy);
		this.scanEnergy = r.scanEnergy;
		this.transmitEnergy = r.transmitEnergy;
		this.scanInterval = r.scanInterval;
		this.warmupTime  = r.warmupTime;
		this.comBus = null;
		this.lastScanUpdate = 0;
		this.lastUpdate = 0;
		this.energy=new HashMap<DTNHost,Double>();
		this.CurrentEnergy=new HashMap<DTNHost,Double>();
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
	@Override
	public boolean createNewMessage(Message msg) 
	{
		makeRoomForNewMessage(msg.getSize());
		msg.setTtl(this.msgTtl);
		
		if(this.Utility.containsKey(msg.getTo()))
			msg.FirstDelegation=this.Utility.get(msg.getTo());
		else
			msg.FirstDelegation=Integer.MAX_VALUE;

		msg.FirstLabel=-1;
		
		msg.FirstValue=0;
		
		msg.MetNode.add(this.getHost());
	
		addToMessages(msg, true);
		return true;
	}	
	
	public  void updateCarrierCount(DTNHost Host)
	{
		Collection<Message> C1 = this.getMessageCollection();
		
		EBRRRouter OtherRouter = (EBRRRouter)Host.getRouter();
		Collection<Message> C2 = OtherRouter.getMessageCollection();
		
		for (Message m1:C1)
		{
			for (Message m2:C2)
			{
				if(m1.getId().equals(m2.getId()))
				{
					for (DTNHost h: m2.MetNode)
					{
						this.getMessage(m1.getId()).updateMetNode(h);
					}
					
					for (DTNHost h: m1.MetNode)	
					{
						OtherRouter.getMessage(m2.getId()).updateMetNode(h);
					}
				}		
			}
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
	
	public void changeRange(DTNHost host){
		if(host.name.startsWith("a")){
			    ModuleCommunicationBus com=host.getComBus();
				/* turn radio off */
				com.updateProperty(NetworkInterface.RANGE_ID, 50.0);
				return; /* no more energy to start new transfers */
			}
	}
	
	@Override
	public void update() 
	{
		super.update();	
		reduceSendingAndScanningEnergy();
		changeRange(getHost());
		if (!canStartTransfer() || isTransferring()) 
			return; // nothing to transfer or is currently transferring 
		
		/* try messages that could be delivered to final recipient */
		if (exchangeDeliverableMessages() != null) 
			return;
		tryOtherMessages();
	}
	
	public void dynamicUpdateDelegation()
	{
		Collection<Message> c1 = this.getMessageCollection();
		
		for (Message m:c1)
		{
			if(this.Utility.containsKey(m.getTo()))
			{
				if(m.FirstDelegation>this.Utility.get(m.getTo()))
					 m.FirstDelegation=this.Utility.get(m.getTo());
			}
		}
	}
		
	public  Tuple<Message, Connection> tryOtherMessages() 
	{	
		Collection<Message> msgCollection = getMessageCollection();
		List<Tuple<Message, Connection>> Approach = new ArrayList<Tuple<Message, Connection>>(); 
		List<Tuple<Message, Connection>> Desin = new ArrayList<Tuple<Message, Connection>>(); 

		for(Connection c : getConnections())
		{
			DTNHost Other = c.getOtherNode(getHost());
	        EBRRRouter OtherRouter = (EBRRRouter)Other.getRouter();
	        
		    for (Message m : msgCollection)     
		    {   
		    	double ExpectDelay=m.getinittl()*60-(SimClock.getTime()-m.getCreationTime());  

		    	if (OtherRouter.hasMessage(m.getId())) 	
		    		continue;
		    	
		    	if (OtherRouter.isTransferring()) 
		    		continue; 
		    	
		    	if ((ackedMessageIds.contains(m.getId()))||(OtherRouter.ackedMessageIds.contains(m.getId())))
		    		continue;
		    	

		    	if(OtherRouter.Utility.containsKey(m.getTo()))
		    	{
		    		if((m.FirstDelegation>OtherRouter.Utility.get(m.getTo())))		 	
		    		{   
		    			m.FirstLabel=1;
		    			m.FirstValue=OtherRouter.Utility.get(m.getTo());
		    			Approach.add(new Tuple<Message, Connection>(m, c));		    		 
		    		}
		    		
		    		else if ((m.FirstDelegation>ExpectDelay))
		    			Approach.add(new Tuple<Message, Connection>(m, c));	
		    	}	

		    	
		    	else if ((!OtherRouter.Utility.containsKey(m.getTo())))
		    	{
		    		double V ; 
		    		V =1-ExpectDelay/(m.getinittl()*60); 
	    		
		    		double P=Math.pow(V, m.MetNode.size());
		    		double Ran = Math.random();
	    			
		    		if(P>Ran)
		    		{
		    			Desin.add(new Tuple<Message, Connection>(m, c));
		    		}
		    	}
		    }
		} 
		 
		Collections.sort(Approach, new HighTupleComparator());	
		Collections.sort(Desin, new LowTupleComparator());	

		if((Approach.size()==0)&&(Desin.size()==0))
			return null;
		
		if(tryMessagesForConnected(Approach)!=null)   
			return tryMessagesForConnected(Approach);
		else
			return tryMessagesForConnected(Desin);
	} 

	private class HighDropComparator implements Comparator<Message> 
	{
		public   HighDropComparator()
		{}
		
		public int compare(Message Msg1, Message Msg2) 
		{
			double Priority1=0;
			double Priority2=0;

			double Copies1 = MaxNode-Msg1.MetNode.size();
			double Copies2 = MaxNode-Msg2.MetNode.size();
			
			double V1,V2;

			V1=1-((Msg1.getTtl()*60-Msg1.FirstDelegation)/(Msg1.getTtl()*60));
			V2=1-((Msg2.getTtl()*60-Msg2.FirstDelegation)/(Msg2.getTtl()*60));
			
			Priority1 = 1-Math.pow(V1, Copies1);
			Priority2 = 1-Math.pow(V2, Copies2);

			if(Priority1>Priority2)
				return -1;
			else if (Priority1==Priority2)
				return -1;
			else 
				return 1;
		}
	}
	
	public class LowDropComparator implements Comparator<Message> 
	{
		public   LowDropComparator()
		{}
		
		public int compare(Message Msg1, Message Msg2) 
		{
			double Priority1=0;
			double Priority2=0;

			double Copies1 = MaxNode-Msg1.MetNode.size();
			double Copies2 = MaxNode-Msg2.MetNode.size();
			
			double V1,V2;

			V1=1-Msg1.getTtl()/Msg1.getinittl();
			V2=1-Msg2.getTtl()/Msg2.getinittl();
			
			Priority1=1-Math.pow(V1, Copies1);
			Priority2=1-Math.pow(V2, Copies2);

			if (Priority1>Priority2) 
				return -1;
			else  
				return 1;
		}
	}
	
	
	@Override
	protected Message getOldestMessage(boolean excludeMsgBeingSent)
	{
		Collection<Message> Messages = this.getMessageCollection();
		List<Message> HighBin = new ArrayList<Message>();
		List<Message> LowBin = new ArrayList<Message>();
		
		for (Message m : Messages) 
		{	
			if (excludeMsgBeingSent && isSending(m.getId())) 				
				continue; // skip the message(s) that router is sending
		          
			if(this.Utility.containsKey(m.getTo()))
				HighBin.add(m); 
			else	
				LowBin.add(m); 
		}

		Collections.sort(LowBin,new LowDropComparator());	
		System.setProperty("java.util.Arrays.useLegacyMergeSort", "true");
		
		Collections.sort(HighBin,new HighDropComparator());
		 
		if(LowBin.size()!=0)
			return LowBin.get(LowBin.size()-1);
		else
			return HighBin.get(HighBin.size()-1);
	}

	@Override
	public EBRRRouter replicate()  
	{
		   return new EBRRRouter(this);
	}
		
	public  void updateMsgDelegation(DTNHost Host)	
	{	
		Collection<Message> C1 = this.getMessageCollection();
		EBRRRouter OtherRouter = (EBRRRouter)Host.getRouter();
		Collection<Message> C2 = OtherRouter.getMessageCollection();

		for (Message m1:C1)	
		{
			for (Message m2:C2)	
			{	
				double ThisDelegation=m1.FirstDelegation;	
				double OtherDelegation=m2.FirstDelegation;	
				double MinDelegation=Math.min(ThisDelegation, OtherDelegation);
					
				if(m1.getId().equals(m2.getId()))	
				{	
					m2.FirstDelegation=MinDelegation;		
					m1.FirstDelegation=MinDelegation;	
				}
			}
		}
	}
	
	@Override
	public void changedConnection(Connection con) 	
	{
		if (con.isUp())    
		{
			DTNHost OtherHost = con.getOtherNode(getHost());	
			EBRRRouter OtherRouter = (EBRRRouter)OtherHost.getRouter();
			List<NetworkInterface> net=OtherHost.getInterfaces();
            
		    
			for(NetworkInterface n:net){
				System.out.println(n.getTransmitRange());
			}
			double StartTime=SimClock.getTime();

			double S = StartTime-getLastEncounterTime(OtherHost);
	
			updateLastEncounterTime(OtherHost, StartTime);	
			updateEncounterCount(OtherHost);	
			updateEncounterTime(OtherHost, S);

			if(this.getEncounterCount(OtherHost)>1)		
			{	
				double ET=this.getEncounterTime(OtherHost);	
				double ED=this.getEncounterDuration(OtherHost);
	
				updatePreviousValue(OtherHost,(ET-ED));	
				updateUtility(OtherHost);
			}
			
			else if (this.getEncounterCount(OtherHost)==1)	
			{
				double ET=this.getEncounterTime(OtherHost);	
				
				updatePreviousValue(OtherHost,(ET-0));	
				updateUtility(OtherHost);
			}
				
			updateTransitivity(OtherHost);					
			updateCarrierCount(OtherHost);
			updateMsgDelegation(OtherHost);
			
			dynamicUpdateDelegation();
				
			this.ackedMessageIds.addAll(OtherRouter.ackedMessageIds);	
			OtherRouter.ackedMessageIds.addAll(this.ackedMessageIds);
	
			deleteAckedMessages();
			OtherRouter.deleteAckedMessages();	

		}
				
		else if (!con.isUp())   
		{
			double FinishTime= SimClock.getTime();	
			DTNHost Peer = con.getOtherNode(getHost());  
			double C = FinishTime-this.getLastEncounterTime(Peer);  
			updateEncounterDuration(Peer,C);
		}
	}

	public void updateEncounterCount(DTNHost Host) 
	{	
		double OldValue = getEncounterCount(Host);	
		double NewValue = OldValue + 1;	
		EncounterCount.put(Host, NewValue);
	}
	
	public double getEncounterCount(DTNHost Host) 
	{   
		if(this.EncounterCount.containsKey(Host))
			return this.EncounterCount.get(Host);
		else
			return 0;		
	}
		
	public void updateEncounterDuration(DTNHost Host, double M) 
	{	
		double NewValue = M;	
		EncounterDuration.put(Host, NewValue);
	}
			
	public double getEncounterDuration(DTNHost Host) 	
	{   	
		if(this.EncounterDuration.containsKey(Host))	
			return this.EncounterDuration.get(Host);	
		else	
			return 0;		
	}
		
	private void updateEncounterTime(DTNHost Host, double T)
	{
		EncounterTime.put(Host,T);
	}

	private double getEncounterTime(DTNHost Host)
	{
		if (EncounterTime.containsKey(Host)) 
			return EncounterTime.get(Host);
		else 
			return Integer.MAX_VALUE;
	}
		
	private void updateLastEncounterTime(DTNHost Host, double T)
	{
		LastEncounterTime.put(Host,T);
	}
	
	private double getLastEncounterTime(DTNHost Host)	
	{
		if (LastEncounterTime.containsKey(Host)) 
			return LastEncounterTime.get(Host);
		else 
			return 0;
	}
			
	public void updatePreviousValue(DTNHost Host, double V)
	{
		double OldValue = getPreviousValue(Host);	
		double NewValue = OldValue + V;
		PreviousValue.put(Host, NewValue);
	}

	public double getPreviousValue(DTNHost Host)
	{
		if (PreviousValue.containsKey(Host)) 	
			return PreviousValue.get(Host);
		else
			return 0;
	}
		
	public void updateUtility(DTNHost Host)
	{	
		double C=this.getEncounterCount(Host);
		double Here;
		
		if(C==1)
			Here =this.PreviousValue.get(Host);
		else
			Here =this.PreviousValue.get(Host)/(C);
			
		this.Utility.put(Host, Here);
	}
		
	public void updateTransitivity(DTNHost Host)
	{   	
		DTNHost OtherHost = Host; 
		MessageRouter MRouter = OtherHost.getRouter();  
		EBRRRouter OtherRouter = (EBRRRouter)MRouter;

		for(Map.Entry<DTNHost, Double> entry : this.Utility.entrySet())
		{    
			DTNHost H = entry.getKey();
			
			/*skip the current encountered node*/   
			if(H == OtherHost) 
				continue;
 
			double LocalValue = entry.getValue();
 
			if(OtherRouter.Utility.containsKey(H))   
			{  
				double OtherValue =OtherRouter.Utility.get(H);  
				double Diff=this.Utility.get(OtherHost);
   
				if(this.Utility.containsKey(H))  
				{
					if(LocalValue> (OtherValue+Diff))   
						this.Utility.put(H, (OtherValue+Diff));
				}
			}  
		}
	}
		
	private void initPreds() 
	{
		this.ackedMessageIds = new HashSet<String>();
		this.EncounterCount=new HashMap<DTNHost, Double>();
		this.EncounterDuration=new HashMap<DTNHost, Double>();
		this.EncounterTime=new HashMap<DTNHost, Double>();
		this.LastEncounterTime=new HashMap<DTNHost, Double>();
		this.Utility=new HashMap<DTNHost, Double>();  
		 
		this.PreviousValue=new HashMap<DTNHost, Double>();
	}

	@Override
	protected void transferDone(Connection con) 
	{
		String msgId = con.getMessage().getId();
		Message msg = getMessage(msgId);
		DTNHost other = con.getOtherNode(getHost());

		if (msg == null)  // message has been dropped from the buffer after..
			return; // ..start of transfer -> no need to reduce amount of copies

		else 
		{
			if(msg.FirstLabel!=-1)  
			{  
				msg.FirstDelegation=msg.FirstValue;	     
				msg.FirstLabel=-1;
			}
		}
			
		msg.MetNode.add(other);
	
		if (msg.getTo() == con.getOtherNode(getHost())) 
		{ 
			this.ackedMessageIds.add(msg.getId()); // yes, add to ACKed messages
			this.deleteMessage(msg.getId(), false); // delete from buffer
		}
	}
		
	@Override
	protected void transferAborted(Connection con) 
	{ 
		String msgId = con.getMessage().getId();
		Message msg = getMessage(msgId);

		if (msg == null)  // message has been dropped from the buffer after..
			return; // ..start of transfer -> no need to reduce amount of copies

		this.getMessage(msg.getId()).FirstLabel=-1;
	}
		
	@Override
	public Message messageTransferred(String id, DTNHost from) 
	{	
		Message msg = super.messageTransferred(id, from);

		if(msg.FirstLabel!=-1) 
		{ 
			msg.FirstDelegation=msg.FirstValue; 
			msg.FirstLabel=-1;
		}		
		  
		msg.MetNode.add(this.getHost());
		return msg;
	}	
	
	public class LowTupleComparator implements Comparator <Tuple<Message, Connection>> 	
	{
		public int compare(Tuple<Message, Connection> Tuple1,Tuple<Message, Connection> Tuple2) 
		{
				
			Message Msg1=Tuple1.getKey();  
			Message Msg2=Tuple2.getKey();
				
			double Copies1=MaxNode-Msg1.MetNode.size();
			double Copies2=MaxNode-Msg2.MetNode.size();

			double V1=1-(Msg1.getTtl()/Msg1.getinittl());
			double V2=1-(Msg2.getTtl()/Msg2.getinittl());

			double Priority1= 1-Math.pow(V1,Copies1);	
			double Priority2= 1-Math.pow(V2,Copies2);
				
			if (Priority1>Priority2)  
				return -1;
			else
				return 1;
		}
	}	
		
	private class HighTupleComparator implements Comparator <Tuple<Message, Connection>> 
	{	
		public int compare(Tuple<Message, Connection> Tuple1,Tuple<Message, Connection> Tuple2)     
		{	
			Message Msg1=Tuple1.getKey();  	
			Message Msg2=Tuple2.getKey();

			double Copies1 = MaxNode-Msg1.MetNode.size();	
			double Copies2 = MaxNode-Msg2.MetNode.size();

			double V1=1-((Msg1.getTtl()*60-Msg1.FirstDelegation)/(Msg1.getTtl()*60));	
			double V2=1-((Msg2.getTtl()*60-Msg2.FirstDelegation)/(Msg2.getTtl()*60));

			double Priority1= 1-Math.pow(V1,Copies1);
			double Priority2= 1-Math.pow(V2,Copies2);

			if (Priority1>Priority2) 
				return -1;
			else
				return 1;
		}
	}
/*
	public RoutingInfo getRoutingInfo() 
	{
		RoutingInfo Top = super.getRoutingInfo();	
		RoutingInfo Ri = new RoutingInfo(Utility.size() + " Utility");
		
		RoutingInfo Eng = new RoutingInfo(this.currentEnergy+ " Current Energy ");
		Top.addMoreInfo(Eng);
			
		for (Entry<DTNHost, Double> e : Utility.entrySet()) 
		{
			DTNHost Host = e.getKey();
			Double Value = e.getValue();

			Ri.addMoreInfo(new RoutingInfo(String.format("%s : %.6f", Host, Value)));
		}

		Top.addMoreInfo(Ri);
		return Top;
	}
	*/
	@Override
	public String toString() {
		return super.toString() + " energy level = " + this.currentEnergy;
	}
	@Override
	public void moduleValueChanged(String key, Object newValue) 
	{
	
		
	}	
}