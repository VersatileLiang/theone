package routing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import routing.encounteruntility.FriendValue;
import routing.encounteruntility.PeopleRankAlgorithmUtil;



import java.util.Set;


//import routing.TupleComparator.TupleComparator;
import util.Tuple;
import core.Connection;
import core.DTNHost;
import core.Message;
import core.MessageListener;
import core.Settings;
import core.SimClock;

/*
 * MetNode不考虑
 * /*FirstDelegation是阀值
 * FirstLabel是计算是否是第一次相遇
 * FirstValue第一次相遇的值
 * */

 public class EncounterUtilityRankRouter extends ActiveRouter 
{
	private PeopleRankAlgorithmUtil peopleRankAlgorithn;
	/** identifier for the initial number of copies setting ({@value})*/ 
	public static final String NROF_COPIES = "nrofCopies";//消息的份数
	/** identifier for the rank-mode setting ({@value})*/ 
	public static final String RANK_MODE = "RankMode";//排名模式进行排名
	
	public static final String ENCOUNTERUTILITYRANKROUTER_NS = "EncounterUtilityRankRouter";
	/** Message property key */
	public static final String MSG_COUNT_PROPERTY = ENCOUNTERUTILITYRANKROUTER_NS + "." +
		"copies";
	
	public Map<DTNHost, Double> EncounterTime;//相遇时间
	public Map<DTNHost, Double> LastEncounterTime;
	public Map<DTNHost, Double> EncounterDuration;//相遇持续时间
	public Map<DTNHost, Double> EncounterCount;//相遇次数
	public Map<DTNHost, Double> Utility;//效用值
	public Map<DTNHost, Double> PreviousValue;
	
	public Set<String> ackedMessageIds;//缓存控制
	
