/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A message that is created at a node or passed between nodes.
 */
public class Message implements Comparable<Message> {
	/** Value for infinite TTL of message */
	public static final String TTL_SECONDS_S = "Scenario.ttlSeconds";
	private static boolean ttlAsSeconds = false;//�������
	public static final int INFINITE_TTL = -1;
	private DTNHost from;
	private DTNHost to;
	/** Identifier of the message */
	private String id;
	/** Size of the message (bytes) */
	private int size;
	/** List of nodes this message has passed */
	public List<DTNHost> path; 
	/** Next unique identifier to be given */
	private static int nextUniqueId;
	/** Unique ID of this message */
	private int uniqueId;
	/** The time this message was received */
	private double timeReceived;
	/** The time when this message was created */
	private double timeCreated;
	/** Initial TTL of the message */
	private int initTtl;
	public int adddelegation;
	public double delegation;
	/***************************************/
	/*For Delegation Usage*/
	public double FirstDelegation;
	public double SecondDelegation;
	public double ThirdDelegation;
	
	public double FirstLabel;
	public double SecondLabel;
	public double ThirdLabel;
	
	public double FirstValue;
	public double SecondValue;
	public double ThirdValue;
	
	/*AaR*/
	public Set<DTNHost> MetNode;
	
	/*For Delete Copy at Carrier*/
	public double Delete;
	
	/*For Ticket Distribute*/
	public double LocalTicket;
	public double OtherTicket;
	public double TicketLabel;
	
    /*For Trajactory Based Forwarding*/
	public List<Coord> msgTRA;
	public List<Coord> PassPoint;
	
	/*Anycast Deadline for EV Communication*/
	public double AnycastDeadline;
	
	/*Cellular Network Delivery Count*/
	public boolean CellularDelivery;
	
	/***************************************/
	
	/*my add******************/
	public int copycount;
	/*for gossip*/
	public double receivingrequest;
	/*for total transmission try*/
	public double transmissioncount;
	/*for successful transmission*/
	public double succestransmissioncount;
	/*for fail transmission*/
	public double failcount;
	
	public double localtransmissioncount;
	public double samemessagecount;
    /*for congestion control use*/
	
	
	public double localcopy=0;
	public double othercopy=0;
	
	public double label=0;
	
	public int contentID=0;
	
	/*my add******************/
	
	
	/** if a response to this message is required, this is the size of the 
	 * response message (or 0 if no response is requested) */
	private int responseSize;
	/** if this message is a response message, this is set to the request msg*/
	private Message requestMsg;
	private static int forwardingCounter;
	/** Container for generic message properties. Note that all values
	 * stored in the properties should be immutable because only a shallow
	 * copy of the properties is made when replicating messages */
	public Map<String, Object> properties;
	
	/** Application ID of the application that created the message */
	private String	appID;
	public double drop;

	static {
		reset();
		DTNSim.registerForReset(Message.class.getCanonicalName());

		forwardingCounter = 0;
 
 		Message.nextUniqueId++;
	}
	
