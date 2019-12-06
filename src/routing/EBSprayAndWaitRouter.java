 package routing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;





import java.util.Set;





//import routing.TupleComparator.TupleComparator;
import util.Tuple;
import core.Connection;
import core.DTNHost;
import core.Message;
import core.Settings;
import core.SimClock;


 public class EBSprayAndWaitRouter extends ActiveRouter
{
	/** identifier for the initial number of copies setting ({@value})*/ 
	public static final String NROF_COPIES = "nrofCopies";//��Ϣ�ķ���
	public static final String ENCOUNTERBASED_MODE = "EncounterBasedMode";
	/** SprayAndWait router's settings name space ({@value})*/ 
	public static final String EBSPRAYANDWAIT_NS = "EBSprayAndWaitRouter";
	/** Message property key *///MSG_COUNT_PROPERTYΪSprayAndWaitRouter.copies
	public static final String MSG_COUNT_PROPERTY = EBSPRAYANDWAIT_NS + "." +
		"copies";
	
	public Map<DTNHost, Double> EncounterTime;
	public Map<DTNHost, Double> LastEncounterTime;
	public Map<DTNHost, Double> EncounterDuration;
	public Map<DTNHost, Double> EncounterCount;
	public Map<DTNHost, Double> Utility;
	public Map<DTNHost, Double> PreviousValue;
	public Map<DTNHost, Double> Proportion;
	public Map<DTNHost, Double> CurrentWindowCounter;
	public Map<DTNHost,Double> LastAgeUpdate;
	public static final String SECONDS_IN_UNIT_S ="secondsInTimeUnit";
	
	private int secondsInTimeUnit;//���������һ��(age)
	public Map<DTNHost,Double> EncounterValue;
	//private static double CurrentWindowCounterupdate;
	public static final double ALFA = 0.85;
	//private double lastAgeUpdate;
	
	
	public Set<String> ackedMessageIds;
	
