/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
//
//EnergyAwareSprayAndWaitRouter
package routing;

/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
import java.util.ArrayList;


import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import core.*;
import core.Connection;
import core.DTNHost;
import core.Message;
import core.Settings;

/**
 * Implementation of Spray and wait router as depicted in 
 * <I>Spray and Wait: An Efficient Routing Scheme for Intermittently
 * Connected Mobile Networks</I> by Thrasyvoulos Spyropoulus et al.
 *
 */
public class EnergyAwareSprayAndWaitRouter extends ActiveRouter implements ModuleCommunicationListener {
	/** identifier for the initial number of copies setting ({@value})*/ 
	public static final String NROF_COPIES = "nrofCopies";//????????
	/** identifier for the binary-mode setting ({@value})*/ 
	//public static final String BINARY_MODE = "binaryMode";//true??????????false?????????
	/** SprayAndWait router's settings name space ({@value})*/ 
	public static final String SPRAYANDWAIT_NS = "EnergyAwareSprayAndWaitRouter";
	/** Message property key *///MSG_COUNT_PROPERTY?EnergyAwareSprayAndWaitRouter.copies
	public static final String MSG_COUNT_PROPERTY = SPRAYANDWAIT_NS + "." +
		"copies";
	
	protected int initialNrofCopies;
	//protected boolean isBinary;
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
	public Map<DTNHost, Double> Energy;
	public Map<DTNHost, Double> Utility;
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
	private ModuleCommunicationBus comBus;
	private static Random rng = null;
	
	public EnergyAwareSprayAndWaitRouter(Settings s) {
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
		Settings snwSettings = new Settings(SPRAYANDWAIT_NS);
		
		initialNrofCopies = snwSettings.getInt(NROF_COPIES);
		//isBinary = snwSettings.getBoolean( BINARY_MODE);
	}
	
