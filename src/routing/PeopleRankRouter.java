/**
 * PeopleRank use the algorithm depicted in paper.
 * 
 */
package routing;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import routing.peoplerankutil.FriendValue;
import routing.peoplerankutil.PeopleRankAlgorithmUtil;


import core.Connection;
import core.DTNHost;
import core.Message;
import core.MessageListener;
import core.Settings;

public class PeopleRankRouter extends ActiveRouter 		
{
	private PeopleRankAlgorithmUtil peopleRankAlgorithn;
	
	public PeopleRankAlgorithmUtil getPeopleRankAlgorithn() 
	{
		return peopleRankAlgorithn;
	}


	public PeopleRankRouter(Settings s)
	{
		super(s);
		//fManager = new FriendManager(true, this.getHost(), this);
	}

	public PeopleRankRouter(ActiveRouter r)
	{
		super(r);
		//fManager = new FriendManager(true, this.getHost(), this);
	}

	@Override
	public PeopleRankRouter replicate()
	{
		return new PeopleRankRouter(this);
	}
	
	@Override
	/*初始化*/
	public void init(DTNHost host, List<MessageListener> mListeners)
	{
		super.init(host,mListeners);
		
		int rank = 0;
		Map<Integer, Integer> neighor = new TreeMap<Integer, Integer>();
		Map<Integer, Integer> friends = new LinkedHashMap<Integer, Integer>();		
		List<FriendValue> myNeighborFriends = new ArrayList<FriendValue>();
		
		peopleRankAlgorithn = new PeopleRankAlgorithmUtil(rank, neighor, friends,myNeighborFriends);
	}
	
	/*获得排名*/
	public double retrieveRank()
	{
		return peopleRankAlgorithn.getRank();
	}

	/*Return True if Successfully transmitted*/
	/*如果成功传输return ture*/
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
	
	/*更新函数 引出UpdateRouting*/
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

		this.updateRouting();
	}
	/*1.获得所有链接
	 *2.遍历所有链接
	 *3.*/
	public void updateRouting()
	{
		List<Connection> connections = getConnections();
		if (connections.size() == 0 || this.getNrofMessages() == 0)
		{
			return;
		}
		
		DTNHost hostA = this.getHost();
		
		for (Connection conn : connections)
		{
			DTNHost hostB = conn.getOtherNode(hostA);
			
			PeopleRankRouter routerB = (PeopleRankRouter)hostB.getRouter();

			List<Message> messages = new ArrayList<Message>(this.getMessageCollection());
			/*根据接受到信息的时间进行排序*/
			this.sortByQueueMode(messages);
			
			/*比较消息排名进行复制*/
			double weightA = this.retrieveRank();
			double weightB = routerB.retrieveRank();

			if (weightB > weightA)
			{
				for (Message message : messages)
				{	
					if(transmitMessage(conn, message))
						return;	 
				}				
			}
		}
	}	
	
	@Override
	/*链接通断更新*/
	public void changedConnection(Connection con)
	{
		if (con.isUp())
		{					
			this.peopleRankAlgorithn.update(this.getHost(), con.getOtherNode(this.getHost()));
		} 
	}
}
