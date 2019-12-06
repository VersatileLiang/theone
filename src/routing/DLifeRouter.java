/**
 * PeopleRank use the algorithm depicted in paper.
 * 
 */
package routing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import core.Connection;
import core.DTNHost;
import core.Message;
import core.MessageListener;
import core.Settings;
import core.SimClock;
import util.Tuple;

public class DLifeRouter extends ActiveRouter 		
{
	private static final Integer CurrentSamplingSlot = 10;
	private static final int SampleTimes = 50;
	public Map<Integer, Map<DTNHost, Double>> SlotAvgDuration;
	public Map<DTNHost, Double> WeightUtility;
	public Map<DTNHost, Double> ImportanceUtility;
	public Map<DTNHost, Double> LastEncounterTime;
	public Map<DTNHost, Double> SampledTotalEncounterDuration;
	public Map<DTNHost, Double> EncounterCount;
    public double MyImportance=0;
	private int CurrentDay;
	
	public DLifeRouter(Settings s)
	{
		super(s);
	}

	public DLifeRouter(ActiveRouter r)
	{
		super(r);
		
		SlotAvgDuration = new HashMap<Integer, Map<DTNHost, Double>>(SampleTimes);
		for(int i=0;i<SampleTimes;i++)
		{
			Map<DTNHost,Double> map=new HashMap<DTNHost,Double>();
			SlotAvgDuration.put(i, map);
		}
		
		WeightUtility = new HashMap<DTNHost, Double>();
		ImportanceUtility = new HashMap<DTNHost, Double>();
		
		LastEncounterTime = new HashMap<DTNHost, Double>();
		SampledTotalEncounterDuration = new HashMap<DTNHost, Double>();
		EncounterCount = new HashMap<DTNHost, Double>();
	}

	@Override
	public DLifeRouter replicate()
	{
		return new DLifeRouter(this);
	}

	/*Return True if Successfully transmitted*/
	public boolean transmitMessage(Connection c, Message m)
	{
		int retVal = startTransfer(m, c);
		
		if (retVal == RCV_OK)
		{
			return true; // accepted a message, don't try others
		} 
		
		else if (retVal > 0)
		{
			return false; // should try later -> don't bother trying others
		}

		return false;
	}
	
	
	@Override
	public void update()
	{
		super.update();
		
		if (isTransferring() || !canStartTransfer())
		{
			return; // transferring, don't try other connections yet
		}

		// Try first the messages that can be delivered to final recipient
		if (exchangeDeliverableMessages() != null)
		{
			return; // started a transfer, don't try others (yet)
		}

		tryOtherMessages();
	}
	
	public Tuple<Message, Connection> tryOtherMessages() 
	{
		List<Connection> connections = getConnections();
		List<Tuple<Message, Connection>> ReplicationList = new ArrayList<Tuple<Message, Connection>>(); 
		
		if (connections.size() == 0 || this.getNrofMessages() == 0)
			return null;
		
		for (Connection c : connections)
		{
			DTNHost OtherHost = c.getOtherNode(this.getHost());
			DLifeRouter OtherRouter = (DLifeRouter)OtherHost.getRouter();

			List<Message> msgCollection = new ArrayList<Message>(this.getMessageCollection());
			this.sortByQueueMode(msgCollection);
			
			for (Message m : msgCollection)       
			{
				if(
				(this.WeightUtility.containsKey(m.getTo()))
			    &&
			    (OtherRouter.WeightUtility.containsKey(m.getTo()))
			    )
				{
					if (OtherRouter.WeightUtility.get(m.getTo()) > this.WeightUtility.get(m.getTo()))
						ReplicationList.add(new Tuple<Message, Connection>(m,c));   
					else if (OtherRouter.ImportanceUtility.get(m.getTo()) > this.ImportanceUtility.get(m.getTo()))
						ReplicationList.add(new Tuple<Message, Connection>(m,c));
				}
			}
		}
		
		 Collections.sort(ReplicationList, new TupleComparator());
	     return tryMessagesForConnected(ReplicationList);  	
	}	
	
	private class TupleComparator implements Comparator <Tuple<Message, Connection>> 
	{
		public int compare(Tuple<Message, Connection> Tuple1,Tuple<Message, Connection> Tuple2) 
		{
			Message Message1 = Tuple1.getKey();
			Message Message2 = Tuple2.getKey();
			
			//System.setProperty("java.util.Arrays.useLegacyMergeSort", "true");  
			
			if(Message1.getReceiveTime()==Message2.getReceiveTime())
				return -1;
			else
			    return (int)(Message1.getReceiveTime()-Message2.getReceiveTime());  
		}
	}
	