	/**
	 * Creates a new Message.
	 * @param from Who the message is (originally) from
	 * @param to Who the message is (originally) to
	 * @param id Message identifier (must be unique for message but
	 * 	will be the same for all replicates of the message)
	 * @param size Size of the message (in bytes)
	 */
	public Message(DTNHost from, DTNHost to, String id, int size) {
		this.from = from;
		this.to = to;
		this.id = id;
		this.size = size;
		this.path = new ArrayList<DTNHost>();
		this.uniqueId = nextUniqueId;
	
		this.timeCreated = SimClock.getTime();
		
		this.timeReceived = this.timeCreated;
		this.initTtl = INFINITE_TTL;
		this.responseSize = 0;
		this.requestMsg = null;
		this.properties = null;
		this.appID = null;
	
		/*For Delegation Usage*/
		this.FirstDelegation=0;
		this.SecondDelegation=0;
		this.ThirdDelegation=0;
		
		this.FirstLabel=0;
		this.SecondLabel=0;
		this.ThirdLabel=0;
		
		this.FirstValue=0;
		this.SecondValue=0;
		this.ThirdValue=0;
		
		/*For Delete Copy at Message Carrier*/
		this.Delete=0;
		
        /*For Ticket Distribution*/
		LocalTicket=0.0;
		OtherTicket=0.0;
		TicketLabel=0.0;
		
	    /*For Trajactory Based Forwarding*/
		this.msgTRA=new ArrayList<Coord>();
		this.PassPoint=new ArrayList<Coord>();
		
		/*For AaR*/
		this.MetNode=new HashSet<DTNHost>();
		
		this.AnycastDeadline=0;
		
		this.CellularDelivery=false;
		
		/************/
		this.copycount =1;
		this.receivingrequest=0;
		this.transmissioncount=0;
		this.succestransmissioncount=0;
		this.failcount=0;
		this.localtransmissioncount=0;
		this.samemessagecount=0;

		this.localcopy=0;
		this.othercopy=0;
		
		this.label=0;
		/************/
		
		Message.nextUniqueId++;
		addNodeOnPath(from);
	}
	
	
	public int getinittl(){
		return this.initTtl;
	}
	/**
	 * Returns the node this message is originally from
	 * @return the node this message is originally from
	 */
	public DTNHost getFrom() {
		return this.from;
	}
	
	 public int getForwardingCounter() {
		                 return this.forwardingCounter;
		        }
		 	/**
		 +	 * Increments the forwardingCounter each time a message is forwarded
		 +	 */
		         public void incrementForwardingCounter() {
		                 this.forwardingCounter++;     
		       }
	/**
	 * Returns the node this message is originally to
	 * @return the node this message is originally to
	 */
	public DTNHost getTo() {
		return this.to;
	}

	/**
	 * Returns the ID of the message
	 * @return The message id
	 */
	public String getId() {
		return this.id;
	}
	
	/**
	 * Returns an ID that is unique per message instance 
	 * (different for replicates too)
	 * @return The unique id
	 */
	public int getUniqueId() {
		return this.uniqueId;
	}
	
	/**
	 * Returns the size of the message (in bytes)
	 * @return the size of the message
	 */
	public int getSize() {
		return this.size;
	}

	/**
	 * Adds a new node on the list of nodes this message has passed
	 * @param node The node to add
	 */
	public void addNodeOnPath(DTNHost node) {
		this.path.add(node);
	}
	
	/**
	 * Returns a list of nodes this message has passed so far
	 * @return The list as vector
	 */
	public List<DTNHost> getHops() {
		return this.path;
	}
	
	/**
	 * Returns the amount of hops this message has passed
	 * @return the amount of hops this message has passed
	 */
	public int getHopCount() {
		return this.path.size() -1;
	}
	
	/** 
	 * Returns the time to live (minutes) of the message or Integer.MAX_VALUE 
	 * if the TTL is infinite. Returned value can be negative if the TTL has
	 * passed already.
	 * @return The TTL (minutes)
	 */
	public int getTtl() {
		if (this.initTtl == INFINITE_TTL) {
			return Integer.MAX_VALUE;
		}
		else {
			return (int)( ((this.initTtl * 60) -
					(SimClock.getTime()-this.timeCreated)) /60.0 );
		}
	}
	
	
	
	
	/**
	 * Sets the initial TTL (time-to-live) for this message. The initial
	 * TTL is the TTL when the original message was created. The current TTL
	 * is calculated based on the time of 
	 * @param ttl The time-to-live to set
	 */
	public void setTtl(int ttl) {
		this.initTtl = ttl;
	}
	
	/**
	 * Sets the time when this message was received.
	 * @param time The time to set
	 */
	public void setReceiveTime(double time) {
		this.timeReceived = time;
	}
	
	/**
	 * Returns the time when this message was received
	 * @return The time
	 */
	public double getReceiveTime() {
		return this.timeReceived;
	}
	
