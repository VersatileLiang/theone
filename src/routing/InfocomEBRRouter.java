package routing;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

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

public class InfocomEBRRouter extends ActiveRouter {
	/** identifier for the initial number of copies setting ({@value})*/ 
	public static final String NROF_COPIES = "nrofCopies";
	/** identifier for the binary-mode setting ({@value})*/ 
	/** SprayAndWait router's settings name space ({@value})*/ 
	public static final String SPRAYANDWAIT_NS = "EBRRouter";
	/** Message property key */
	public static final String MSG_COUNT_PROPERTY = SPRAYANDWAIT_NS + "." +
		"copies";
	
	protected double initialNrofCopies;
	private double updateCWCtime;
	private double CWC;
	private double EV;
	
	public InfocomEBRRouter(Settings s) 
	{
		super(s);
		
		Settings snwSettings = new Settings(SPRAYANDWAIT_NS);
		initialNrofCopies = snwSettings.getInt(NROF_COPIES);
	}
	
	protected InfocomEBRRouter(InfocomEBRRouter r) 
	{
		super(r);
		this.initialNrofCopies = r.initialNrofCopies;
	}
	
	@Override
	public Message messageTransferred(String id, DTNHost from) 
	{
		Message msg = super.messageTransferred(id, from);
		Double nrofCopies = (Double)msg.getProperty(MSG_COUNT_PROPERTY);
		
		assert nrofCopies != null : "Not a SnW message: " + msg;
	 
		if(msg.TicketLabel!=-1)	
		{
			nrofCopies=msg.OtherTicket;
			msg.updateProperty(MSG_COUNT_PROPERTY, nrofCopies);	
			msg.TicketLabel=-1;
		}  
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
	
	@Override 
	public boolean createNewMessage(Message msg) 
	{
		makeRoomForNewMessage(msg.getSize());
		
        msg.TicketLabel=-1;
        msg.LocalTicket=0;
        msg.OtherTicket=0;
		
		msg.setTtl(this.msgTtl);
		msg.addProperty(MSG_COUNT_PROPERTY, new Double(initialNrofCopies));
		addToMessages(msg, true);
		return true;
	}
	
    public void changedConnection(Connection con) 
    {
    	if (con.isUp()) 
		{
    		CWC=CWC+1;
		}
	}
    
    public void updateEV()
    {
	    double timeDiff = (SimClock.getTime() - this.updateCWCtime);
	    
	    if(timeDiff>30)
		{
	    	EV=0.85*CWC+0.15*EV;
	    	CWC=0;
	    	updateCWCtime=SimClock.getTime();
	    }  
    }

	
	@Override
	public void update() 
	{
		super.update();
		
		/*Update EV*/
		updateEV();
		
		if (!canStartTransfer() || isTransferring()) 
		{
			return; // nothing to transfer or is currently transferring 
		}

		/* try messages that could be delivered to final recipient */
		if (exchangeDeliverableMessages() != null) 
		{
			return;
		}
		
		/* create a list of SAWMessages that have copies left to distribute */
		@SuppressWarnings(value = "unchecked")
		List<Message> CopiesLeft = sortByQueueMode(getMessagesWithCopiesLeft());
		
		if (CopiesLeft.size() > 0) 
		{
			/* try to send those messages */
			this.tryMessagesToConnections(CopiesLeft, getConnections());
		}
	}
	
	protected List<Message> getMessagesWithCopiesLeft() 
	{
		List<Message> list = new ArrayList<Message>();

		for (Message m : getMessageCollection())
		{
			for (Connection con:getConnections())
			{
				Double nrofCopies = (Double)m.getProperty(MSG_COUNT_PROPERTY);
				assert nrofCopies != null : "SnW message " + m + " didn't have " + 
				"nrof copies property!";
	
				DTNHost Other = con.getOtherNode(getHost());   
				InfocomEBRRouter OtherRouter = (InfocomEBRRouter)Other.getRouter();
					
				double LocalValue=this.EV;	
				double OtherValue=OtherRouter.EV;
				double Ratio= (nrofCopies*OtherValue)/(LocalValue+OtherValue);
		
				double Tem=Ratio;
				
				/*****For Skip Stationary Destination****/
				if(super.Skip==true)
				{   
					if ((Other.movement.toString().equals("StationaryMovement"))||(Other.movement.toString().equals("CSPlacementMovement")))
					continue; 
				}
				/*****For Skip Stationary Destination****/
	
				if(nrofCopies>1)	
				{
					if((Tem>0)&&((nrofCopies-Tem)!=0))
					{
						m.TicketLabel=1;
						m.OtherTicket=Tem;	
						m.LocalTicket=nrofCopies-Tem;
						
						if((m.LocalTicket==0)||(m.OtherTicket==0))
							System.out.println("ticket is 0");
				     
						list.add(m);
					}
				}
			}
		}
		return list;
	}
	
	@Override
	protected void transferDone(Connection con) 
	{
		Double nrofCopies;
		String msgId = con.getMessage().getId();
		/* get this router's copy of the message */
		Message msg = getMessage(msgId);

		if (msg == null) 
		{ // message has been dropped from the buffer after..
			return; // ..start of transfer -> no need to reduce amount of copies
		}
		
		nrofCopies = (Double)msg.getProperty(MSG_COUNT_PROPERTY);

		if(msg.TicketLabel!=-1)	
		{
			nrofCopies=msg.LocalTicket;
			msg.updateProperty(MSG_COUNT_PROPERTY, nrofCopies);	
			msg.TicketLabel=-1;
		    
			if(nrofCopies==0)
			{
				System.out.println("a copy is deleted due to 0 ticket");
				this.deleteMessage(msgId, false);	
			}
		}
	}

	@Override
	public InfocomEBRRouter replicate() 
	{
		return new InfocomEBRRouter(this);
	}
}
