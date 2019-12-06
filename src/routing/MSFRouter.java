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
/**
 * Energy level-aware variant of Epidemic router.
 */
public class MSFRouter extends ActiveRouter implements ModuleCommunicationListener
{   
	public double msf=0;
	public double lasttime=0;
	public Set<DTNHost> localmet;

	public static final String NROF_COPIES = "nrofCopies";
	public static final String MSFROUTER_NS = "MSFRouter";
	/** Message property key */
	public static final String MSG_COUNT_PROPERTY = MSFROUTER_NS + "." +
		"copies";
	protected int initialNrofCopies;
	
	
	/**
	 * Constructor. Creates a new message router based on the settings in
	 * the given Settings object.
	 * @param s The settings object
	 */
	public MSFRouter(Settings s) 
	{
		super(s);
		Settings VDFSettings=new Settings(MSFROUTER_NS );
		initialNrofCopies=VDFSettings.getInt(NROF_COPIES);
	}

	/**
	 * Copy constructor.
	 * @param r The router prototype where setting values are copied from
	 */
	protected MSFRouter(MSFRouter r) 
	{
		super(r);
		this.localmet=new HashSet<DTNHost>();
		this.initialNrofCopies = r.initialNrofCopies;
	}
	
	public void updatemsf()
	{
		double current=localmet.size();
		double timeDiff = (SimClock.getTime() - lasttime)/500;
		if (timeDiff >1) 
		{
			msf=0.8*msf+0.2*current;	
			this.lasttime = SimClock.getTime();
			localmet.clear();	
		}
		
	}
	
	public boolean createNewMessage(Message msg) 
	{
		makeRoomForNewMessage(msg.getSize());
		msg.setTtl(this.msgTtl);
		addToMessages(msg, true);	
		msg.addProperty(MSG_COUNT_PROPERTY, new Integer(initialNrofCopies));
		return true;
	}
	
	public void update() 
	{
		super.update();
		updatemsf();

		if (!canStartTransfer() || isTransferring()) 
			return; 
		
		if (exchangeDeliverableMessages() != null) 
			return;
		
		tryOtherMessages();
	}

	public  Tuple<Message, Connection> tryOtherMessages() 
	{
		Collection<Message> msgCollection = getMessageCollection();
		List<Tuple<Message, Connection>> highprior = new ArrayList<Tuple<Message, Connection>>(); 

		for(Connection c : getConnections()) 
		{
	    	DTNHost other = c.getOtherNode(getHost());
	        MSFRouter otherRouter = (MSFRouter)other.getRouter();
 
		    for (Message m : msgCollection)	        
		    {  
		    	 if (otherRouter.hasMessage(m.getId())) 
		    		 continue;
		    	 
		    	 else if (otherRouter.isTransferring()) 
		    		 continue;

		    	 Integer nrofCopies = (Integer)m.getProperty(MSG_COUNT_PROPERTY);
		    	 
		    	 if(nrofCopies>1)
		    	 {
		    		 if (msf<otherRouter.msf)
		    			 highprior.add(new Tuple<Message, Connection>(m, c));
		    	 }
		    }
		 }
		         
		 Collections.sort(highprior, new TupleComparator());
		 return tryMessagesForConnected(highprior);
	}
        
	@Override
	public MSFRouter replicate()     
	{
		   return new MSFRouter(this);
	}
	
	public void changedConnection(Connection con) 
	{
		if (con.isUp())   
		{
			DTNHost otherHost = con.getOtherNode(getHost());
			MSFRouter otherRouter = (MSFRouter)otherHost.getRouter();
	
			localmet.add(otherHost);
			otherRouter.localmet.add(getHost());
		}

		else if (!con.isUp())  
		{}
	}

	protected void transferDone(Connection con) 
	{
			
		String msgId = con.getMessage().getId();
			/* get this router's copy of the message */
		Message msg = getMessage(msgId);
		DTNHost other = con.getOtherNode(getHost());
		
		if (msg == null)  // message has been dropped from the buffer after..
				return; // ..start of transfer -> no need to reduce amount of copies

		else 
		{    
			Integer nrofCopies;	
			nrofCopies = (Integer)msg.getProperty(MSG_COUNT_PROPERTY);
				
			if(nrofCopies>1)
			{
				nrofCopies--;
				msg.updateProperty(MSG_COUNT_PROPERTY, nrofCopies);
			}
		}
	}

	public Message messageTransferred(String id, DTNHost from) 	
	{
		Message msg = super.messageTransferred(id, from);
 
		Integer nrofCopies;				
		nrofCopies = (Integer)msg.getProperty(MSG_COUNT_PROPERTY);				
		nrofCopies = 1;		
		msg.updateProperty(MSG_COUNT_PROPERTY, nrofCopies);
		return msg;
	}
		  	
		
	private class TupleComparator implements Comparator <Tuple<Message, Connection>> 	
	{		
		public int compare(Tuple<Message, Connection> tuple1,Tuple<Message, Connection> tuple2)  
		{
			Message message1 = tuple1.getKey();
			Message message2 = tuple2.getKey();

			if (message1.getReceiveTime()<=message2.getReceiveTime())
				  return -1;
			else
				  return 1;
		}
	}


	@Override
	public void moduleValueChanged(String key, Object newValue) {
		// TODO Auto-generated method stub
		
	}
}