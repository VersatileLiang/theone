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
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.Map.Entry;

import core.*;
import util.Tuple;

public class EBSRRouter extends ActiveRouter implements ModuleCommunicationListener
{   
	public Map<DTNHost, Double> EncounterTime;
	public Map<DTNHost, Double> LastEncounterTime;
	public Map<DTNHost, Double> EncounterDuration;
	public Map<DTNHost, Double> EncounterCount;
	public Map<DTNHost, Double> Utility;
	public Map<DTNHost, Double> newUtility;
	public Map<DTNHost, Double> PreviousValue;
	public Set<String> ackedMessageIds;
	
	public static final String HESSPRouter_NS = "EBSRRouter";
	public static final String NROF_COPIES = "nrofCopies";;
	/** Message property key */
	public static final String MSG_COUNT_PROPERTY = HESSPRouter_NS + "." + "copies";
	protected int initialNrofCopies;
	
	public EBSRRouter(Settings s) 
	{
		super(s);
		Settings VDFSettings = new Settings (HESSPRouter_NS);
		initialNrofCopies = VDFSettings.getInt(NROF_COPIES);
	}
	
	protected EBSRRouter(EBSRRouter r) 
	{
		super(r);
		initPreds();
		this.initialNrofCopies = r.initialNrofCopies;
	}
	/*FirstDelegation «∑ß÷µ*/
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
		
		/*For Probability Spray*/
		msg.Delete=-1;
	