	/**
	 * Copy constructor.
	 * @param r The router prototype where setting values are copied from
	 */
	protected EnergyAwareSprayAndWaitRouter(EnergyAwareSprayAndWaitRouter r) {
		super(r);
		this.initialNrofCopies = r.initialNrofCopies;
		//this.isBinary = r.isBinary;
		this.initEnergy = r.initEnergy;
		setEnergy(this.initEnergy);
		this.scanEnergy = r.scanEnergy;
		this.transmitEnergy = r.transmitEnergy;
		this.scanInterval = r.scanInterval;
		this.warmupTime  = r.warmupTime;
		this.comBus = null;
		this.lastScanUpdate = 0;
		this.lastUpdate = 0;
		this.Energy=new HashMap<DTNHost,Double>();
		this.energy=new HashMap<DTNHost,Double>();
		this.Utility=new HashMap<DTNHost,Double>();
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
	
	@Override
	public Message messageTransferred(String id, DTNHost from) {
		Message msg = super.messageTransferred(id, from);
		
		//????1??????????copies
		Integer nrofCopies = (Integer)msg.getProperty(MSG_COUNT_PROPERTY);//??
		
		assert nrofCopies != null : "Not a SnW message: " + msg;
		
		//????2???????????copies
		if (nrofCopies*this.getUtility(from)>1) {
				nrofCopies = (int)Math.floor(nrofCopies*this.getUtility(from));
				System.out.println(nrofCopies);
			/* in binary S'n'W the receiving node gets ceil(n/2) copies */
			
		}
		else {
			/* in standard S'n'W the receiving node gets only single copy */
			nrofCopies = 1;
		}
		
		msg.updateProperty(MSG_COUNT_PROPERTY, nrofCopies);//????
		return msg;
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
	private void updateEnergy(DTNHost peer) {
		// TODO Auto-generated method stub
		EnergyAwareSprayAndWaitRouter othRouter=(EnergyAwareSprayAndWaitRouter) peer.getRouter();
		double value=othRouter.currentEnergy;
		Energy.put(peer, value);
	}
	
    public double getEnergy(DTNHost peer){
    	if(this.Energy.containsKey(peer))
			return this.Energy.get(peer);
		else
			return 0;
    }
	public void changedConnection(Connection con) 	{
		DTNHost OtherHost = con.getOtherNode(getHost());
		updateEnergy(OtherHost);
		double speed1=getHost().getPath()!=null?getHost().getPath().getSpeed():0;
		double speed2=OtherHost.getPath()!=null?OtherHost.getPath().getSpeed():0;
		double value=0;
		if(speed2==0||speed1==0||speed2<speed1)
			value=0.5;
		else
			value=speed2/(speed2+speed1);
		double value2=this.getEnergy(OtherHost)/(this.getEnergy(getHost())+this.getEnergy(OtherHost));
		double utility=1-(0.75*value+0.25*value2);
		updateUtility(OtherHost, utility);
	}
	
	private void updateUtility(DTNHost host, double utility) {
		// TODO Auto-generated method stub
		Utility.put(host, utility);
	}
	
	private double getUtility(DTNHost host) {
		// TODO Auto-generated method stub
		if(Utility.containsKey(host))
			return Utility.get(host);
		else
			return 0;
	}
	
	@Override 
	public boolean createNewMessage(Message msg) {
		makeRoomForNewMessage(msg.getSize());
		msg.setTtl(this.msgTtl);
		msg.addProperty(MSG_COUNT_PROPERTY, new Integer(initialNrofCopies));//??????
		addToMessages(msg, true);
		return true;
	}
	
	@Override
	public void update() {
		super.update();
		reduceSendingAndScanningEnergy();
		if (!canStartTransfer() || isTransferring()) {
			return; // nothing to transfer or is currently transferring 
		}
		/* try messages that could be delivered to final recipient */
		if (exchangeDeliverableMessages() != null) {
			return;
		}
		
		 //???????????งน??????????copies????1?????
		/* create a list of SAWMessages that have copies left to distribute */
		@SuppressWarnings(value = "unchecked")
		List<Message> copiesLeft = sortByQueueMode(getMessagesWithCopiesLeft());
		
		if (copiesLeft.size() > 0) {
			/* try to send those messages */
			this.tryMessagesToConnections(copiesLeft, getConnections());
		}
	}
	
	/**
	 * Creates and returns a list of messages this router is currently
	 * carrying and still has copies left to distribute (nrof copies > 1).
	 * @return A list of messages that have copies left
	 */
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
	
	/**
	 * Called just before a transfer is finalized (by 
	 * {@link ActiveRouter#update()}).
	 * Reduces the number of copies we have left for a message. 
	 * In binary Spray and Wait, sending host is left with floor(n/2) copies,
	 * but in standard mode, nrof copies left is reduced by one. 
	 */
	@Override
	protected void transferDone(Connection con) {
		Integer nrofCopies;
		String msgId = con.getMessage().getId();
		/* get this router's copy of the message */
		Message msg = getMessage(msgId);

		if (msg == null) { // message has been dropped from the buffer after..
			return; // ..start of transfer -> no need to reduce amount of copies
		}
		
		/* reduce the amount of copies left */
		nrofCopies = (Integer)msg.getProperty(MSG_COUNT_PROPERTY);
		if (nrofCopies*this.getUtility(getHost())>1) { 
			if(nrofCopies*this.getUtility(getHost())>1)
				nrofCopies = (int)Math.ceil(nrofCopies*(1-this.getUtility(getHost())));
		}
		else {
			nrofCopies--;
		}
		msg.updateProperty(MSG_COUNT_PROPERTY, nrofCopies);
	}
	
	@Override
	public void moduleValueChanged(String key, Object newValue) {
		this.currentEnergy = (Double)newValue;
	}

	
	@Override
	public String toString() {
		return super.toString() + " energy level = " + this.currentEnergy;
	}	
	@Override
	public EnergyAwareSprayAndWaitRouter replicate() {
		return new EnergyAwareSprayAndWaitRouter(this);
	}
}