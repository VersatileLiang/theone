package routing.encounteruntility;

/*Friend Value*/
public class EncounterUtility {
	
	/*Nodal ID*/
	int address;

	/*RankValue*/
	double rankValue ;
	
	/*Number of Friends of Given Friend*/
	int friendNum ;

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
	
	public EncounterUtility(int address, double rankValue, int friendNum) 
	{
		this.address = address;
		this.rankValue = rankValue;
		this.friendNum = friendNum;
	}
}