		addToMessages(msg, true);
		msg.addProperty(MSG_COUNT_PROPERTY, new Integer(initialNrofCopies));
		return true;
	}

	public  void updateCarrierCount(DTNHost Host)
	{
		Collection<Message> C1 = this.getMessageCollection();
		
		EBSRRouter OtherRouter = (EBSRRouter)Host.getRouter();
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
	
	@Override
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
			
	public  Tuple<Message, Connection> tryOtherMessages() 
	{	
		Collection<Message> msgCollection = getMessageCollection();
		
		List<Tuple<Message, Connection>> Approach = new ArrayList<Tuple<Message, Connection>>(); 	
		List<Tuple<Message, Connection>> Desin = new ArrayList<Tuple<Message, Connection>>(); 

		for(Connection c : getConnections())
		{
			DTNHost Other = c.getOtherNode(getHost());
	        EBSRRouter OtherRouter = (EBSRRouter)Other.getRouter();
	        
		    for (Message m : msgCollection)		        
		    {   
		    	double ExpectDelay=m.getinittl()*60-(SimClock.getTime()-m.getCreationTime());  

		    	if (OtherRouter.hasMessage(m.getId())) 
		    		continue;

		    	if (OtherRouter.isTransferring()) 
		    		continue; 
		    	
		    	if ((ackedMessageIds.contains(m.getId()))||(OtherRouter.ackedMessageIds.contains(m.getId())))
		    		continue;
		    	/*
		    	else if(super.Skip==true)
				{
					if (Other.name.contains("DES"))
						continue; 
				}
				*/
		    	
		    	Integer nrofCopies = (Integer)m.getProperty(MSG_COUNT_PROPERTY);
		    	
		    	if(nrofCopies>1)
		    	{	 	
		    		if(OtherRouter.Utility.containsKey(m.getTo()))
		    		{
		    			if(m.FirstDelegation>OtherRouter.Utility.get(m.getTo()))
		    			{   
		    				m.FirstLabel=1;   		
		    				m.FirstValue=OtherRouter.Utility.get(m.getTo());		
		    				Approach.add(new Tuple<Message, Connection>(m, c));
		    			}
	
		    			else if (m.FirstDelegation>ExpectDelay)
		    			{
		    				m.FirstLabel=1;   		
		    				m.FirstValue=m.FirstDelegation;	
		    				Approach.add(new Tuple<Message, Connection>(m, c));	 
		    			}
		    		}

		    		else
		    		{
		    			double Copy=0;
		    			
		    			if(initialNrofCopies<=m.MetNode.size())
		    				Copy=initialNrofCopies;
		    			else
		    				Copy=m.MetNode.size();
		    			
		    			double V =1-ExpectDelay/(m.getinittl()*60); 
			    		double P=Math.pow(V, Copy);
			    		double Ran = Math.random();
		    				    		
			    		if(P>Ran)
			    		{
			    			m.Delete=1;
			    			Desin.add(new Tuple<Message, Connection>(m, c));
			    		}
		    		}
		    	}
	    	
		    	else if(nrofCopies==1)
		    	{	 
		    		if(OtherRouter.Utility.containsKey(m.getTo()))
		    		{
		    			if(m.FirstDelegation>OtherRouter.Utility.get(m.getTo()))
		    			{   
		    				m.FirstLabel=1;   		
		    				m.FirstValue=OtherRouter.Utility.get(m.getTo());		
		    				Approach.add(new Tuple<Message, Connection>(m, c));
		    			}	
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

	public class HighDropComparator implements Comparator<Message> 
	{
		public   HighDropComparator()
		{}
		
		public int compare(Message Msg1, Message Msg2) 
		{
			double Priority1=0;
			double Priority2=0;
			
			Integer Copies1;
			Integer Copies2;
			Copies1 = (Integer)Msg1.getProperty(MSG_COUNT_PROPERTY);
			Copies2 = (Integer)Msg2.getProperty(MSG_COUNT_PROPERTY);

			double V1=1-((Msg1.getTtl()*60-Msg1.FirstDelegation)/(Msg1.getTtl()*60));	
			double V2=1-((Msg2.getTtl()*60-Msg2.FirstDelegation)/(Msg2.getTtl()*60));

			Priority1= 1-Math.pow(V1,Copies1);
			Priority2= 1-Math.pow(V2,Copies2);
	
			if(Priority1>Priority2)
				return -1;
			else if (Priority1==Priority2)
				return -1;
			else 
				return 1;
			//return (int)(Priority2-Priority1);
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
		
		if(HighBin.size()>1)
			Collections.sort(HighBin,new HighDropComparator());
		 
		if(LowBin.size()!=0)
			return LowBin.get(LowBin.size()-1);
		else
			return HighBin.get(HighBin.size()-1);
	}

	@Override
	public EBSRRouter replicate()  
	{
		   return new EBSRRouter(this);
	}

	public  void updateMsgDelegation(DTNHost Host)	
	{	
		Collection<Message> C1 = this.getMessageCollection();
		EBSRRouter OtherRouter = (EBSRRouter)Host.getRouter();
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
			EBSRRouter OtherRouter = (EBSRRouter)OtherHost.getRouter();
						
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

	public class LowDropComparator implements Comparator<Message> 
	{	
		public   LowDropComparator()
		{}
			
		public int compare(Message Msg1, Message Msg2) 	
		{	
			double Priority1=0;
			double Priority2=0;
				
			Integer Copies1;
			Integer Copies2;
				
			Copies1 = (Integer)Msg1.getProperty(MSG_COUNT_PROPERTY);
			Copies2 = (Integer)Msg2.getProperty(MSG_COUNT_PROPERTY);
	
			double V1=1-(Msg1.getTtl()/Msg1.getinittl());	
			double V2=1-(Msg2.getTtl()/Msg2.getinittl());

			Priority1= 1-Math.pow(V1,Copies1);	
			Priority2= 1-Math.pow(V2,Copies2);			
				
			return (int)(Priority2-Priority1);	
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
		EBSRRouter OtherRouter = (EBSRRouter)MRouter;

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
		//this.newUtility=new HashMap<DTNHost, Double>();
		this.PreviousValue=new HashMap<DTNHost, Double>();
	}
				
	@Override
	protected void transferDone(Connection con) 
	{	
		String msgId = con.getMessage().getId();

		Message msg = getMessage(msgId);
		
		DTNHost other=con.getOtherNode(this.getHost());
			
		if (msg == null)  // message has been dropped from the buffer after..
			return; // ..start of transfer -> no need to reduce amount of copies
				
		if (msg.getTo() == con.getOtherNode(getHost())) 
		{ 
			this.ackedMessageIds.add(msg.getId()); // yes, add to ACKed messages	
			this.deleteMessage(msg.getId(), false); // delete from buffer
		}
  
		else 	
		{	
			if(msg.FirstLabel==1)	
			{     
				msg.FirstDelegation=msg.FirstValue;	       
				msg.FirstLabel=-1;
			         
				Integer nrofCopies;    
				nrofCopies = (Integer)msg.getProperty(MSG_COUNT_PROPERTY);
				
				if(nrofCopies>1)    
				{ 
					nrofCopies = nrofCopies/2; 
					msg.updateProperty(MSG_COUNT_PROPERTY, nrofCopies);
					msg.MetNode.add(other);
				}
   
				else if (nrofCopies==1) 
				{
					deleteMessage(msgId, false);
				}
			}
    
			else if(msg.Delete==1)		
			{
				Integer nrofCopies;	
				nrofCopies = (Integer)msg.getProperty(MSG_COUNT_PROPERTY);
				msg.Delete=-1;

				if(nrofCopies>1)
				{	
					nrofCopies = nrofCopies/2;
					msg.updateProperty(MSG_COUNT_PROPERTY, nrofCopies);
					msg.MetNode.add(other);
				}
			}
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
		this.getMessage(msg.getId()).Delete=-1;
	}
	
	@Override
	public Message messageTransferred(String id, DTNHost from) 
	{
		Message msg = super.messageTransferred(id, from);
			
		Integer nrofCopies;	
		nrofCopies = (Integer)msg.getProperty(MSG_COUNT_PROPERTY);
		
		if(msg.FirstLabel==1)
		{
			msg.FirstDelegation=msg.FirstValue;	
			msg.FirstLabel=-1;
			
			
			if(nrofCopies>1)	
			{
				nrofCopies = (int)Math.ceil(nrofCopies/2.0);	
				msg.updateProperty(MSG_COUNT_PROPERTY, nrofCopies);
				msg.MetNode.add(this.getHost());
			}
		}

		else if (msg.Delete==1)
		{
			msg.Delete=-1;
			
			if(nrofCopies>1)	  	
			{	
				nrofCopies = (int)Math.ceil(nrofCopies/2.0);	
				msg.updateProperty(MSG_COUNT_PROPERTY, nrofCopies);
				msg.MetNode.add(this.getHost());
			}
		}	
		
		return msg;
	}

	public class LowTupleComparator implements Comparator <Tuple<Message, Connection>> 
	{
		public int compare(Tuple<Message, Connection> Tuple1,Tuple<Message, Connection> Tuple2) 
		{	
			Message Msg1=Tuple1.getKey();  	
			Message Msg2=Tuple2.getKey();
				
			Integer Copies1;
			Integer Copies2;
				
			Copies1 = (Integer)Msg1.getProperty(MSG_COUNT_PROPERTY);	
			Copies2 = (Integer)Msg2.getProperty(MSG_COUNT_PROPERTY);
	
			double V1=1-(Msg1.getTtl()/Msg1.getinittl());	
			double V2=1-(Msg2.getTtl()/Msg2.getinittl());

			double Priority1= 1-Math.pow(V1,Copies1);
			double Priority2= 1-Math.pow(V2,Copies2);
	
			return (int)(Priority2-Priority1);
		}
	}

	public class HighTupleComparator implements Comparator <Tuple<Message, Connection>> 	
	{	
		public int compare(Tuple<Message, Connection> Tuple1,Tuple<Message, Connection> Tuple2)   
		{	
			Message Msg1=Tuple1.getKey();  	
			Message Msg2=Tuple2.getKey();	
				
			Integer Copies1;	
			Integer Copies2;

			Copies1 = (Integer)Msg1.getProperty(MSG_COUNT_PROPERTY);	
			Copies2 = (Integer)Msg2.getProperty(MSG_COUNT_PROPERTY);
	
			double V1=1-((Msg1.getTtl()*60-Msg1.FirstDelegation)/(Msg1.getTtl()*60));	
			double V2=1-((Msg2.getTtl()*60-Msg2.FirstDelegation)/(Msg2.getTtl()*60));

			double Priority1= 1-Math.pow(V1,Copies1);	
			double Priority2= 1-Math.pow(V2,Copies2);
		
			return (int)(Priority2-Priority1);
		}
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
	public void moduleValueChanged(String key, Object newValue) {
		// TODO Auto-generated method stub
		
	}	
}