	/**
	 * Returns the time when this message was created
	 * @return the time when this message was created
	 */
	public double getCreationTime() {
		return this.timeCreated;
	}
	
	/**
	 * If this message is a response to a request, sets the request message
	 * @param request The request message
	 */
	public void setRequest(Message request) {
		this.requestMsg = request;
	}
	
	/**
	 * Returns the message this message is response to or null if this is not
	 * a response message
	 * @return the message this message is response to
	 */
	public Message getRequest() {
		return this.requestMsg;
	}
	
	/**
	 * Returns true if this message is a response message
	 * @return true if this message is a response message
	 */
	public boolean isResponse() {
		return this.requestMsg != null;
	}
	
	/**
	 * Sets the requested response message's size. If size == 0, no response
	 * is requested (default)
	 * @param size Size of the response message
	 */
	public void setResponseSize(int size) {
		this.responseSize = size;
	}
	
	/**
	 * Returns the size of the requested response message or 0 if no response
	 * is requested.
	 * @return the size of the requested response message
	 */
	public int getResponseSize() {
		return responseSize;
	}
	
	/**
	 * Returns a string representation of the message
	 * @return a string representation of the message
	 */
	public String toString () {
		return id;
	}

	/**
	 * Deep copies message data from other message. If new fields are
	 * introduced to this class, most likely they should be copied here too
	 * (unless done in constructor).
	 * @param m The message where the data is copied
	 */
	protected void copyFrom(Message m) {
		this.path = new ArrayList<DTNHost>(m.path);
		this.timeCreated = m.timeCreated;
		this.responseSize = m.responseSize;
		this.requestMsg  = m.requestMsg;
		this.initTtl = m.initTtl;
		this.appID = m.appID;
		
		/*For Delete Copy at Carrier*/
		this.Delete=m.Delete;
		
		/*For Delegation Usage*/
		this.FirstDelegation = m.FirstDelegation;
		this.SecondDelegation=m.SecondDelegation;
		this.ThirdDelegation=m.ThirdDelegation;
		
		this.FirstLabel = m.FirstLabel;
		this.SecondLabel=m.SecondLabel;
		this.ThirdLabel=m.ThirdLabel;
		
		this.FirstValue = m.FirstValue;
		this.SecondValue=m.SecondValue;
		this.ThirdValue=m.ThirdValue;
		
        /*For AaR*/
		this.MetNode = new HashSet<DTNHost>(m.MetNode);
		
        /*For Ticket Distribution*/
		this.LocalTicket=m.LocalTicket;
		this.OtherTicket=m.OtherTicket;
		this.TicketLabel=m.TicketLabel;
		
	    /*For Trajactory Based Forwarding*/
		this.msgTRA=new ArrayList<Coord>(m.msgTRA);
		this.PassPoint=new ArrayList<Coord>(m.PassPoint);
		
		this.AnycastDeadline=m.AnycastDeadline;
		
		/**************************/
        this.label=m.label;
		
		this.copycount = m.copycount;
		
		this.succestransmissioncount=m.succestransmissioncount;
		this.failcount=m.failcount;
		this.transmissioncount=m.transmissioncount;
		
		this.samemessagecount=m.samemessagecount;
		
		this.localcopy=m.localcopy;
		this.othercopy=m.othercopy;
		/********************/
		
		if (m.properties != null) {
			Set<String> keys = m.properties.keySet();
			for (String key : keys) {
				updateProperty(key, m.getProperty(key));
			}
		}
	}

	/**
	 * Adds a generic property for this message. The key can be any string but 
	 * it should be such that no other class accidently uses the same value.
	 * The value can be any object but it's good idea to store only immutable
	 * objects because when message is replicated, only a shallow copy of the
	 * properties is made.  
	 * @param key The key which is used to lookup the value
	 * @param value The value to store
	 * @throws SimError if the message already has a value for the given key
	 */
	public void addProperty(String key, Object value) throws SimError {
		if (this.properties != null && this.properties.containsKey(key)) {
			/* check to prevent accidental name space collisions */
			throw new SimError("Message " + this + " already contains value " + 
					"for a key " + key);
		}
		
		this.updateProperty(key, value);
	}
	
