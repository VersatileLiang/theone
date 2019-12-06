package routing.peoplerankutil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import routing.PeopleRankRouter;
import core.DTNHost;

/*Compute PeopleRank Value*/
public class PeopleRankAlgorithmUtil 
{
	/*Setting Value*/
	/*d_factor阀值*/
	private double d_factor = 0.9;
	/*friend阀值*/
	private int friend_number_factor = 10;//排名只排前十名
	/*排名*/
	private double rank;
	
	/*key is Nodal ID*/
	/*Value is Number of Encounters*/
	//相遇的节点数量
	Map<Integer, Integer> neighbors;// = new TreeMap<Integer, Integer>();
	
	/*My Friendship With Given Number of Nodes*/
	//给定节点数量的friends，根据相遇次数前10,如果改的话用效用度前10，从小到大排列
	Map<Integer, Integer> friends; //= new LinkedHashMap<Integer, Integer>();

	/*Information of a Set of My Friends*/
	//一组好友信息
	List<FriendValue> myNeighborFriends;

	/*构造函数*/
	public PeopleRankAlgorithmUtil(double rank,
		   Map<Integer, Integer> neighors, Map<Integer, Integer> friends,
		   List<FriendValue> myNeighborFriends) 
	{
		this.rank = rank;
		this.neighbors = neighors;
		this.friends = friends;
		this.myNeighborFriends = myNeighborFriends;
	}
	/*getfriends*/
	public Map<Integer, Integer> getFriends() 
	{
		return friends;
	}
	/*getRank*/
	public double getRank()
	{
		return rank;
	}
	/*计算排名*/
	/*myNeighborFriends=F(Nj)即与Nj链接的节点集合
	 *myNeighborFriends.get(i).getFriendNum()=F(Nj)
	 *myNeighborFriends.get(i).getRankValue()=PeR(Nj)
	 *通过公式计算rank*/
	
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
	 * 1.如果没相遇过，加入neighbors，标号和相遇次数
	 * 2.如果相遇过，标号和相遇次数
	 * 3.将friends进行从高到低排序
	 * 4.如果friends里有相遇节点的话*/
	public void update(DTNHost myHost,DTNHost peerHost)
	{
			
		/*Did Not Encounter Before*/
		if(!neighbors.containsKey(peerHost.getAddress()))
		{
			neighbors.put(peerHost.getAddress(), 1);
		}
		
		else
		{
			neighbors.put(peerHost.getAddress(), 1+neighbors.get(peerHost.getAddress()));
		}
		
		/****Update My Friendship****/
		updateFriends();
		/****Update My Friendship****/
		/*如果friendship包含EncounteredNode如果myNeighborFriends记录为0，则记录
		 * 如果已经记录过则将信息进行更新*/
		/*If My Friendship Contains Encountered Node*/
		if(friends.containsKey(peerHost.getAddress()))
		{		
			PeopleRankRouter friendRouter = (PeopleRankRouter)peerHost.getRouter();			

			int friendNeighborNumber = friendRouter.getPeopleRankAlgorithn().getFriends().size();
			double friendRank = friendRouter.getPeopleRankAlgorithn().getRank();
				
			if(myNeighborFriends.size()==0)
			{
				FriendValue friendFriendValue = new FriendValue(peerHost.getAddress(), friendRank, friendNeighborNumber);
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
						isExist = true;
					}
				}
				
				if(!isExist)
				{
					FriendValue friendFriendValue = new FriendValue(peerHost.getAddress(), friendRank, friendNeighborNumber);
					myNeighborFriends.add(friendFriendValue);
				}
			}		
		
			calculatePeopleRank();		
		}
	}
	
	//friends排序
	/*将friends根据相遇次数的值进行从高到低排序进行从高到低排列
	 * 将新排序好的放进去*/
	
	public void updateFriends()
	{
		List<Map.Entry<Integer, Integer>> infoIds = new ArrayList<Map.Entry<Integer, Integer>>
		( neighbors.entrySet());
		
		/*Sort Friends According to Number of Encounters*/
		Collections.sort(infoIds, new Comparator<Map.Entry<Integer, Integer>>() 
		{ 
			public int compare(Map.Entry<Integer, Integer> o1,  Map.Entry<Integer, Integer> o2) 
			{ 
				return (o2.getValue() - o1.getValue()); 
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
