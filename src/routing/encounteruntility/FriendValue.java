package routing.encounteruntility;
/*Friend Value*/
public class FriendValue {
	
	/*Nodal ID*/
	int address;

	/*RankValue*/
	double rankValue ;
	
	/*Number of Friends of Given Friend*/
	int friendNum ;
	/*与当前节点的效用值*/
	double curnodeutility;

	public double getRankValue() 
	{
		return rankValue;
	}
	
	public void setRankValue(double rankValue) 
	{
		this.rankValue = rankValue;
	}
	
	public int getFriendNum() 
	{
		return friendNum;
	}
	
	public void setFriendNum(int friendNum) 
	{
		this.friendNum = friendNum;
	}
	
	public int getAddress() 
	{
		return address;
	}
	
	public void setAddress(int address) 
	{
		this.address = address;
	}
	public double getUtility()
	{
		return curnodeutility;
	}
	public void setUtility(double curnodeutility) 
	{
		this.curnodeutility = curnodeutility;
	}
	
	public FriendValue(int address, double rankValue, int friendNum,double curnodeutility) 
	{
		this.address = address;
		this.rankValue = rankValue;
		this.friendNum = friendNum;
		this.curnodeutility=curnodeutility;
	}
}