	/**
	 * Returns an object that was stored to this message using the given
	 * key. If such object is not found, null is returned.
	 * @param key The key used to lookup the object
	 * @return The stored object or null if it isn't found
	 */
	public Object getProperty(String key) {
		if (this.properties == null) {
			return null;
		}
		return this.properties.get(key);
	}
	
	/**
	 * Updates a value for an existing property. For storing the value first 
	 * time, {@link #addProperty(String, Object)} should be used which
	 * checks for name space clashes.
	 * @param key The key which is used to lookup the value
	 * @param value The new value to store
	 */
	public void updateProperty(String key, Object value) throws SimError {
		if (this.properties == null) {
			/* lazy creation to prevent performance overhead for classes
			   that don't use the property feature  */
			this.properties = new HashMap<String, Object>();
		}		

		this.properties.put(key, value);
	}
	
	/**
	 * Returns a replicate of this message (identical except for the unique id)
	 * @return A replicate of the message
	 */
	public Message replicate() {
		Message m = new Message(from, to, id, size);
		m.copyFrom(this);
		return m;
	}
	
	/**
	 * Compares two messages by their ID (alphabetically).
	 * @see String#compareTo(String)
	 */
	public int compareTo(Message m) {
		return toString().compareTo(m.toString());
	}
	
	/**
	 * Resets all static fields to default values
	 */
	public static void reset() {
		nextUniqueId = 0;
	}

	/**
	 * @return the appID
	 */
	public String getAppID() {
		return appID;
	}

	/**
	 * @param appID the appID to set
	 */
	public void setAppID(String appID) {
		this.appID = appID;
	}
	
	//////////////AaR//////////////////////
    public void updateMetNode(DTNHost host)
    {
	    this.MetNode.add(host);
    }
	
    public double getSizeMetNode(){
	    return MetNode.size();
    }
    ///////////////////////////////////
	
    /*-----------------adaptive epidemic use-------------------*/
	public void updatecopycount (int i)
	{
		this.copycount=i;
	}
		
	
	public double getreplicatecount()
	{
		double ex = 0.4*(copycount-2);
		double prob = 1/(Math.pow(2.7182, ex));
		
		if (this.copycount<=2)
			return 1.0;
		else
			return prob;	
	}
		
	
    /*---------------------------------------*/

    /*--------------getconditionaltransmission probability-----*/
    public double getsuccessprob()
    { double i = this.succestransmissioncount;
      double j = this.transmissioncount;
      double ratio = i/j;
      if(i==0)
    	  return 0;
      else
    	  return ratio;	
    }
    
    public double getfailprob()
    {double i = this.failcount;
     double j = this.transmissioncount;
     double ratio = i/j;
     if(j==0)
    	 return 0;
     else 
    	 return ratio;	
    }
    
    public double getgosippro()
    {double i = this.receivingrequest;
     double j = this.localtransmissioncount;
     double k = j/i;
     
     if((i==0)||(j==0))
    	return 1;
     else
        { if(k>=1)
            return 1;
          else 
    	    return k;
        } 
    }
    
   
    
    public double lifetime()
    {
    	double i = this.getTtl();
    	double j =this.getinittl();
    	return i/j;
    	
    }
    
    
    public double getmessagepriority()
    {
    	double i = getgosippro();
    	double j =this.getfailprob();
    	double k=1-lifetime();
    	
    	double p= 0.2605*i+0.1061*j+0.6334*k;
    	
    	/*
    	if(p>0.5)
    	{System.out.println(i);
    	System.out.println(j);
    	System.out.println(k);
    	System.out.println(p);
    	System.out.println("-----------");
    	}
    	*/
    	
    	return p;
    }
    
   
    
    public double getmessageprobability()
    {
    	double i=this.samemessagecount;
    	double j=this.succestransmissioncount;
    	
    	if(j>=i)
    		return 1;

    	else
    		return j/i;
    }
   
    
}
	