	@Override
	public void changedConnection(Connection con)
	{
		if (con.isUp())
		{								
			DTNHost OtherHost = con.getOtherNode(getHost());
			DLifeRouter OtherRouter = (DLifeRouter) OtherHost.getRouter();
			double StartTime=SimClock.getTime();
			
			updateLastEncounterTime(OtherHost, StartTime);	
			updateEncounterCount(OtherHost);	
			
			/*Update Neighbour Importance*/
			ImportanceUtility.put(OtherHost, OtherRouter.MyImportance);
		} 
		
		else if (!con.isUp())   
		{
			double FinishTime= SimClock.getTime();	
			DTNHost Peer = con.getOtherNode(getHost());  
			double C = FinishTime-this.getLastEncounterTime(Peer);  
			
			/*Update Total Sampled Encounter Duration*/
			updateSampledTotalEncounterDuration(Peer,C);
		}
	}
	
	/*Update For Each Sample*/
	public void UpdateDailyHistory()
	{
		updateAvgDuration();
		updateWeightUtility();
		updateImportanceUtility();
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
		
	public void updateSampledTotalEncounterDuration(DTNHost Host, double M) 
	{	
		double NewValue = M;	
		SampledTotalEncounterDuration.put(Host, (getSampledTotalEncounterDuration(Host)+NewValue));
	}
			
	public double getSampledTotalEncounterDuration(DTNHost Host) 	
	{   	
		if(this.SampledTotalEncounterDuration.containsKey(Host))	
			return this.SampledTotalEncounterDuration.get(Host);	
		else	
			return 0;		
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
	
	private void updateAvgDuration()
	{
		/*Get History About Encountered Node in a Given Sampling slot*/
		Map<DTNHost,Double> CurrentSlotAvgDuration=this.SlotAvgDuration.get(CurrentSamplingSlot);
		
		for (Map.Entry<DTNHost,Double> e : SampledTotalEncounterDuration.entrySet()) 
		{
			DTNHost Node = e.getKey();
			double Value = e.getValue();
			
			double OldAD = 0;
			double NewAD=0;
			
			//System.out.println(CurrentSlotTime);
			//System.out.println(CurrentSamplingSlot);
			//System.out.println(this.SlotAvgDuration.get(this.CurrentSamplingSlot));
			
			if(CurrentSlotAvgDuration.containsKey(Node))
				OldAD = CurrentSlotAvgDuration.get(Node);
			else
				OldAD = 0;
			
			NewAD = (Value+(this.CurrentDay-1)*OldAD)/CurrentDay;
			
			CurrentSlotAvgDuration.put(Node, NewAD);
		}
		
		SlotAvgDuration.put(CurrentSamplingSlot, CurrentSlotAvgDuration);
		
		/*Clear Record, For Next Sample*/
	    this.SampledTotalEncounterDuration.clear();
	}
	
	public void updateWeightUtility()
	{
		int Denominator = SampleTimes;
		int Index = CurrentSamplingSlot;
		
		for(int i = SampleTimes; i>0; i--)
		{
			/*Get History About Encountered Node in a Given Sampling Slot*/
			Map<DTNHost,Double> CurrentSlotAvgDuration=this.SlotAvgDuration.get(Index);
			
			for (Map.Entry<DTNHost,Double> e : CurrentSlotAvgDuration.entrySet()) 
			{
				DTNHost Node = e.getKey();
				double Value = e.getValue();
				
				double OldWeight = 0;
				double NewWeight = 0;
				
				if(this.WeightUtility.containsKey(Node))
					OldWeight = WeightUtility.get(Node);
				else
					OldWeight = 0;
				
				NewWeight = OldWeight + (SampleTimes/(Denominator-1))*Value;
				
				WeightUtility.put(Node, NewWeight);
			}
			
			Denominator++;
			Index++;
			
			if(Index==24)
				Index=0;
		}
	}
	
	private void updateImportanceUtility()
	{
		/*Number of Nodes I Encountered*/
		int NeighbotNumbers = this.EncounterCount.size();
		
		for (Map.Entry<DTNHost,Double> e : WeightUtility.entrySet()) 
		{
			DTNHost Node = e.getKey();
			
			double NeighborImportance = 0;
			
			if(this.ImportanceUtility.containsKey(Node))
				NeighborImportance = this.ImportanceUtility.get(Node);
			else
				NeighborImportance = 0;
			
			MyImportance = MyImportance+(WeightUtility.get(Node)*NeighborImportance)/(NeighbotNumbers);
		}
		
		MyImportance=0.2+0.8*MyImportance;
	}
}