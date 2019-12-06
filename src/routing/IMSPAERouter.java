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
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.Vector;

import util.Tuple;
import core.Connection;
import core.DTNHost;
import core.Message;
import core.MessageListener;
import core.ModuleCommunicationBus;
import core.ModuleCommunicationListener;
import core.NetworkInterface;
import core.Settings;
import core.SettingsError;
import core.SimClock;
import core.SimScenario;
import movement.CommunityBasedMovement;
import movement.MovementModel;
import routing.encounteruntility.FriendValue;
import routing.encounteruntility.PeopleRankAlgorithmUtil;
import routing.util.RoutingInfo;

 public class IMSPAERouter extends ActiveRouter implements ModuleCommunicationListener{

	 private PeopleRankAlgorithmUtil peopleRankAlgorithn;
		
		public PeopleRankAlgorithmUtil getPeopleRankAlgorithn() 
		{
			return peopleRankAlgorithn;
		}
	/** identifier for the initial number of copies setting ({@value})*/ 
	public static final String NROF_COPIES = "nrofCopies";//锟斤拷息锟侥凤拷锟斤拷
	public static final String ENCOUNTERBASED_MODE = "EncounterBasedMode";
	/** SprayAndWait router's settings name space ({@value})*/ 
	public static final String EBSPRAYANDWAIT_NS = "IMSPAERouter";
	/** Message property key *///MSG_COUNT_PROPERTY为SprayAndWaitRouter.copies
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
	public Map<DTNHost,Double>  LastAgeUpdate;
	public Map<DTNHost,Double>  Copies;
	public static final String  SECONDS_IN_UNIT_S ="secondsInTimeUnit";
	private Map<DTNHost,Double>Possion;
	private Map<DTNHost,Double>IrlPreds;
	private Map<DTNHost,Double>LastBuffer;
	private Map<DTNHost,Double>Resource;
	/** last delivery predictability update (sim)time */
	private double betweeness = 0;
	private Vector encounterHost;
	private int dimension;
	/** host(s) that are met recently in the neibor, for temporal use */
	private Vector encounterHostInNeighbor;
	/** host(s) that are Indirect met in neighbor */
	private Vector indirectEncounterHost;
//	private Map<DTNHost, Double> utility;
	private int [][] newAdjacencyMatrix;
	private int [][] oldAdjacencyMatrix;
	private int [][] newIndirectMatrix;
	private int [][] oldIndirectMatrix;
	private double alpha=0.5;
	private double beta=0.5;
	private int secondsInTimeUnit;//锟斤拷锟斤拷锟斤拷锟斤拷锟揭伙拷锟�(age)
	public Map<DTNHost,Double> EncounterValue;
	//private static double CurrentWindowCounterupdate;
	public static final double ALFA = 0.85;
	//private double lastAgeUpdate;
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
    private Map<DTNHost,Double>Energy;
    private Map<DTNHost,Double>Encounter;
    private Map<DTNHost,Double>LastEnergy;
    private Map<DTNHost,Double>Packet;
	public Set<String> ackedMessageIds;
	protected int initialNrofCopies;
    protected boolean isEncounterBased;
	private double lastAgeUpdate;
	private double similarity = 0;
	protected boolean dropMsgBeingSent = true;
	
    public IMSPAERouter(Settings s)
    {
    	super(s);
        this.initEnergy = s.getCsvDoubles(INIT_ENERGY_S);
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

    	Settings ebsnwSettings=new Settings(EBSPRAYANDWAIT_NS );
    	initialNrofCopies=ebsnwSettings.getInt(NROF_COPIES);
    	isEncounterBased=ebsnwSettings.getBoolean( ENCOUNTERBASED_MODE);
    	secondsInTimeUnit=ebsnwSettings.getInt(SECONDS_IN_UNIT_S);
    	this.Copies=new HashMap<DTNHost,Double>();
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
    
    protected IMSPAERouter(IMSPAERouter r)
    {
    	super(r);
    	initPreds();
    	this.initialNrofCopies = r.initialNrofCopies;
    	this.isEncounterBased = r.isEncounterBased;
    	this.secondsInTimeUnit=r.secondsInTimeUnit;
    	this.Copies=r.Copies;
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
		this.LastEnergy=new HashMap<DTNHost,Double>();
		this.Encounter=new HashMap<DTNHost,Double>();
		this.IrlPreds=new HashMap<DTNHost,Double>();
		this.LastBuffer=new HashMap<DTNHost,Double>();
    	encounterHost = new Vector();
    	
		this.EncounterCount=new HashMap<DTNHost, Double>();
		this.Utility=new HashMap<DTNHost, Double>();  
		 
		this.PreviousValue=new HashMap<DTNHost, Double>();
		this.EncounterValue=new HashMap<DTNHost, Double>();
		this.CurrentWindowCounter=new HashMap<DTNHost,Double>();
		this.Proportion=new HashMap<DTNHost,Double>();
		encounterHostInNeighbor = new Vector();
		indirectEncounterHost = new Vector();
    }
    
	@Override
	protected int checkReceiving(Message m, DTNHost from) {
		if (this.currentEnergy < 0) {
			return DENIED_UNSPECIFIED;
		}
		else
		/* peer message count check OK; receive based on other checks */
		   return super.checkReceiving(m, from);
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
	
    public void init(DTNHost host, List<MessageListener> mListeners)
	{
		super.init(host,mListeners);
		
		int rank = 0;
		Map<Integer, Double> neighor = new TreeMap<Integer, Double>();
		Map<Integer, Double> friends = new LinkedHashMap<Integer, Double>();		
		List<FriendValue> myNeighborFriends = new ArrayList<FriendValue>();
		
		peopleRankAlgorithn = new PeopleRankAlgorithmUtil(rank, neighor, friends,myNeighborFriends);
	}
	
	public double retrieveRank()
	{
		return peopleRankAlgorithn.getRank();
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
	
	private double calculateLocalBetUtil() {
		double sum = 0.0;
		for (int i=0; i < newAdjacencyMatrix.length; i++) {
			for (int j=0; j < newAdjacencyMatrix.length; j++) {
				if ((i<j) && (newAdjacencyMatrix[i][j] == 0)) {
					for (int k = 0; k < newAdjacencyMatrix.length; k++) {
						sum = sum + newAdjacencyMatrix[i][k] * newAdjacencyMatrix[k][j];
					}	
					betweeness = betweeness + (1/sum);	
					sum = 0.0;				
				}
			}
	    }
	    return betweeness;
	}
	
	private void updateIndirectEncounterHostList(DTNHost host) {
		IMSPAERouter othRouter = (IMSPAERouter)host.getRouter();
		boolean isNewEncounterHost = true;			
		//
		if (encounterHost.contains(host)) {
			int column = 0;
			int row = 0;
			isNewEncounterHost = false;
			for (int i=0; i < (othRouter.encounterHost.size()); i++) {
				if (encounterHost.contains(othRouter.encounterHost.get(i)))	{
					continue;
				} else {
					if (indirectEncounterHost.contains(othRouter.encounterHost.get(i))){
						row = encounterHost.indexOf(host);
						column = indirectEncounterHost.indexOf((othRouter.encounterHost.get(i)));
						oldIndirectMatrix[row][column] = 1;
					} else {
						row = encounterHost.size() + 1; //
						column = indirectEncounterHost.size() + 1;//
						newIndirectMatrix = new int [row][column];
						if (indirectEncounterHost.size() > 1) {
							for (int k = 0; k < (oldIndirectMatrix.length - 1); k++) {
								for (int j=0; j<(oldIndirectMatrix[k].length - 1); j++) {//
									newIndirectMatrix[k][j] = oldIndirectMatrix[k][j];
								}
							}
						}
						for (int s = 0; s < (row - 1); s++) {
							newIndirectMatrix[s][column - 1] = 0;
						}
						newIndirectMatrix[row - 1][column - 1] = 1;
						oldIndirectMatrix = newIndirectMatrix;					
					}
				}
			}		
		}
		
		//
		if (isNewEncounterHost == true) {
			int column = 0;
			int row = 0;

			if (indirectEncounterHost.contains(host)) {
				row = encounterHost.size() + 1;
				column = indirectEncounterHost.size() - 1;
				newIndirectMatrix = new int[row][column];
				for (int i=0; i < encounterHost.size(); i++){
					for (int j=0; j < indirectEncounterHost.size(); j++){
						if (j < indirectEncounterHost.indexOf(host)){
							newIndirectMatrix[i][j] = oldIndirectMatrix[i][j];
						}
						if (j > indirectEncounterHost.indexOf(host)){
							newIndirectMatrix[i][j-1] = oldIndirectMatrix[i][j];
						}
					}					
				}
				
				oldIndirectMatrix = newIndirectMatrix;
				indirectEncounterHost.removeElement(host);
			}
						
			for (int i=0; i < othRouter.encounterHost.size(); i++) {
				if (encounterHost.contains(othRouter.encounterHost.get(i)))	{
					continue;
				} 
                              else {
					if (indirectEncounterHost.contains(othRouter.encounterHost.get(i))){
						row = encounterHost.indexOf(host);
						column = indirectEncounterHost.indexOf((othRouter.encounterHost.get(i)));
						oldIndirectMatrix[row][column] = 1;
					} 
                                       else {
						row = encounterHost.size() + 1; //
						column = indirectEncounterHost.size() + 1;//
						newIndirectMatrix = new int [row][column];
						for (int k=0; k<(oldIndirectMatrix.length - 1); k++) {
							for (int j=0; j<(oldIndirectMatrix[k].length - 1); j++) {//
								newIndirectMatrix[k][j] = oldIndirectMatrix[k][j];
							}
						}
						for (int s = 0; s < (row - 1); s++) {
							newIndirectMatrix[s][column - 1] = 0;
						}
						newIndirectMatrix[row - 1][column - 1] = 1;
						oldIndirectMatrix = newIndirectMatrix;					
					}
				}
			}		
		}		
	}
	
	@SuppressWarnings("unchecked")
	private void updateEncounterHostList(DTNHost host) {
		boolean isNewEncounterHost = true;
			
		if (encounterHost.contains(host)) {
			isNewEncounterHost = false;
		}
		
		if (isNewEncounterHost == true) {
			encounterHost.add(host);
			//
			dimension = encounterHost.size() + 1;
			newAdjacencyMatrix = new int [dimension][dimension];
		
			//
			if (encounterHost.size() > 1) {
				for (int i=0; i<newAdjacencyMatrix.length - 1; i++) {
					for (int j=0; j<newAdjacencyMatrix.length - 1; j++) {
						newAdjacencyMatrix[i][j] = oldAdjacencyMatrix[i][j];
					}
				}
			}		
			//
			for (int i=0; i<encounterHost.size(); i++) {
				if (encounterHostInNeighbor.contains(encounterHost.get(i))) {
					newAdjacencyMatrix[newAdjacencyMatrix.length-1][i+1] = 1;
					newAdjacencyMatrix[i+1][newAdjacencyMatrix.length-1] = 1;
				}
			}
			//
			newAdjacencyMatrix[0][newAdjacencyMatrix.length-1] = 1;
			newAdjacencyMatrix[newAdjacencyMatrix.length-1][0] = 1;
		
			oldAdjacencyMatrix = newAdjacencyMatrix;
		}
				
		return;
	}
	
	public void updateResource(DTNHost Host){
		double Energy=Math.abs(getEnergy(Host)-getlastEnergy(Host))/(this.initEnergy[0]-getEnergy(Host));
		double buf=Math.abs(Host.getBuffer()-this.getLastBuffer(Host))/(this.getBufferSize()-Host.getBuffer());
		double value=1/(Energy+buf);
		Resource.put(Host,value);
	}
	
	public double getResource(DTNHost Host){
		if(this.Resource.containsKey(Host))
			return this.Resource.get(Host);
		else
			return 0;
	}
	
	public void updateUtility(DTNHost Host)
	{	
		double C=this.getEncounterCount(Host);
		double Here;
	
		if(C==1)
				Here = this.PreviousValue.get(Host);
		else
			    Here = this.PreviousValue.get(Host)/(C);
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
		MessageRouter otherRouter=Host.getRouter();
		  //  double pForHost = getEncounter(host); //P(b,a)
		    Map<DTNHost, Double> othersPreds = ((IMSPAERouter)otherRouter).getTransEncounter();
		    double value=0;
		    double count=0;
		    double avgvalue=0;
		    for (Map.Entry<DTNHost, Double> e : othersPreds.entrySet()) {
		        if (e.getKey() == getHost()) { //
		            continue; 
		        }
		        value += getEncounter(e.getKey());
		        
		        if(getEncounter(e.getKey())>0){
		        	count++;
		        } 	
		    }
		    if(count!=0&&value!=0)
		    	
		    avgvalue = value/count;
		    
		    else
		    	return;
		    
		    double factorial=1;
		    
		    if(avgvalue<=1){
		    	factorial=1;
		    }
		double preds = 0;
		double timeDiff = (SimClock.getTime() - getLastAgeUpdate(Host))-secondsInTimeUnit;//
		if (timeDiff<0.0) {
			return;
		}
		if(timeDiff>=0.0)
			
		{  // System.out.println(timeDiff+"	"+this.getUtilityFor(Host));
			preds = Math.pow(Math.E, -avgvalue*timeDiff/60);
		    for(int k=1;k<=this.getEncounter(Host);k++){
		    	factorial*=k;	
		    	if(factorial==0){
		    		preds=Math.pow(Math.E, -avgvalue*timeDiff/60);
		    	}
		    	else
		    		preds=Math.pow(Math.E, -avgvalue*timeDiff/60)*Math.pow(avgvalue*timeDiff/60, k)/factorial;
		    }
		    double v=1-Math.pow(Math.E, -avgvalue*timeDiff/60);
		    Possion.put(Host, v);
			EncounterValue.put(Host, (getCurrentWindowCounter(Host)*ALFA+(1-ALFA)*getEncounterValue(Host)));
			updateLastAgeUpdate(Host,SimClock.getTime());
			//CurrentWindowCounterupdate=CurrentWindowCounter;
			CurrentWindowCounter.put(Host,0.0);
			//System.out.println(getEncounterValue(Host));
			
		}
	}
	
	public double getPossion(DTNHost host){
		if(this.Possion.containsKey(host))
			return this.Possion.get(host);
		else
			return 0;
	}
	
    public void updateCurrentWindowCounter(DTNHost Host)//
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
		IMSPAERouter OtherRouter = (IMSPAERouter)MRouter;

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
		this.Packet=new HashMap<DTNHost, Double>();
		this.PreviousValue=new HashMap<DTNHost, Double>();
		this.EncounterValue=new HashMap<DTNHost, Double>();
		this.CurrentWindowCounter=new HashMap<DTNHost,Double>();
		this.Proportion=new HashMap<DTNHost,Double>();
		this.LastAgeUpdate=new HashMap<DTNHost,Double>();
		this.Resource=new HashMap<DTNHost,Double>();
		this.Possion=new HashMap<DTNHost,Double>();
	}
	
	public void changedConnection(Connection con) 	
	{
		DTNHost OtherHost = con.getOtherNode(getHost());	
	    IMSPAERouter OtherRouter = (IMSPAERouter)OtherHost.getRouter();
		if (con.isUp())   
		{			
			double StartTime=SimClock.getTime();
            updateEncounter(OtherHost);
			double S = StartTime-getLastEncounterTime(OtherHost);
	
			updateLastEncounterTime(OtherHost, StartTime);	
			updateEncounterCount(OtherHost);	
			updateEncounterTime(OtherHost, S);
			
			updateTransitivePreds(OtherHost);
			updateTransitiveIrlPreds(OtherHost);
			updateEncounterHostList(OtherHost);
			updateIndirectEncounterHostList(OtherHost);
		//	updatePreds(OtherHost);
            
			updateCurrentWindowCounter(getHost());
			updateCurrentWindowCounter(OtherHost);//
			updateEncounterValues(getHost());
			updateEncounterValues(OtherHost);
			double sum=getCopies(getHost())*getEncounterValue(getHost())*getEnergy(getHost())+
					getCopies(con.getOtherNode(getHost()))*getEnergy(con.getOtherNode(getHost()))*getEncounterValue(con.getOtherNode(getHost()));
        	if(this.getCopiesRatio(OtherHost)>0){
        		sum=getEncounterValue(getHost())*getEnergy(getHost())/getCopiesRatio(getHost())
        				+getEnergy(con.getOtherNode(getHost()))*getEncounterValue(con.getOtherNode(getHost()))/getCopiesRatio(con.getOtherNode(getHost()));
        		Proportion.put(getHost(),getEnergy(con.getOtherNode(getHost()))*getEncounterValue(con.getOtherNode(getHost()))/(getCopiesRatio(con.getOtherNode(getHost()))*sum));
        	}
        	else
        		Proportion.put(getHost(),getCopies(con.getOtherNode(getHost()))*getEnergy(con.getOtherNode(getHost()))*getEncounterValue(con.getOtherNode(getHost()))/sum);
			if(this.getEncounterCount(OtherHost)>1)		
			{	
				double ET=this.getEncounterTime(OtherHost);	
				double ED=this.getEncounterDuration(OtherHost);
	
				updatePreviousValue(OtherHost,(ET-ED));	
				updateLastEnergy(OtherHost);
				updateLastBuffer(OtherHost);
				updateResource(OtherHost);
				updateUtility(OtherHost);
				updateIreland(OtherHost);
			}
			
			else if (this.getEncounterCount(OtherHost)==1)	
			{
				double ET=this.getEncounterTime(OtherHost);	
				updatePreviousValue(OtherHost,(ET-0));	
				updateLastEnergy(OtherHost);
				updateLastBuffer(OtherHost);
				updateResource(OtherHost);
				updateUtility(OtherHost);
				updateIreland(OtherHost);
			}
				
			updateTransitivity(OtherHost);					
			this.ackedMessageIds.addAll(OtherRouter.ackedMessageIds);	
			OtherRouter.ackedMessageIds.addAll(this.ackedMessageIds);
	
			deleteAckedMessages();
			OtherRouter.deleteAckedMessages();	
		}
				
		else if (!con.isUp())   
		{
			double FinishTime= SimClock.getTime();	
			DTNHost Peer = con.getOtherNode(getHost());
			updateCopies(Peer);
		    updateEnergy(Peer);
			double C = FinishTime-this.getLastEncounterTime(Peer); 
			updateEncounterDuration(Peer,C);
		}
	}
	
	private void updateCopies(DTNHost peer) {
		// TODO Auto-generated method stub
		double num=peer.getNrofMessages();
		Copies.put(peer,num);
	}

	private double getCopies(DTNHost peer){
		if(this.Copies.containsKey(peer))
			return this.Copies.get(peer);
		else
			return 0;
	}

	private double getCopiesRatio(DTNHost peer)
	{
		if(this.Copies.containsKey(peer))
			return (Math.abs((this.Copies.get(peer)-peer.getNrofMessages()))/this.Copies.get(peer));
		else
			return 0;
	}
	private void updateDynamic(DTNHost host) {
		// TODO Auto-generated method stub
		Settings SPB=new Settings(EBSPRAYANDWAIT_NS);
		double copies=SPB.getInt(NROF_COPIES);
		double occupy=1-host.getBufferOccupancy()*0.01;
		IMSPAERouter oth=(IMSPAERouter) host.getRouter();
		double energy=oth.currentEnergy;
	    double value=energy/this.initEnergy[0];
	    double count=(this.getEncounter(host)*occupy+value*copies);
	    Packet.put(host, count);
	}

	private double getDynamicPacket(DTNHost from) {
		// TODO Auto-g
		IMSPAERouter oth=(IMSPAERouter) from.getRouter();
		double energy=oth.currentEnergy;
		Settings SPB=new Settings(EBSPRAYANDWAIT_NS);
		double copies=SPB.getInt(NROF_COPIES);
		
		if(this.Packet.containsKey(from))
			
	       return this.Packet.get(from);
		else 
		   return copies*energy/this.initEnergy[0];
	}
	
	private void updateLastBuffer(DTNHost otherHost) {
		// TODO Auto-generated method stub
		double buf=otherHost.getBuffer();
		LastBuffer.put(otherHost,buf);
	}

	   private double getLastBuffer(DTNHost host){
			if(this.LastBuffer.containsKey(host))
				return this.LastBuffer.get(host);
			else
				return 0;
		  }
	
	private void updateLastEnergy(DTNHost host) {
		// TODO Auto-generated method stub
		IMSPAERouter other=(IMSPAERouter) host.getRouter();
		double energy=other.currentEnergy;
		LastEnergy.put(host,energy);
	}

   private double getlastEnergy(DTNHost host){
	if(this.LastEnergy.containsKey(host))
		return this.LastEnergy.get(host);
	else
		return 0;
  }
	
	private Map<DTNHost, Double> getTransEncounter() {
		ageDeliveryEncounter(); // make sure the aging is done
		return this.Encounter;
	}
	
	private void ageDeliveryEncounter() {
		double secondsInTimeUnit=50;
		double timeDiff = (SimClock.getTime() - this.lastAgeUpdate) / 
			secondsInTimeUnit;		
		if (timeDiff == 0) {
			return;
		}		
		this.lastAgeUpdate = SimClock.getTime();
	}

	private double getIrlPreds(DTNHost host){
		
		if (IrlPreds.containsKey(host)) {
			return IrlPreds.get(host);
		}
		else {
			return 0;
		}
	}
	
	public double getIrlPredFor(DTNHost host) {
		ageDeliveryPreds(); // make sure preds are updated before getting
		if (IrlPreds.containsKey(host)) {
			return IrlPreds.get(host);
		}
		else {
			return 0;
		}
	}
	
	public double calculateLocalSimUtil(DTNHost host) {//
		int row = 0;
		int column = 0;
		similarity = 0;
		if (encounterHost.contains(host)) {
			row = encounterHost.indexOf(host) + 1;
			for (int j=0; j < (encounterHost.size() + 1); j++) {
				 similarity += oldAdjacencyMatrix[0][j] * oldAdjacencyMatrix[row][j];
			}
			return similarity;
		}
		if (indirectEncounterHost.contains(host)) {
			column = indirectEncounterHost.indexOf(host);
			for (int i=0; i < (encounterHost.size() + 1); i++) {
				similarity += oldIndirectMatrix[i][column];
			}
			return similarity;
		}
		return similarity;
	}
	
	private void updateIreland(DTNHost thishost) {
		// TODO Auto-generated method stub
		double Here;
		double C=this.getEncounterCount(thishost);
		if(C==1)
			Here = this.PreviousValue.get(thishost);
     	else
		    Here = this.PreviousValue.get(thishost)/(C);
	//	System.out.println(Here/60);
		double prop=0;
		double factorial=1;
		double encounter=this.getEncounter(thishost);
		 MessageRouter otherRouter=thishost.getRouter();
		  //  double pForHost = getEncounter(host); //P(b,a)
		    Map<DTNHost, Double> othersPreds = ((IMSPAERouter)otherRouter).getTransEncounter();
		    double value=0;
		    double count=0;
		    double avgvalue=0;
		    
		    for (Map.Entry<DTNHost, Double> e : othersPreds.entrySet()) {
		        if (e.getKey() == getHost()) { 
		            continue; 
		        }
		        value += getEncounter(e.getKey());
		        if(getEncounter(e.getKey())>0){
		        	count++;
		        } 	
		    }
		    if(count!=0&&value!=0)
		    	
		    avgvalue = value/count;
		    
		    else
		    	avgvalue=0;

        	for(int k=1;k<encounter;k++){
        		factorial*=k;
        		prop+=avgvalue*Math.pow(Math.E, -Here/60*avgvalue)*Math.pow(Here/60*avgvalue, k)/factorial;
        	}
            IrlPreds.put(thishost, Math.pow(prop+avgvalue*Math.pow(Math.E, -Here/60*avgvalue), 0.1));
        }

	private double getPreds(DTNHost thishost){
		
		if(this.Possion.containsKey(thishost))
			
			return this.Possion.get(thishost);
		else
			return 0;
	}
	
	public double getPredFor(DTNHost host) {
		ageDeliveryPreds(); // make sure preds are updated before getting
		if (Possion.containsKey(host)) {
			return Possion.get(host);
		}
		else {
			return 0;
		}
	}
	
	private void updateTransitiveIrlPreds(DTNHost host){
		MessageRouter otherRouter = host.getRouter();
		assert otherRouter instanceof IMSPAERouter : "SPBEU only works " + 
			" with other routers of same type";
		
		double pForHost = getIrlPreds(host); // P(a,b)
		Map<DTNHost, Double> othersPreds = 
			((IMSPAERouter)otherRouter).getDeliveryIrlPreds();
		
		for (Map.Entry<DTNHost, Double> e : othersPreds.entrySet()) {
			if (e.getKey() == getHost()) { //锟斤拷时getHost()锟斤拷指锟节碉拷a
				continue; // don't add yourself
			}
			
			double pOld = getIrlPreds(e.getKey()); // P(a,c)_old实为锟节碉拷a锟斤拷getPredFor锟斤拷锟洁当锟斤拷this.getPredFor
			double pNew = pOld + ( 1 - pOld) * pForHost * e.getValue() * 0.25;//e.getValue为P(b, c)
			IrlPreds.put(e.getKey(), pNew);
		}
	}
	
	/**
	 * Updates transitive (A->B->C) delivery predictions.
	 * <CODE>P(a,c) = P(a,c)_old + (1 - P(a,c)_old) * P(a,b) * P(b,c) * BETA
	 * </CODE>
	 * @param host The B host who we just met
	 */
	
	//updateTransitivePreds(otherHost); 锟斤拷锟斤拷a--b锟斤拷锟节碉拷a锟斤拷update锟斤拷锟斤拷么锟斤拷锟斤拷锟斤拷host锟斤拷指b
	private void updateTransitivePreds(DTNHost host) {
		MessageRouter otherRouter = host.getRouter();
		assert otherRouter instanceof IMSPAERouter : "IMSPAE only works " + 
			" with other routers of same type";
		double p=getPossion(host);
		Map<DTNHost, Double> othPreds = 
				((IMSPAERouter)otherRouter).getDeliveryPossion();
		for (Map.Entry<DTNHost, Double> e2 : othPreds.entrySet()) {
			if (e2.getKey() == getHost()) { //锟斤拷时getHost()锟斤拷指锟节碉拷a
				continue; // don't add yourself
		}
			double pOld = getPossion(e2.getKey()); // P(a,c)_old实为锟节碉拷a锟斤拷getPredFor锟斤拷锟洁当锟斤拷this.getPredFor
			double pNew = pOld + ( 1 - pOld) * p * e2.getValue() * 0.25;//e.getValue为P(b, c)
			Possion.put(e2.getKey(), pNew);
		}
	}
	
	/**
	 * Ages all entries in the delivery predictions.
	 * <CODE>P(a,b) = P(a,b)_old * (GAMMA ^ k)</CODE>, where k is number of
	 * time units that have elapsed since the last time the metric was aged.
	 * @see #SECONDS_IN_UNIT_S
	 */
	private void ageDeliveryPreds() {
		double timeDiff = (SimClock.getTime() - this.lastAgeUpdate) / 
			30;//
		
		if (timeDiff == 0) {
			return;
		}
		this.lastAgeUpdate = SimClock.getTime();
	}
	
	/**
	 * Returns a map of this router's delivery predictions
	 * @return a map of this router's delivery predictions
	 */

	private Map<DTNHost, Double> getDeliveryPossion() {
		ageDeliveryPreds(); // make sure the aging is done
		return this.Possion;
	}
	private Map<DTNHost, Double> getDeliveryIrlPreds() {
		ageDeliveryPreds(); // make sure the aging is done
		return this.IrlPreds;
	}
	private void updateEncounter(DTNHost otherHost) {
		// TODO Auto-generated method stub
		double value=getEncounter(otherHost);
		double newvalue=value+1;
		Encounter.put(otherHost, newvalue);
	}
	
	public double getEncounter(DTNHost otherHost) {
		// TODO Auto-generated method stub
		if(this.Encounter.containsKey(otherHost))
			return this.Encounter.get(otherHost);
		else
			return 0;
	}
	
	private void updateEnergy(DTNHost peer) {
		// TODO Auto-generated method stub
		IMSPAERouter othRouter=(IMSPAERouter) peer.getRouter();
		double value=othRouter.currentEnergy;
		double v=(double)peer.getComBus().getProperty(IMSPAERouter.ENERGY_VALUE_ID);
		Energy.put(peer, value);
	}
	
    public double getEnergy(DTNHost peer){
    	if(this.Energy.containsKey(peer))
			return this.Energy.get(peer);
		else
			return 0;
    }
	
	public void update() 
	{
		super.update();	
		reduceSendingAndScanningEnergy();
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
		List<Tuple<Message, Connection>> single = 
				new ArrayList<Tuple<Message, Connection>>(); 
		Collection<Message> msgCollection = getMessageCollection();
		double sourceUtil=0;
		double desUtil=0;
		double simUtil = 0.0;
		double simBetUtil = 0.0;
		double othSimUtil = 0.0;
		/* for all connected hosts collect all messages that have a higher
		   probability of delivery by the other host */
		
			for (Connection con : getConnections()) 
			{
				DTNHost other = con.getOtherNode(getHost());
	            
				IMSPAERouter othRouter = (IMSPAERouter)other.getRouter();

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
					
					//CommunityBasedMovement m1= (CommunityBasedMovement)other.movement;
					//CommunityBasedMovement m2= (CommunityBasedMovement)m.getTo().movement;
					
					double count = getEncounter(other);
					double num = getEncounter(m.getTo());
					double avg1 = Math.sqrt(count*num);
	                double avg2= Math.sqrt(count*othRouter.getEncounter(m.getTo()));
	                sourceUtil=othRouter.getUtilityFor(m.getTo())/(othRouter.getUtilityFor(m.getTo())+getUtilityFor(m.getTo()));
	                desUtil=getUtilityFor(m.getTo())/(othRouter.getUtilityFor(m.getTo())+getUtilityFor(m.getTo()));
	                double othPred=othRouter.getPredFor(m.getTo())/(othRouter.getPredFor(m.getTo())+getPredFor(m.getTo()));
	                double simPred=getPredFor(m.getTo())/(othRouter.getPredFor(m.getTo())+getPredFor(m.getTo()));
	            //    System.out.println(othPred+"	"+simPred);    
	                double othPredUtil=alpha *simPred+beta * sourceUtil;
	                double simPredUtil=alpha *othPred+beta * desUtil;
					
	                if(avg1!=0&&avg2!=0){
		            	  simUtil = (calculateLocalBetUtil()/avg1) / (calculateLocalBetUtil()/avg1 + othRouter.calculateLocalBetUtil()/avg2);
						  othSimUtil = (othRouter.calculateLocalBetUtil()/avg2) / (calculateLocalBetUtil()/avg1 + othRouter.calculateLocalBetUtil()/avg2);
		              }
		              else
		            	  simUtil = calculateLocalBetUtil()/ (calculateLocalBetUtil() + othRouter.calculateLocalBetUtil());
					      othSimUtil =othRouter.calculateLocalBetUtil() / (calculateLocalBetUtil() + othRouter.calculateLocalBetUtil());
	                
				    simBetUtil = alpha * simUtil + beta * this.getIrlPredFor(m.getTo())/(othRouter.getIrlPredFor(m.getTo())+getIrlPredFor(m.getTo()));
					double othSimBetUtil = alpha * othSimUtil + beta * othRouter.getIrlPredFor(m.getTo())/(othRouter.getIrlPredFor(m.getTo())+getIrlPredFor(m.getTo()));
					if ((ackedMessageIds.contains(m.getId()))||(othRouter.ackedMessageIds.contains(m.getId())))
			    		continue;
					Integer nrofCopies = (Integer)m.getProperty(MSG_COUNT_PROPERTY);  
				//	if(m1.rnd_i!=m2.rnd_i){
						 if(avg1!=0&&avg2!=0){
			            	  simUtil = (calculateLocalSimUtil(m.getTo())/avg1) / ((calculateLocalSimUtil(m.getTo())/avg1) + othRouter.calculateLocalSimUtil(m.getTo())/avg2);
							  othSimUtil = (othRouter.calculateLocalSimUtil(m.getTo())/avg2) / ((calculateLocalSimUtil(m.getTo())/avg1) + othRouter.calculateLocalSimUtil(m.getTo())/avg2);
			              }
			              else
			            	  simUtil = calculateLocalSimUtil(m.getTo())/ (calculateLocalSimUtil(m.getTo()) + othRouter.calculateLocalSimUtil(m.getTo()));
						      othSimUtil =othRouter.calculateLocalSimUtil(m.getTo()) / (calculateLocalSimUtil(m.getTo())) + othRouter.calculateLocalSimUtil(m.getTo());
						      simBetUtil = alpha * simUtil + beta * this.getIrlPredFor(m.getTo())/(othRouter.getIrlPredFor(m.getTo())+getIrlPredFor(m.getTo()));
							  othSimBetUtil = alpha * othSimUtil + beta * othRouter.getIrlPredFor(m.getTo())/(othRouter.getIrlPredFor(m.getTo())+getIrlPredFor(m.getTo()));
							 if(nrofCopies>1){
                                  if(othRouter.Utility.containsKey(m.getTo()))
									{
									  if(Utility.containsKey(m.getTo())){
										    if(othRouter.getPredFor(m.getTo())!=0&&getPredFor(m.getTo())!=0){
												if (othPredUtil*othRouter.getResource(m.getTo())>simPredUtil*getResource(m.getTo()))
												{
													// the other node has higher probability of delivery
												  messages.add(new Tuple<Message, Connection>(m,con));
											    }
												else if(othRouter.getUtilityFor(m.getTo())/othRouter.getResource(m.getTo())<getUtilityFor(m.getTo())/getResource(m.getTo())){
													 messages.add(new Tuple<Message, Connection>(m,con));
												}
												else  {
													 messages.add(new Tuple<Message, Connection>(m,con));
												   }
											}
				
										else  { 
											if(othRouter.getPredFor(m.getTo())>getPredFor(m.getTo())){
												 messages.add(new Tuple<Message, Connection>(m,con));
										}
											   }
											}	
										else{
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
									    if(othRouter.getPredFor(m.getTo())!=0&&getPredFor(m.getTo())!=0){
											if (othPredUtil*othRouter.getResource(m.getTo())>simPredUtil*getResource(m.getTo())) 
											{
												// the other node has higher probability of delivery
											  messages.add(new Tuple<Message, Connection>(m,con));
										    }
											else if(othRouter.getUtilityFor(m.getTo())/othRouter.getResource(m.getTo())<
													getUtilityFor(m.getTo())/getResource(m.getTo())){
												 messages.add(new Tuple<Message, Connection>(m,con));
											}
										}

									}
								}
							 }
					//}
					//if(m1.rnd_i==m2.rnd_i){
						if(nrofCopies>1){
						    if(!othRouter.EncounterValue.containsKey(m.getTo())){
						    	if(othSimBetUtil > simBetUtil)
								{
									single.add(new Tuple<Message, Connection>(m,con));
								}
						     }
						   else if(othRouter.Utility.containsKey(m.getTo()))
							{
							  if(Utility.containsKey(m.getTo())){
								    if(othRouter.getPredFor(m.getTo())!=0&&getPredFor(m.getTo())!=0){
										if (othPredUtil*othRouter.getResource(m.getTo())>simPredUtil*getResource(m.getTo()))
										{
											// the other node has higher probability of delivery
										  messages.add(new Tuple<Message, Connection>(m,con));
									    }
										else if(othRouter.getUtilityFor(m.getTo())/othRouter.getResource(m.getTo())<getUtilityFor(m.getTo())/getResource(m.getTo())){
											 messages.add(new Tuple<Message, Connection>(m,con));
										}
										else  {
											 messages.add(new Tuple<Message, Connection>(m,con));
										   }
									}
		
								else  { 
									if(othRouter.getPredFor(m.getTo())>getPredFor(m.getTo())){
										 messages.add(new Tuple<Message, Connection>(m,con));
								}
									   }
									}	
								else{
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
							    if(othRouter.getPredFor(m.getTo())!=0&&getPredFor(m.getTo())!=0){
									if (othPredUtil*othRouter.getResource(m.getTo())>simPredUtil*getResource(m.getTo())) 
									{
										// the other node has higher probability of delivery
									  messages.add(new Tuple<Message, Connection>(m,con));
								    }
									else if(othRouter.getUtilityFor(m.getTo())/othRouter.getResource(m.getTo())<
											getUtilityFor(m.getTo())/getResource(m.getTo())){
										 messages.add(new Tuple<Message, Connection>(m,con));
									}
								}

							}
						}
					 }
					}
			//	}	
				if (messages.size() == 0&&single.size()==0) 
				{
					return null;
				}
			}
			  if(tryMessagesForConnected(messages)!=null)
				   return tryMessagesForConnected(messages);	
			  else
				  return tryMessagesForConnected(single);
	}
	
	@Override
	public boolean makeRoomForMessage(int size) {
		
		if (size > this.getBufferSize()) {
			return false; // Message too big for the buffer
		}
		
		long freeBuffer = this.getFreeBufferSize();
		
		// Check if there is enough space to receive the message before sorting the buffer
		if (freeBuffer >= size) {
			return true;
		}
		
		// Sort the messages by ttl
		ArrayList<Message> messages = new ArrayList<Message>(this.getMessageCollection());
		Collections.sort(messages, new SHLIComparator());
		
		/* delete messages from the buffer until there's enough space */
		while (freeBuffer < size) {
			
			if (messages.size() == 0) {
				return false; // Couldn't remove more messages
			}
			// Get the message with minimum ttl
			Message msg = messages.remove(0);
			
			// Check if the router is sending this message
			if (this.dropMsgBeingSent || !this.isSending(msg.getId())) {
				this.deleteMessage(msg.getId(), true);
				freeBuffer += msg.getSize();
			}
		}
		return true;
	}
	
	private class SHLIComparator implements Comparator<Message> {
		@Override
		public int compare(Message msg0, Message msg1) {
			return ((Integer)msg0.getTtl()).compareTo(msg1.getTtl());
		}
		
	}
	
	public boolean createNewMessage(Message msg) {
		makeRoomForNewMessage(msg.getSize());
		msg.setTtl(this.msgTtl);
		Settings SPB=new Settings(EBSPRAYANDWAIT_NS);
		int copies=SPB.getInt(NROF_COPIES);
		int newCopies=(int) Math.ceil(this.getDynamicPacket(msg.getFrom()));
		initialNrofCopies=(int) Math.max(newCopies, copies/1.25);
		addToMessages(msg, true);
		msg.addProperty(MSG_COUNT_PROPERTY, initialNrofCopies);
	//	System.out.println(this.getDynamicPacket(msg.getFrom()));
		return true;
	}
	
	public Message messageTransferred(String id, DTNHost from) {
	    
		Message msg = super.messageTransferred(id, from);
		//锟斤拷锟斤拷1锟斤拷取锟斤拷锟斤拷息锟斤拷copies
		Integer nrofCopies = (Integer)msg.getProperty(MSG_COUNT_PROPERTY);//取值
		assert nrofCopies != null : "Not a SnW message: " + msg;
		//锟斤拷锟斤拷2锟斤拷锟斤拷锟斤拷锟斤拷息锟斤拷copies
		if (isEncounterBased) 
		{
			if(nrofCopies>1)
			{
				/* in binary S'n'W the receiving node gets ceil(3n/4) copies */
				nrofCopies = (int)Math.ceil(nrofCopies*getProportion(getHost()));
				//msg.updateProperty(MSG_COUNT_PROPERTY, nrofCopies);
			}	//System.out.println(Proportion.get(getHost()));			
		}
		else {
			/* in standard S'n'W the receiving node gets only single copy */
			nrofCopies = 1;
		}
		msg.updateProperty(MSG_COUNT_PROPERTY, nrofCopies);//锟斤拷锟斤拷
		
		return msg;
	}
	
	protected void transferDone(Connection con) 
	{   updateDynamic(getHost());
		Integer nrofCopies;
		String msgId = con.getMessage().getId();
		/* get this router's copy of the message */
		Message msg = getMessage(msgId);
		if (msg == null) { // message has been dropped from the buffer after..
			return; // ..start of transfer -> no need to reduce amount of copies
		}
		msg.MetNode.add(con.getOtherNode(getHost()));
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
	
	public void moduleValueChanged(String key, Object newValue) {
		this.currentEnergy = (Double)newValue;
	}

	@Override
	public RoutingInfo getRoutingInfo() {
		ageDeliveryPreds();
		RoutingInfo top = super.getRoutingInfo();
		RoutingInfo ri = new RoutingInfo(Possion.size() + 
				" delivery prediction(s)");
		
		for (Map.Entry<DTNHost, Double> e : Possion.entrySet()) {
			DTNHost host = e.getKey();
			Double value = e.getValue();
			
			ri.addMoreInfo(new RoutingInfo(String.format("%s : %.6f", 
					host, value)));
		}
		
		top.addMoreInfo(ri);
		return top;
	}
	
	@Override
	public String toString() {
		return super.toString() + " energy level = " + this.currentEnergy;
	}	
	
    public MessageRouter replicate() {
    	IMSPAERouter r = new IMSPAERouter(this);
		return r;
	}
}