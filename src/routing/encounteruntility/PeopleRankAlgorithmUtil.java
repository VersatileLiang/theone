package routing.encounteruntility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import routing.EncounterUtilityRankRouter;
import core.DTNHost;

/*Compute PeopleRank Value*/
public class PeopleRankAlgorithmUtil 
{
	/*Setting Value*/
	/*d_factor��ֵ*/
	private double d_factor = 0.9;
	/*friend��ֵ*/
	private int friend_number_factor = 10;//����ֻ��Ч��ǰʮ��
	/*����*/
	private double rank;
	
	/*key is Nodal ID*/
	/*Value is Number of Encounters*/
	//�����Ľڵ�����
	Map<Integer, Double> neighbors;// = new TreeMap<Integer, Integer>();
	
	/*My Friendship With Given Number of Nodes*/
	//�����ڵ�������friends��������������ǰ10,����ĵĻ���Ч�ö�ǰ10����С��������
	Map<Integer, Double> friends; //= new LinkedHashMap<Integer, Integer>();

	/*Information of a Set of My Friends*/
	//һ�������Ϣ
	List<FriendValue> myNeighborFriends;

	/*���캯��*/
	public PeopleRankAlgorithmUtil(double rank,
		   Map<Integer, Double> neighors, Map<Integer, Double> friends,
		   List<FriendValue> myNeighborFriends) 
	{
		this.rank = rank;
		this.neighbors = neighors;
		this.friends = friends;
		this.myNeighborFriends = myNeighborFriends;
	}
	/*getfriends*/
	public Map<Integer, Double> getFriends() 
	{
		return friends;
	}
	/*getRank*/
	public double getRank()
	{
		return rank;
	}
	/*��������*/
	/*myNeighborFriends=F(Nj)����Nj���ӵĽڵ㼯��
	 *myNeighborFriends.get(i).getFriendNum()=F(Nj)
	 *myNeighborFriends.get(i).getRankValue()=PeR(Nj)
	 *ͨ����ʽ����rank*/
	
	public void  calculatePeopleRank( )
	{	
		double otherNodesrank = 0;

		/*friendValue.size()=10*/
		for(int i=0; i<myNeighborFriends.size();i++)
		{
			/*Only When Friend Number > 0*/
			if(myNeighborFriends.get(i).getFriendNum()>0)
				otherNodesrank = otherNodesrank + myNeighborFriends.get(i).getRankValue()/myNeighborFriends.get(i).getFriendNum();
		}
		
		rank = 0;
		rank = (1-d_factor) + otherNodesrank*d_factor;
	}
	/*update
	 * 1.���û������������neighbors����ź���������
	 * 2.�������������ź���������
	 * 3.��friends���дӸߵ�������
	 * 4.���friends���������ڵ�Ļ�*/
	public void update(DTNHost myHost,DTNHost peerHost)
	{
		EncounterUtilityRankRouter myRouter = (EncounterUtilityRankRouter)myHost.getRouter();	
		/*Did Not Encounter Before*/
		if(!neighbors.containsKey(peerHost.getAddress()))
		{
			neighbors.put(peerHost.getAddress(),myRouter.getUtilityFor(peerHost));
		}
		
		else
		{
			//�õ�Ч��ֵ
			neighbors.put(peerHost.getAddress(), myRouter.getUtilityFor(peerHost));
		}
		
		/****Update My Friendship****/
		updateFriends();
		/****Update My Friendship****/
		/*���friendship����EncounteredNode���myNeighborFriends��¼Ϊ0�����¼
		 * ����Ѿ���¼������Ϣ���и���*/
		/*If My Friendship Contains Encountered Node*/
		if(friends.containsKey(peerHost.getAddress()))
		{		
					
			EncounterUtilityRankRouter friendRouter = (EncounterUtilityRankRouter)peerHost.getRouter();	
			int friendNeighborNumber = friendRouter.getPeopleRankAlgorithn().getFriends().size();
			double friendRank = friendRouter.getPeopleRankAlgorithn().getRank();	
			if(myNeighborFriends.size()==0)
			{
				FriendValue friendFriendValue = new FriendValue(peerHost.getAddress(), friendRank, friendNeighborNumber,friendRouter.getUtilityFor(myHost));
				myNeighborFriends.add(friendFriendValue);
			}
			
			else
			{
				int peerHostAddress = peerHost.getAddress();
				
				boolean isExist = false;
				
				for(FriendValue fv : myNeighborFriends)
				{
					if(fv.getAddress() == peerHostAddress)
					{
						fv.setFriendNum(friendNeighborNumber);
						fv.setRankValue(friendRank);
						fv.setUtility(friendRouter.getUtilityFor(myHost));
						isExist = true;
					}
				}
				
				if(!isExist)
				{
					FriendValue friendFriendValue = new FriendValue(peerHost.getAddress(), friendRank, friendNeighborNumber,friendRouter.getUtilityFor(myHost));
					myNeighborFriends.add(friendFriendValue);
				}
			}		
		
			calculatePeopleRank();		
		}
	}
	
	//friends����
	/*��friends�������������ֵ���дӵ͵�������
	 * ��������õķŽ�ȥ*/
	
	public void updateFriends()
	{
		List<Map.Entry<Integer, Double>> infoIds = new ArrayList<Map.Entry<Integer, Double>>
		( neighbors.entrySet());
		
		/*Sort Friends According to Number of Encounters*/
		Collections.sort(infoIds, new Comparator<Map.Entry<Integer, Double>>() 
		{ 
			public int compare(Map.Entry<Integer, Double> o1,  Map.Entry<Integer, Double> o2) 
			{ 
				double result = o1.getValue() - o2.getValue();
				if(result > 0)
					return 1;
				else if(result == 0)
					return 0;
				else 
				    return -1;
				     
				
			} 
		}
		); 
		
		/*Select Number of Friends According to Configuration*/
		int friendNumber = infoIds.size();
		
		if(friendNumber > friend_number_factor)
			friendNumber = friend_number_factor;
		
		
		friends.clear();
		
		for(int i=0; i<friendNumber; i++)
		{
			friends.put(infoIds.get(i).getKey(), infoIds.get(i).getValue());			
		}
	}

}
