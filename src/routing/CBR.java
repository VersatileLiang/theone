/* CBR router
 * CBR.java
 *
 * @Author Sujata Pal
 *
 * Created on August 02, 2014
 * Paper name: "Contact-Based Routing in DTNs" published in
 * ACM IMCOM 2015
 */
 
package routing;

import util.Tuple;

import java.util.*;
import core.*;


public class CBR extends ActiveRouter implements ModuleCommunicationListener{
   
   
    protected Map<Integer, ArrayList<Integer>> nodesInf;
    protected ArrayList<Integer> nodeConnections;
    private Set<String> ackedMessageIds;
    private Map<DTNHost, Set<String>> sentMessages;
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
	private ModuleCommunicationBus comBus;
	private static Random rng = null;
    /**
     * Constructor. Creates a new message router based on the settings in
     * the given Settings object.
     * @param s The settings object
     */
    public CBR(Settings s) {
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
        init();
       
    }
   
    /**
     * Copy constructor.
     * @param r The router prototype where setting values are copied from
     */
    protected CBR(CBR r) {
        super(r);
        this.ackedMessageIds = new HashSet<String>();
        this.sentMessages = new HashMap<DTNHost, Set<String>>();
        this.initEnergy = r.initEnergy;
		setEnergy(this.initEnergy);
		this.scanEnergy = r.scanEnergy;
		this.transmitEnergy = r.transmitEnergy;
		this.scanInterval = r.scanInterval;
		this.warmupTime  = r.warmupTime;
		this.comBus = null;
		this.lastScanUpdate = 0;
		this.lastUpdate = 0;
        init();
       
    }
   
    private void init() {

        this.nodesInf = new TreeMap<Integer, ArrayList<Integer>>();
        this.nodeConnections = new ArrayList<Integer> ();
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
    public void changedConnection(Connection con) {
        if (con.isUp()) {

                DTNHost self = this.getHost();
                DTNHost otherNode = con.getOtherNode(self);
                CBR otherRouter=(CBR) otherNode.getRouter();
                this.ackedMessageIds.addAll(otherRouter.ackedMessageIds);
				otherRouter.ackedMessageIds.addAll(this.ackedMessageIds);
				deleteAckedMessages();
				otherRouter.deleteAckedMessages();

                if (! this.nodesInf.containsKey(otherNode.getAddress())) {
                    ArrayList<Integer> items = new ArrayList<Integer>();
                    items.add(1);
                   
                    this.nodesInf.put(otherNode.getAddress(), items);
                }

                if (this.nodesInf.containsKey(otherNode.getAddress())) {     
                    ArrayList<Integer> items = this.nodesInf.get(otherNode.getAddress());

                    items.set(0, items.get(0) + 1);
                                   
                    this.nodesInf.put(otherNode.getAddress(), items);
                }
        }
    }

     private void deleteAckedMessages() {
 		for (String id : this.ackedMessageIds) {
 			if (this.hasMessage(id) && !isSending(id)) {
 				this.deleteMessage(id, false);
 			}
 		}
 	}
    
     @Override
 	protected void transferDone(Connection con) {
 		Message m = con.getMessage();
 		String id = m.getId();
 		DTNHost recipient = con.getOtherNode(getHost());
 		Set<String> sentMsgIds = this.sentMessages.get(recipient);
 		
 		/* was the message delivered to the final recipient? */
 		if (m.getTo() == recipient) { 
 			this.ackedMessageIds.add(m.getId()); // yes, add to ACKed messages
 			this.deleteMessage(m.getId(), false); // delete from buffer
 		}
 		
 		/* update the map of where each message is already sent */
 		if (sentMsgIds == null) {
 			sentMsgIds = new HashSet<String>();
 			this.sentMessages.put(recipient, sentMsgIds);
 		}		
 		sentMsgIds.add(id);
 	}
     
    @Override
    protected int checkReceiving(Message m, DTNHost from) {
        int recvCheck = super.checkReceiving(m, from);
        if (this.currentEnergy < 0) {
			return DENIED_UNSPECIFIED;
		}
        if (recvCheck == RCV_OK) {
            /* don't accept a message that has already traversed this node */
            if (m.getHops().contains(getHost())) {
                recvCheck = DENIED_OLD;
            }
        }
       
        return recvCheck;
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
    public void update() {
        super.update();
        reduceSendingAndScanningEnergy();

        //if(SimClock.getIntTime() == 43200)
            //System.out.println("[" + this.getHost() + "]  list: " + this.nodesInf + "\n");
        if (isTransferring() || !canStartTransfer()) {
            return;
        }
       
        if (exchangeDeliverableMessages() != null) {
            return;
        }
        tryAllMessagesToAllConnections();
    }
    
	@Override
    protected Connection tryMessagesToConnections(List<Message> messages,
            List<Connection> connections) {

        for (Message m : messages) {
           
            DTNHost msgDst = m.getTo();
            DTNHost self = this.getHost();
            
            int maxEnc = 0;
            DTNHost maxEncHost=null;
            Connection conEnc = connections.get(0);
            int k=0;
            for (int i=0, n=connections.size(); i<n; i++) {
                Connection con = connections.get(i);
                DTNHost otherNode = con.getOtherNode(self);
                Set<String> sentMsgIds = this.sentMessages.get(otherNode);
                CBR otherRouter = (CBR) otherNode.getRouter();
              
                if (sentMsgIds != null && sentMsgIds.contains(m.getId())) {
					continue;
				}    
                //if other end of the connection contain (meet with the) destination node of the current msg
                if (otherRouter.nodesInf.containsKey(msgDst.getAddress())) {
                    ArrayList<Integer> items = otherRouter.nodesInf.get(msgDst.getAddress());                   
                   
                    int encounter = items.get(0);
                    if (encounter > maxEnc)
                    {
                        maxEnc = encounter;
                        maxEncHost = otherNode;
                        conEnc = con;
                    }
                }

                Random r = new Random();
                k = r.nextInt(n);
               
            }
            if(maxEnc == 0)
            {   
                conEnc = connections.get(k);
            }
            int retVal = startTransfer(m, conEnc);
            if (retVal == RCV_OK) {
                return conEnc;    // accepted a message, don't try others
            }
            else if (retVal > 0) {
                return null; // should try later -> don't bother trying others
            }
        }
        return null;
    }
    
    public void moduleValueChanged(String key, Object newValue) {
		this.currentEnergy = (Double)newValue;
	}

	
	@Override
	public String toString() {
		return super.toString() + " energy level = " + this.currentEnergy;
	}
	
    @Override
    public CBR replicate() {
        return new CBR(this);
    }
}