	protected int initialNrofCopies;
    protected boolean isEncounterBased;
    public EBSprayAndWaitRouter(Settings s)
    {
    	
    	super(s);
    	Settings ebsnwSettings=new Settings(EBSPRAYANDWAIT_NS );
    	initialNrofCopies=ebsnwSettings.getInt(NROF_COPIES);
    	isEncounterBased=ebsnwSettings.getBoolean( ENCOUNTERBASED_MODE);
    	secondsInTimeUnit=ebsnwSettings.getInt(SECONDS_IN_UNIT_S);
    	
    	
    }
    protected EBSprayAndWaitRouter(EBSprayAndWaitRouter r)
    {
    	super(r);
    	initPreds();
    	this.initialNrofCopies = r.initialNrofCopies;
    	this.isEncounterBased = r.isEncounterBased;
    	this.secondsInTimeUnit=r.secondsInTimeUnit;
    	
    }
    public void updateLastAgeUpdate(DTNHost Host,double T)
    {
    	LastAgeUpdate.put(Host,T);
    }
    public double getLastAgeUpdate(DTNHost Host)
    {
    	if(this.LastAgeUpdate.containsKey(Host))
    	{
    		return LastAgeUpdate.get(Host);
    	}
    	else
    		return 0;
    }
    public void updateCurrentWindowCounter(DTNHost Host)//����CWC
    {
    	double OldValue=getCurrentWindowCounter(Host);
    	double NewValue=OldValue+1.0;
    	if(this.CurrentWindowCounter.containsKey(Host))
    	{
    		CurrentWindowCounter.put(Host,NewValue);
    	}
    	else
    	{
    		
    		CurrentWindowCounter.put(Host,1.0);
    	}
    	
    	
    }
    public double getCurrentWindowCounter(DTNHost Host)
    {
    	if(this.CurrentWindowCounter.containsKey(Host))
    	{
    		return CurrentWindowCounter.get(Host);
    	}
    	else
    		return 0;
    	
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
	public void updateProportion(DTNHost Host)
	{
		
	}
	public double getProportion(DTNHost Host)
	{
		if(this.Proportion.containsKey(Host))
			return this.Proportion.get(Host);
		else
			return 0;
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
	public double getUtilityFor(DTNHost host)
	{
		if(Utility.containsKey(host))
		{
			return Utility.get(host);
		}
		else 
			return 0;
	}
	public double getEncounterValue(DTNHost host) {
		// make sure preds are updated before getting
		if (EncounterValue.containsKey(host)) {
		
			return EncounterValue.get(host);
		}
		else {
			return 0;
		}
	}
	private void updateEncounterValues(DTNHost Host) 
	{
		double timeDiff = (SimClock.getTime() - getLastAgeUpdate(Host))-secondsInTimeUnit;//���û����secondsInTimeUnit���Ͳ�����
		
		if (timeDiff<0.0) {
			return;
		}
		if(timeDiff>=0.0)
		{
			
			EncounterValue.put(Host, getCurrentWindowCounter(Host)*ALFA+(1-ALFA)*getEncounterValue(Host));
			updateLastAgeUpdate(Host,SimClock.getTime());
			//CurrentWindowCounterupdate=CurrentWindowCounter;
			CurrentWindowCounter.put(Host,0.0);
			//System.out.println(getEncounterValue(Host));
			
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
		
	public void updateTransitivity(DTNHost Host)
	{   
		DTNHost OtherHost = Host; 
		MessageRouter MRouter = OtherHost.getRouter();  
		EBSprayAndWaitRouter OtherRouter = (EBSprayAndWaitRouter)MRouter;

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
		this.EncounterValue=new HashMap<DTNHost, Double>();
		this.CurrentWindowCounter=new HashMap<DTNHost,Double>();
		this.Proportion=new HashMap<DTNHost,Double>();
		this.LastAgeUpdate=new HashMap<DTNHost,Double>();
	}
	public void changedConnection(Connection con) 	
	{
		if (con.isUp())    
		{
			DTNHost OtherHost = con.getOtherNode(getHost());	
			EBSprayAndWaitRouter OtherRouter = (EBSprayAndWaitRouter)OtherHost.getRouter();
						
			double StartTime=SimClock.getTime();

			double S = StartTime-getLastEncounterTime(OtherHost);
	
			updateLastEncounterTime(OtherHost, StartTime);	
			updateEncounterCount(OtherHost);	
			updateEncounterTime(OtherHost, S);
			updateCurrentWindowCounter(getHost());
			updateCurrentWindowCounter(OtherHost);//����CWC
			updateEncounterValues(getHost());
			updateEncounterValues(OtherHost);
			double sum=getEncounterValue(getHost())+getEncounterValue(con.getOtherNode(getHost()));
			//System.out.println(getEncounterValue(getHost()));
			//System.out.println(getEncounterValue(con.getOtherNode(getHost())));
        	Proportion.put(getHost(),getEncounterValue(con.getOtherNode(getHost()))/sum);
        	//System.out.println(getProportion(getHost()));
      

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
			//updateCarrierCount(OtherHost);
			//updateMsgDelegation(OtherHost);
			
			//dynamicUpdateDelegation();
				
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
	
	public int receiveMessage(Message m, DTNHost from){
		int recvCheck=super.checkReceiving(m, from);
		if(recvCheck==RCV_OK){
			if(m.getId().startsWith("M")){
				for(Message mi:getMessageCollection()){
					if(mi.getUniqueId()==m.getUniqueId()){
						recvCheck=DENIED_OLD;
						return recvCheck;
					}
				}
			}
			else{
				for(Message mi:getMessageCollection()){
					if(mi.getUniqueId()==m.getUniqueId()){
						super.deleteMessage(mi.getId(), false);
					}
				}
				this.ackedMessageIds.add(m.getId());
			}
		}
		return super.receiveMessage(m, from);
	}
	
	public void update() 
	{
		super.update();	
		
		if (!canStartTransfer() || isTransferring()) 
			return; // nothing to transfer or is currently transferring 
		
		/* try messages that could be delivered to final recipient */
		if (exchangeDeliverableMessages() != null) 
			return;
		
		tryOtherMessages();
	}
	public Tuple<Message, Connection> tryOtherMessages() 
	{
		List<Tuple<Message, Connection>> messages = 
			new ArrayList<Tuple<Message, Connection>>(); 
	
		Collection<Message> msgCollection = getMessageCollection();
		
		/* for all connected hosts collect all messages that have a higher
		   probability of delivery by the other host */
		
			for (Connection con : getConnections()) 
			{
				DTNHost other = con.getOtherNode(getHost());
				EBSprayAndWaitRouter othRouter = (EBSprayAndWaitRouter)other.getRouter();
				for (Message m : msgCollection)
				{
			
					if (othRouter.isTransferring()) 
					{
						continue; // skip hosts that are transferring
					}
					if (othRouter.hasMessage(m.getId())) 
					{
						continue; // skip messages that the other one has
					}
					if ((ackedMessageIds.contains(m.getId()))||(othRouter.ackedMessageIds.contains(m.getId())))
			    		continue;
			    	
					if(m.getTo()==other){
				    	   m.setResponseSize((int) (this.getBufferSize()*0.1));
				       }
					
					Integer nrofCopies = (Integer)m.getProperty(MSG_COUNT_PROPERTY);
					if(nrofCopies>1)
					{
						if(othRouter.Utility.containsKey(m.getTo()))
						{
							if (othRouter.getUtilityFor(m.getTo()) <getUtilityFor(m.getTo())) 
							{
								// the other node has higher probability of delivery
								messages.add(new Tuple<Message, Connection>(m,con));
							}
						}
					}
					
					/*
					else if(nrofCopies==1)
					{
						messages.add(new Tuple<Message, Connection>(m,con));
					}
					*/
					
					
				
					
				}
				if (messages.size() == 0) 
				{
					return null;
				}
				//double sum=getEncounterValue(getHost())+getEncounterValue(con.getOtherNode(getHost()));
	        	//Proportion.put(getHost(),getEncounterValue(con.getOtherNode(getHost()))/sum);
				
			}
			
			 /*
			     //����������ÿ����Ϣ
			        for (Connection con : getConnections()) 
			        {
			        	double sum=getEncounterValue(getHost())+getEncounterValue(con.getOtherNode(getHost()));
			        	Proportion.put(getHost(),getEncounterValue(con.getOtherNode(getHost()))/sum);
						//System.out.println(Proportion.get(getHost()));
			        }
			        */
			  
			    
			// sort the message-connection tuples
			//orders the tuples by their delivery probability by the host on the other side of the connection
			// try to send messages
			Collections.sort(messages, new TupleComparator());
			return tryMessagesForConnected(messages);
	}
	
	private class TupleComparator implements Comparator 
	<Tuple<Message, Connection>> {

	public int compare(Tuple<Message, Connection> tuple1,
			Tuple<Message, Connection> tuple2) {
		// delivery probability of tuple1's message with tuple1's connection
		double p1 = ((EBSprayAndWaitRouter)tuple1.getValue().
				getOtherNode(getHost()).getRouter()).getUtilityFor
				(tuple1.getKey().getTo());
		// -"- tuple2...
		double p2 = ((EBSprayAndWaitRouter)tuple2.getValue().
				getOtherNode(getHost()).getRouter()).getUtilityFor
				(tuple2.getKey().getTo());

		// bigger probability should come first
		if (p2-p1 == 0) {
			/* equal probabilities -> let queue mode decide */
			return compareByQueueMode(tuple1.getKey(), tuple2.getKey());
		}
		else 
		{
			return (int)(p2-p1);
		}
		
	}
}
	
	public boolean createNewMessage(Message msg) {
		makeRoomForNewMessage(msg.getSize());
		if (msg.getId().startsWith("R")){
			msg.setResponseSize(this.getBufferSize()/50);
			msg.setTtl((int) (this.msgTtl/50));
		}
		else{
			msg.setResponseSize(0);
			msg.setTtl(this.msgTtl);
		}
		addToMessages(msg, true);
		msg.addProperty(MSG_COUNT_PROPERTY, new Integer(initialNrofCopies));//����ֶ�
		return true;
	}
	
	public Message messageTransferred(String id, DTNHost from) {
		Message msg = super.messageTransferred(id, from);
		//����1��ȡ����Ϣ��copies
		Integer nrofCopies = (Integer)msg.getProperty(MSG_COUNT_PROPERTY);//ȡֵ
		
		assert nrofCopies != null : "Not a SnW message: " + msg;
	
		if (msg.getTo() == getHost() && msg.getResponseSize() > 0) {
			// generate a response message
			Message res = new Message(this.getHost(),msg.getFrom(), 
					RESPONSE_PREFIX+msg.getId(), msg.getResponseSize());
			this.createNewMessage(res);
			this.getMessage(RESPONSE_PREFIX+msg.getId()).setRequest(msg);
		}
		//����2��������Ϣ��copies
		if (isEncounterBased) 
		{
			if(nrofCopies>1)
			{
				/* in binary S'n'W the receiving node gets ceil(3n/4) copies */
				nrofCopies = (int)Math.ceil(nrofCopies*getProportion(getHost()));
				//msg.updateProperty(MSG_COUNT_PROPERTY, nrofCopies);
				//System.out.println(Proportion.get(getHost()));
			}
			
		}
		else {
			/* in standard S'n'W the receiving node gets only single copy */
			nrofCopies = 1;
		}
		
		msg.updateProperty(MSG_COUNT_PROPERTY, nrofCopies);//����
		
		return msg;
	}
	
	protected void transferDone(Connection con) 
	{
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
		
		/* reduce the amount of copies left */
		else
		{
			nrofCopies = (Integer)msg.getProperty(MSG_COUNT_PROPERTY);
			if (isEncounterBased) 
			{ 
				if(nrofCopies>1)
				{
					nrofCopies = (int)Math.ceil(nrofCopies*(1-getProportion(getHost())));
					msg.updateProperty(MSG_COUNT_PROPERTY, nrofCopies);
				}
				else if (nrofCopies==1) 
				{
					deleteMessage(msgId, false);
				}
			}		
		}
	}
	protected List<Message> getMessagesWithCopiesLeft() {
		List<Message> list = new ArrayList<Message>();
		for (Message m : getMessageCollection()) {
			Integer nrofCopies = (Integer)m.getProperty(MSG_COUNT_PROPERTY);
			assert nrofCopies != null : "SnW message " + m + " didn't have " + 
				"nrof copies property!";
			if (nrofCopies > 1) {
				list.add(m);
			}
		}
		
		return list;
	}
	
    
    public MessageRouter replicate() {
    	EBSprayAndWaitRouter r = new EBSprayAndWaitRouter(this);
		return r;
	}
}