	protected double initialNrofCopies;
    protected boolean isRankMode;
    public EncounterUtilityRankRouter(Settings s)
    {
    	
    	super(s);
    	Settings VDFSettings=new Settings(ENCOUNTERUTILITYRANKROUTER_NS );
    	initialNrofCopies=VDFSettings.getInt(NROF_COPIES);
    	isRankMode=VDFSettings.getBoolean( RANK_MODE);
    }
    protected EncounterUtilityRankRouter(EncounterUtilityRankRouter r)
    {
    	super(r);
    	initPreds();
    	this.initialNrofCopies = r.initialNrofCopies;
    	this.isRankMode = r.isRankMode;
    }
    public void init(DTNHost host, List<MessageListener> mListeners)
	{
		super.init(host,mListeners);
		
		int rank = 0;
		Map<Integer, Double> neighor = new TreeMap<Integer, Double>();
		Map<Integer, Double> friends = new LinkedHashMap<Integer, Double>();		
		List<FriendValue> myNeighborFriends = new ArrayList<FriendValue>();
		
		peopleRankAlgorithn = new PeopleRankAlgorithmUtil(rank, neighor, friends,myNeighborFriends);
	}
    public PeopleRankAlgorithmUtil getPeopleRankAlgorithn() 
	{
		return peopleRankAlgorithn;
	}
	/*获得排名*/
	public double retrieveRank()
	{
		return peopleRankAlgorithn.getRank();
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
	public double getUtilityFor(DTNHost host)
	{
		if(Utility.containsKey(host))
		{
			return Utility.get(host);
		}
		else 
			return 0;
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
		EncounterUtilityRankRouter OtherRouter = (EncounterUtilityRankRouter)MRouter;

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
	public  void updateCarrierCount(DTNHost Host)
	{
		Collection<Message> C1 = this.getMessageCollection();
		
		EncounterUtilityRankRouter OtherRouter = (EncounterUtilityRankRouter)Host.getRouter();
		Collection<Message> C2 = OtherRouter.getMessageCollection();
		
		for (Message m1:C1)
		{
			for (Message m2:C2)
			{
				if(m1.getId()==m2.getId())
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
	public void changedConnection(Connection con) 	
	{
		if (con.isUp())    
		{
			DTNHost OtherHost = con.getOtherNode(getHost());	
			EncounterUtilityRankRouter OtherRouter = (EncounterUtilityRankRouter)OtherHost.getRouter();
						
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
			
			this.peopleRankAlgorithn.update(this.getHost(), con.getOtherNode(this.getHost()));//updaterank
			
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
		Collection<Message> msgCollection = getMessageCollection();
		List<Tuple<Message, Connection>> messages = 
			new ArrayList<Tuple<Message, Connection>>();
	
			for (Connection con : getConnections()) 
			{
				DTNHost other = con.getOtherNode(getHost());
				EncounterUtilityRankRouter othRouter = (EncounterUtilityRankRouter)other.getRouter();
				double weightA = this.retrieveRank();
				double weightB=othRouter.retrieveRank();

				for (Message m : msgCollection)
				{
					double ExpectDelay=m.getinittl()*60.0-(SimClock.getTime()-m.getCreationTime()); 
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
					Double nrofCopies = (Double)m.getProperty(MSG_COUNT_PROPERTY);
					assert nrofCopies != null : "SnW message " + m + " didn't have " + 
							"nrof copies property!";
			    	double LocalValue=weightA;
			    	double OtherValue=weightB;
			    	double Ratio= (double)(nrofCopies*OtherValue)/(LocalValue+OtherValue);
			    	double Tem=Ratio;
					
					if(nrofCopies>1)
					{
						if((Tem>0)&&((nrofCopies-Tem)!=0))
						{
							m.TicketLabel=1.0;
							m.OtherTicket=Tem;	
							m.LocalTicket=nrofCopies-Tem;
							if((m.LocalTicket==0)||(m.OtherTicket==0))
							System.out.println("ticket is 0");
						}
						
						if(othRouter.Utility.containsKey(m.getTo()))//如果j到过目的节点d
						{
							if(this.Utility.containsKey(m.getTo()))//如果i到过目的节点d
							{
								if (othRouter.getUtilityFor(m.getTo()) <getUtilityFor(m.getTo())) 
								{
									// the other node has higher probability of delivery
									messages.add(new Tuple<Message, Connection>(m,con));
								}
								else if ((getUtilityFor(m.getTo())>ExpectDelay))
								{
								messages.add(new Tuple<Message, Connection>(m,con));
								}
							}
							else//如果没有
							{
								messages.add(new Tuple<Message, Connection>(m,con));	
							}
						}
						
						else//如果j没到过目的节点d，根据排名进行转发
						{
							if(weightA<weightB)
							{
								messages.add(new Tuple<Message, Connection>(m,con));
							}
						}
						
					}
					
					else if(nrofCopies==1)
					{
						if(othRouter.Utility.containsKey(m.getTo()))
						{
							if(this.Utility.containsKey(m.getTo()))
							{
								if(getUtilityFor(m.getTo())>othRouter.Utility.get(m.getTo()))
								{
									messages.add(new Tuple<Message, Connection>(m,con));
								}
							}
							else
							{
								messages.add(new Tuple<Message, Connection>(m,con));
							}
						}
					}
				}
				if (messages.size() == 0) 
				{
					return null;
				}
				if(tryMessagesForConnected(messages)!=null)//只处理一个消息的传送，而不是处理Tuple中的所有消息，因为一有消息发送，信道就被占用了。    
					return tryMessagesForConnected(messages);	
			}
			// sort the message-connection tuples
			//orders the tuples by their delivery probability by the host on the other side of the connection
			// try to send messages
			Collections.sort(messages, new TupleComparator());
			if(messages.size()==0)
			{
				return null;
			}
			return tryMessagesForConnected(messages);
	}
	
	private class TupleComparator implements Comparator 
	<Tuple<Message, Connection>> {

	public int compare(Tuple<Message, Connection> tuple1,
			Tuple<Message, Connection> tuple2) {
		// delivery probability of tuple1's message with tuple1's connection
		double p1 = ((EncounterUtilityRankRouter)tuple1.getValue().
				getOtherNode(getHost()).getRouter()).getUtilityFor
				(tuple1.getKey().getTo());
		// -"- tuple2...
		double p2 = ((EncounterUtilityRankRouter)tuple2.getValue().
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
		
		msg.TicketLabel=-1;
	    msg.LocalTicket=0;
	    msg.OtherTicket=0 ;
	    
		msg.setTtl(this.msgTtl);
		addToMessages(msg, true);
		msg.addProperty(MSG_COUNT_PROPERTY, new Double(initialNrofCopies));//添加字段
		return true;
	}
	//根据rank进行转发
	public Message messageTransferred(String id, DTNHost from) {
		Message msg = super.messageTransferred(id, from);
		
		//步骤1：取得消息的copies
		Double nrofCopies = (Double)msg.getProperty(MSG_COUNT_PROPERTY);//取值
		
		assert nrofCopies != null : "Not a SnW message: " + msg;
		
		//步骤2：更新消息的copies
		if (isRankMode) 
		{
			if(msg.TicketLabel!=-1)	
			{
				EncounterUtilityRankRouter routerB=(EncounterUtilityRankRouter)from.getRouter();
				double weightA = this.retrieveRank();
				double weightB = routerB.retrieveRank();
				if(weightA>weightB)
				{
					nrofCopies=(Double)msg.OtherTicket;
					msg.updateProperty(MSG_COUNT_PROPERTY, nrofCopies);	
					msg.TicketLabel=-1.0;
				}
				else
				{
					nrofCopies=(Double)msg.LocalTicket;
					msg.updateProperty(MSG_COUNT_PROPERTY, nrofCopies);	
					msg.TicketLabel=-1.0;
				}
			}
			
		}
		
		msg.updateProperty(MSG_COUNT_PROPERTY, nrofCopies);//更新

		return msg;
	}
	protected void transferAborted(Connection con) 
	{ 
		String msgId = con.getMessage().getId();
		Message msg = getMessage(msgId);

		if (msg == null)  // message has been dropped from the buffer after..
			return; // ..start of transfer -> no need to reduce amount of copies

		this.getMessage(msg.getId()).TicketLabel=-1;
	}
	protected void transferDone(Connection con) 
	{
		Double nrofCopies;
		String msgId = con.getMessage().getId();
		/* get this router's copy of the message */
		Message msg = getMessage(msgId);
		DTNHost other=con.getOtherNode(this.getHost());
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
			nrofCopies = (Double)msg.getProperty(MSG_COUNT_PROPERTY);
			if (isRankMode) 
			{ 
				
				if(msg.TicketLabel!=-1)	
				{
					EncounterUtilityRankRouter routerB=(EncounterUtilityRankRouter)other.getRouter();
					double weightA = this.retrieveRank();
					double weightB = routerB.retrieveRank();
					if(weightA>weightB)
					{
						nrofCopies=(Double)msg.LocalTicket;
						msg.updateProperty(MSG_COUNT_PROPERTY, nrofCopies);	
						msg.TicketLabel=-1.0;
					}
					else
					{
						nrofCopies=(Double)msg.OtherTicket;
						msg.updateProperty(MSG_COUNT_PROPERTY, nrofCopies);	
						msg.TicketLabel=-1.0;
					}
				}
					
				else if (nrofCopies==0) 
				{
					deleteMessage(msgId, false);
				}
			}
		
		}

	}
    public MessageRouter replicate() {
    	EncounterUtilityRankRouter r = new EncounterUtilityRankRouter(this);
		return r;
	}
}


