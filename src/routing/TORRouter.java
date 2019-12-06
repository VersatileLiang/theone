package routing;
import java.util.ArrayList;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.Map.Entry;

import movement.Path;
import movement.map.MapNode;
import util.Tuple;
import core.*;

public class TORRouter extends ActiveRouter
{   
	private Set<String> ackedMessageIds;
	public int ReferencePoint;
	public int BuildTrajectoryType;
	
	public Coord TRAStartPoint=null;
	public boolean TrajectoryAssociation = false;
	
	public static final String POINT = "AdvancedMobilityPoint";
	public static final String TYPE = "BuildTrajectoryType";
	public static final String TORRouter_NS = "TORRouter";
	private static final double VariationX = -1;
	private static final double VariationY = -1;

	public TORRouter(Settings s) 
	{
		super(s);
		Settings VDFSettings = new Settings (TORRouter_NS);
		ReferencePoint = VDFSettings.getInt(POINT);
		BuildTrajectoryType = VDFSettings.getInt(TYPE);
	}
	
	protected TORRouter(TORRouter r) 
	{
		super(r);
		this.ackedMessageIds = new HashSet<String>();
		this.ReferencePoint = r.ReferencePoint;
		this.BuildTrajectoryType=r.BuildTrajectoryType;
	}
	
	public boolean createNewMessage(Message msg) 
	{
		makeRoomForNewMessage(msg.getSize());
		msg.setTtl(this.msgTtl);
		addToMessages(msg, true);
		
		msg.FirstLabel=-1;
		msg.FirstDelegation=Integer.MAX_VALUE;
		msg.FirstValue=0;
		
		msg.SecondLabel=-1;
		msg.SecondDelegation=Integer.MAX_VALUE;
		msg.SecondValue=0;

		msg.Delete=-1;
		
		/*Build the Trajectory Path*/
		if(this.getHost().movement.toString().contains("ShortestPathMapBasedMovement"))			
		{
			/****0 = Shortest Path****/
			if(this.BuildTrajectoryType==0)
				getShortestTRA(msg);
			
			/****1 = From the Next Point for Calculation****/
			else if (this.BuildTrajectoryType==1)
				getMobilityTRA(msg);
			
			/****2 = Define the Starting Point for Calculation****/
			else if (this.BuildTrajectoryType==2)
			{
				int PathSize=this.getHost().getPath().getCoords().size();
			    int CurrentSize=(getStartingPoint()+1);
			    int Value=PathSize-CurrentSize;
			    
			    int Input=0;
			    
			    if(this.ReferencePoint<Value)
			    	Input=ReferencePoint;
			    else
			    	Input=Value;
			    
			    getAdvancedMobilityTRA(msg, Input);
			}
			
			else
			{
				/*Use Shortest Path by Default*/
				getShortestTRA(msg);
			}
		}
	
		else
			System.out.println("The Underlying Mobility Model Is Not Shortest Path");
			
		return true;
	}
	
	/*Find the Next (1st) Point For Trajectory Computing*/
	public int getStartingPoint()
	{
		int ReturnValue=Integer.MAX_VALUE;;
		
		for (int i=0; i<=(this.getHost().getPath().getCoords().size()-1);i++)
        {
			/*The Next Point A Node Will Move*/
			if(this.getHost().getPath().getCoords().get(i).equals(this.getHost().destination))
				ReturnValue=i;
        }
		
		return ReturnValue;
	}
	
	
	public void getShortestTRA(Message M)
	{
		Coord Start1=this.findStartingPath().get(0);
		Coord Start2=this.findStartingPath().get(1);
			
		List<Coord> FirstList =this.getHost().movement.getTRApath(Start1, M.getTo().getLocation());
		List<Coord> SecondList=this.getHost().movement.getTRApath(Start2, M.getTo().getLocation()) ;

		double FirstValue =this.getHost().getLocation().distance(Start1);	
		double SecondValue=this.getHost().getLocation().distance(Start2);

		List<Coord> NewList=this.ComparePath(FirstList, SecondList, FirstValue, SecondValue);	
		 
		/****Must Use Clone!!!, as Without It, The Coord is Real-time****/
		M.msgTRA.add(this.getHost().getLocation().clone());

		for(Coord C: NewList)	
		{	
			M.msgTRA.add(C);
		}
	}
	
	public void getAdvancedMobilityTRA(Message M, int Input)
	{
		M.msgTRA.add(this.getHost().getLocation().clone());
		
		for (int i=getStartingPoint(); i<(getStartingPoint()+Input); i++)
		{
			M.msgTRA.add(this.getHost().path.getCoords().get(i));
		}
		
		Coord StartingCoord=this.getHost().getPath().getCoords().get(getStartingPoint()+Input);
		
		List<Coord> NewList=this.getHost().movement.getTRApath(StartingCoord.clone(), M.getTo().getLocation().clone()) ;
		
		for(Coord C: NewList)	
		{	
			M.msgTRA.add(C);
		}
	}
	
	public void getMobilityTRA(Message M)
	{
		/****Must Use Clone!!!, As Without It, the Coord is Realtime for Mobile Node****/
		M.msgTRA.add(this.getHost().getLocation().clone());
		
		List<Coord> NewList=this.getHost().movement.getTRApath(this.getHost().destination.clone(), M.getTo().getLocation().clone()) ;
		
		for(Coord C: NewList)
		{
			M.msgTRA.add(C);
		}
		
		//System.out.println(msg.msgTRA);
		//System.out.println(this.getHost());
		//System.out.println(this.getHost().getLocation());
		//System.out.println(this.findstartpath());
		//System.out.println(this.getHost.destination);
		//System.out.println("---------------");
		//System.out.println(this.getHost().getPath().toString());
		//System.out.println("---------------");
	}
	
	public List<Coord> findStartingPath()
	{
		List<MapNode> MapNodeList=this.getHost().movement.getAllMapNodes();
		
		Coord LocalLocation=this.getHost().getLocation();
		List<Coord> CoordNodeList=new ArrayList<Coord>();
		
		for(MapNode MP: MapNodeList)
		{
			for(MapNode Neighbor: MP.getNeighbors())
			{
				double Distance1 = MP.getLocation().distance(Neighbor.getLocation());
				double Distance2 = MP.getLocation().distance(LocalLocation);
				double Distance3 = Neighbor.getLocation().distance(LocalLocation);

				//When The Node Is On The Path*/
			    if(Math.abs(Distance2+Distance3-Distance1)<0.000001)	
			    {
				   CoordNodeList.add(MP.getLocation());
				   CoordNodeList.add(Neighbor.getLocation());
				   
				   return CoordNodeList;
			    }
			}
		}
		
		return null;
	}
	
	/*Get The Start Point for Trajectory Based Forwarding*/
	public List<Coord> ComparePath(List<Coord> List1, List<Coord> List2, double V1, double V2)
	{
		double Sum1=0;
		double Sum2=0;
		
		/*Must Use "<"*/
		for(int i=0; i<(List1.size()-1);i++)
		{
			Coord C1=List1.get(i);
			Coord C2=List1.get(i+1);
			
			Sum1=Sum1+C1.distance(C2);
		}
		
		for(int i=0; i<(List2.size()-1);i++)
		{
			Coord J1=List2.get(i);
			Coord J2=List2.get(i+1);
			Sum2=Sum2+J1.distance(J2);
		}
		
		if((Sum1+V1)<(Sum2+V2))	 
			return List1;
		else
			return List2;
	}

	
	/*X1 = First Point,  X2 = Second Point*/
	public double getRelativeAngle(double X1, double Y1, double X2, double Y2)
	{
		double TX=this.getHost().getLocation().getX();
		double TY=this.getHost().getLocation().getY();

		double DistanceA=Math.sqrt((TX-X2)*(TX-X2)+(TY-Y2)*(TY-Y2));
		double DistanceB=Math.sqrt((TX-X1)*(TX-X1)+(TY-Y1)*(TY-Y1));
		double DistanceC=Math.sqrt((X2-X1)*(X2-X1)+(Y2-Y1)*(Y2-Y1));
		
		double ReturnValue=(DistanceB*DistanceB+DistanceC*DistanceC-DistanceA*DistanceA)/(2*DistanceB*DistanceC);
		
		return Math.acos(ReturnValue);
	}
	
	/*X1 = Distance*/
	public double getTriangleAngle(double X1, double X2, double X3)
	{
		double ReturnValue=(X2*X2+X3*X3-X1*X1)/(2*X2*X3);	
		return Math.acos(ReturnValue);
	}
	
	/*Whether A Node Is Associated With Trajectory*/
	public List<Coord> IsWithTrajectory(Message MSG)
	{
		List<Coord> CertainPath = new ArrayList<Coord>();
		
		List<Coord> LinePath = new ArrayList<Coord>();
		
		/*Must Use "<"*/
		for(int i=0;i<MSG.msgTRA.size()-1;i++)
		{
			double X=MSG.msgTRA.get(i).getX();
			double Y=MSG.msgTRA.get(i).getY();
			
			double NextX=MSG.msgTRA.get(i+1).getX();
			double NextY=MSG.msgTRA.get(i+1).getY();
			
			/*From Path Head*/
			double FirstAngle=this.getRelativeAngle(X, Y, NextX, NextY);
			
			/*From Path End*/
			double SecondAngle=this.getRelativeAngle(NextX, NextY, X, Y);
			
			double Distance1 = MSG.msgTRA.get(i).distance(this.getHost().getLocation());
			double Distance2 = MSG.msgTRA.get(i+1).distance(this.getHost().getLocation());
			double Distance3 = MSG.msgTRA.get(i).distance(MSG.msgTRA.get(i+1));

			/*Whether the Node is Following Trajectory*/
			if(Math.abs(Distance1+Distance2-Distance3)<0.000001)	
			{
				LinePath.add(MSG.msgTRA.get(i));
				LinePath.add(MSG.msgTRA.get(i+1));
				
				/*
				System.out.println("the node is along the Trajectory");
				System.out.println(linepath);
				System.out.println(this.getHost());
				System.out.println(this.getHost().getLocation());
				System.out.println(linepath.size()+"size");
                */
				
				return LinePath;
			}
			
			//need to build a triangle, "pi/2" of path end is not considered for path end
			else if(
					((Distance1+Distance2)>Distance3)&&
					((Distance1+Distance3)>Distance2)&&
					((Distance3+Distance2)>Distance1)&&
					((FirstAngle<=(Math.PI/2))&&(SecondAngle<(Math.PI/2)))
			       )
			{
				CertainPath.add(MSG.msgTRA.get(i));
			    CertainPath.add(MSG.msgTRA.get(i+1));
			}
		}
		
		if(CertainPath.size()==0)
		{
			//System.out.println("cant find my location along a path");
			//System.out.println(this.getHost());
			//System.out.println(this.getHost().getLocation());
			//System.out.println(msg.msgTRA);
			//System.out.println("-------------------");
			return null;
		}
		
		else if (CertainPath.size()>2)
		{
			List<Coord> ReturnPath = new ArrayList<Coord>();
			
			double MaxAngle=0;
			int RecordPoint=0;
		    
			/*Must Use "<",  The Largest Angle Determines The Trajectory*/
			for(int i=0; i<(CertainPath.size()-1);i++)
			{
				double Distance1=CertainPath.get(i).distance(CertainPath.get(i+1));
				double Distance2=this.getHost().getLocation().distance(CertainPath.get(i));
				double Distance3=this.getHost().getLocation().distance(CertainPath.get(i+1));
				
				double Angle=this.getTriangleAngle(Distance1, Distance2, Distance3);
				
				if(Angle>MaxAngle)
					RecordPoint=i;
			}
			
			ReturnPath.add(CertainPath.get(RecordPoint));
			ReturnPath.add(CertainPath.get(RecordPoint+1));
			
			return ReturnPath;
		}
		
		else if (CertainPath.size()==2)
		{		
			return CertainPath;
		}
		
		return null;
	}
	/*
	public void deletelastTRA(Message m)
	{
		Coord c0=m.msgTRA.get(0);
		Coord c1=m.msgTRA.get(1);
		
		double x0=c0.getX();
		double y0=c0.getY();
		
		double x1=c1.getX();
		double y1=c1.getY();
		
		if(getcalangle(x1,y1,x0,y0)>=Math.PI/2)
		{
			m.msgTRA.remove(0);
			List<Coord> newmsglist= new ArrayList<Coord>(m.msgTRA);

			m.msgTRA.clear();
			
			for(Coord c: newmsglist)
			{
				m.msgTRA.add(c);
			}
		}
	}
	*/
	
	/*Vertical Distance To a Trajectory Edge*/
	public double getVerticalDistance(List<Coord> Pathlist)
	{
		double Distance1=Pathlist.get(0).distance(this.getHost().getLocation());
		double Distance2=Pathlist.get(1).distance(this.getHost().getLocation());
		double Distance3=Pathlist.get(0).distance(Pathlist.get(1));

		double P = (Distance1+Distance2+Distance3)/2;
		double Area = Math.sqrt(P*(P-Distance1)*(P-Distance2)*(P-Distance3));

		double VerDistance=2*Area/Distance3;
		
		if(VerDistance<0.000001)
			VerDistance=0;
		
		return VerDistance;
	}
	
	public double getParallelForwardDistance(List<Coord> PathList)
	{
		double X = this.getVerticalDistance(PathList);
		double Distance = PathList.get(1).distance(this.getHost().getLocation());
		double Pal = Math.sqrt(Distance*Distance-X*X);

		return Pal;
	}
	
	public double getParallelBackwardDistance(List<Coord> PathList)
	{
		double X = this.getParallelForwardDistance(PathList);
		double Distance = PathList.get(0).distance(PathList.get(1));
		double Pal = Distance-X;

		return Pal;
	}
	
	public boolean checkTriangleAngle(List<Coord> Input)
	{
		Coord Ver=this.getVerticalCoord(Input);
		
		double VAngle=this.getMovingAngle(Ver.getX(), Ver.getY());
		double PAngle=this.getMovingAngle(Input.get(1).getX(), Input.get(1).getY());
		
		double Distance1 = Ver.distance(Input.get(1));
		double Distance2 = this.getHost().getLocation().distance(Ver);
		double Distance3 = this.getHost().getLocation().distance(Input.get(1));
		
		//angle point to distance1
		double MaxVAngle=this.getTriangleAngle(Distance1, Distance2, Distance3);
		double MaxPAngle=(Math.PI/2)-MaxVAngle;
		
		if((VAngle<=MaxVAngle)&&(PAngle<=MaxPAngle))
			return true;
		else
			return false;
	}
	
	public double getTrajectoryDiversity(List<Coord> Input)
	{
		double Angle=this.getMovingAngle(Input.get(1).getX(), Input.get(1).getY());
		return Angle;
	}

	/*Relative Angle With a Certain Trajectory Edge*/
	public double getMaxTrajectoryDiversity(List<Coord> Input)
	{
		Coord Ver=this.getVerticalCoord(Input);
		
		double Distance1=Ver.distance(Input.get(1));
		double Distance2=this.getHost().getLocation().distance(Ver);
		double Distance3=this.getHost().getLocation().distance(Input.get(1));
		
		double MaxVerAngle=this.getTriangleAngle(Distance1, Distance2, Distance3);
		double MaxPalAngle=(Math.PI/2)-MaxVerAngle;
		
		return Math.min(MaxVerAngle, MaxPalAngle);
	}
	
	
	/*Find Coord That is Vertical to Input*/
	public Coord getVerticalCoord(List<Coord> Input)
	{
		if(Input.size()>2)
			System.out.println("Error: Path Size is Larger Than 2");
		
		Coord ReturnCoord; 
		
		double DX = Input.get(0).getX()-Input.get(1).getX();
		double DY = Input.get(0).getY()-Input.get(1).getY();
		
		if((Math.abs(DX)<0.00000001)&&(Math.abs(DY)<0.00000001))
		{
			ReturnCoord = new Coord(Input.get(0).getX(),Input.get(0).getY());
		}
		
		else
		{
			double U = (this.getHost().getLocation().getX()-Input.get(0).getX())*
					   (Input.get(0).getX()-Input.get(1).getX())+
					   (this.getHost().getLocation().getY()-Input.get(0).getY())*
					   (Input.get(0).getY()-Input.get(1).getY());
			
			U = U/((DX*DX)+(DY*DY));
			
			double RX = Input.get(0).getX()+U*DX;
			double RY = Input.get(0).getY()+U*DY;
			
			ReturnCoord = new Coord(RX,RY);
		}
	
		//System.out.println(this.getHost());
		//System.out.println(this.getHost().getLocation());
		//System.out.println(input);
		//System.out.println(ReturnCoord);
		//System.out.println("-----------------");
		
	    return ReturnCoord;
		
	}
	
	public void update() 
	{
		super.update();
		
		if (!canStartTransfer() || isTransferring()) 
			return; // nothing to transfer or is currently transferring 
		
		/* try messages that could be delivered to final recipient */
		if (exchangeDeliverableMessages() != null) 
			return;
		
		tryOtherMessages();
	}
		
	/*If Node Close to Starting Point of Trajectory*/
	public boolean checkStartPointAngle(Message M)
	{
		Coord C1 = M.msgTRA.get(0);
		Coord C2 = M.msgTRA.get(M.msgTRA.size()-1);
		
		if(this.getHost().getLocation().distance(C1)<this.getHost().getLocation().distance(C2))
			return true;
		else
			return false;
	}

	public  Tuple<Message, Connection> tryOtherMessages() 
	{
		Collection<Message> msgCollection = getMessageCollection();
		List<Tuple<Message, Connection>> HighPriority = new ArrayList<Tuple<Message, Connection>>(); 
		List<Tuple<Message, Connection>> LowPriority = new ArrayList<Tuple<Message, Connection>>(); 
		List<Tuple<Message, Connection>> MidPriority = new ArrayList<Tuple<Message, Connection>>(); 

		for(Connection C : getConnections()) 
		{
			DTNHost Other = C.getOtherNode(getHost());
	        TORRouter OtherRouter = (TORRouter)Other.getRouter();
 
		    for (Message M : msgCollection)
		    {  
		    	if (OtherRouter.hasMessage(M.getId())) 
		    		continue;
		    	
		    	else if (OtherRouter.isTransferring()) 
					continue;
		    	
		    	/****Skip Stationary Destination****/
		    	if(super.Skip==true)
				{
		    		if (Other.name.contains("DES"))
						continue; 
				}
		    	/****Skip Stationary Destination****/
				
		    	if ((ackedMessageIds.contains(M.getId()))||(OtherRouter.ackedMessageIds.contains(M.getId())))
		    		continue;
		    
		    	double OtherSpeed=Other.getPath() == null ? 0 : Other.getPath().getSpeed();
		    	double ThisSpeed=this.getHost().getPath() == null ? 0 : this.getHost().getPath().getSpeed();
 	
		    	/*Only If Two Nodes Are Mobile*/
		    	if(this.IsNodeMobile()&&OtherRouter.IsNodeMobile())
		    	{
		    		if(this.IsWithTrajectory(M)!=null)
		    			this.TrajectoryAssociation = true;
		    		else
		    			this.TrajectoryAssociation = false;
		    		
		    		if(OtherRouter.IsWithTrajectory(M)!=null)
		    			OtherRouter.TrajectoryAssociation = true;
		    		else
		    			OtherRouter.TrajectoryAssociation = false;
		    		
		    		
		    		/*Both Two Nodes Are Not With Message Trajectory*/
		    		if((!TrajectoryAssociation)&&(!OtherRouter.TrajectoryAssociation))	
		    		{
		    			/*Both of Nodes Are Close to Trajectory Starting Point*/
		    			if((this.checkStartPointAngle(M)==true)&&(OtherRouter.checkStartPointAngle(M)==true))
		    			{
		    				double TrueX=M.msgTRA.get(0).getX();
		    				double TrueY=M.msgTRA.get(0).getY();

		    				if ((OtherRouter.getTrajectoryProximity(TrueX, TrueY)<Math.PI/2)&&(OtherRouter.getTrajectoryProximity(TrueX, TrueY)<M.FirstDelegation))	
		    				{
		    					M.FirstLabel=1;
		    					M.FirstValue = OtherRouter.getTrajectoryProximity(TrueX, TrueY);
		    					LowPriority.add(new Tuple<Message, Connection>(M, C));
		    				}
		    			}
		    		
		    			/*Both of Nodes Are Close to Trajectory Ending Point*/
		    			else if((this.checkStartPointAngle(M)==false)&&(OtherRouter.checkStartPointAngle(M)==false))
		    			{
		    				double FalseX = M.msgTRA.get(M.msgTRA.size()-1).getX();
		    				double FalseY = M.msgTRA.get(M.msgTRA.size()-1).getY();
		    			
		    				if ((OtherRouter.getTrajectoryProximity(FalseX, FalseY)<Math.PI/2)&&(OtherRouter.getTrajectoryProximity(FalseX, FalseY)<M.SecondDelegation))
		    				{
		    					M.SecondLabel=1;
		    					M.SecondValue = OtherRouter.getTrajectoryProximity(FalseX, FalseY);
		    					LowPriority.add(new Tuple<Message, Connection>(M, C));
		    				}
		    			}
		    		
		    			else if((this.checkStartPointAngle(M)==false)&&(OtherRouter.checkStartPointAngle(M)==true))
		    			{}
		    		
		    			else if((this.checkStartPointAngle(M)==true)&&(OtherRouter.checkStartPointAngle(M)==false))
		    			{}
		    		}
		    	
		    		/*Only Encountered Node Is With Trajectory*/
		    		/*This Rarely Happens*/
		    		else if((!TrajectoryAssociation)&&(OtherRouter.TrajectoryAssociation))	
		    		{
		    			List<Coord> TargetInput = new ArrayList<Coord>();
		    	
		    			for(Coord X:OtherRouter.IsWithTrajectory(M))	
		    			{
		    				TargetInput.add(X);	
		    			}
		    		
		    			double OtherMovingAngle=OtherRouter.getMovingAngle(TargetInput.get(1).getX(), TargetInput.get(1).getY());

		    			if(
		    			(OtherRouter.getTrajectoryDiversity(TargetInput)<OtherRouter.getMaxTrajectoryDiversity(TargetInput))&&
		    			(OtherRouter.getVerticalDistance(TargetInput)!=0)&&
		    		    (OtherRouter.checkTriangleAngle(TargetInput)==true)
		    		    )
		    			{
		    				MidPriority.add(new Tuple<Message, Connection>(M, C));
		    			}
		    			
		    			else if(
		    			(OtherMovingAngle<Math.PI/2)&&
		    			(OtherRouter.getVerticalDistance(TargetInput)==0)
		    		    )
		    			{	
		    				HighPriority.add(new Tuple<Message, Connection>(M, C));		
		    			}		
		    		}
		    	
		    		
		    		/*Both Two Nodes Are With Same Trajectory*/
		    		else if ((TrajectoryAssociation)&&(OtherRouter.TrajectoryAssociation)&&(OtherRouter.IsWithTrajectory(M).equals(this.IsWithTrajectory(M))))	
		    		{
		    			List<Coord> Input = new ArrayList<Coord>();

		    			for(Coord X:this.IsWithTrajectory(M))	
		    			{
		    				Input.add(X);	
		    			}

		    			/*Both Nodes Do Not Exactly Follow Trajectory*/
		    			if((this.getVerticalDistance(Input)!=0)&&(OtherRouter.getVerticalDistance(Input)!=0))
		    			{
		    				if(
		    				(OtherRouter.checkTriangleAngle(Input)==true)&&
		    			    (OtherRouter.getTrajectoryDiversity(Input)<OtherRouter.getMaxTrajectoryDiversity(Input))
		    			    )
		    				{
		    					MidPriority.add(new Tuple<Message, Connection>(M, C));   
		    				}
		    			}
	

		    			/*Both Nodes Exactly Follow Trajectory*/
		    			else if((this.getVerticalDistance(Input)==0)&&(OtherRouter.getVerticalDistance(Input)==0))
		    			{
		    				double ThisMovingAngle=this.getMovingAngle(Input.get(1).getX(),Input.get(1).getY());
		    				double OtherMovingAngle=OtherRouter.getMovingAngle(Input.get(1).getX(), Input.get(1).getY());

		    				if((ThisMovingAngle<Math.PI/2)&&(OtherMovingAngle<Math.PI/2)&&(ThisSpeed<OtherSpeed))
		    				{
		    					M.Delete=1;
		    					HighPriority.add(new Tuple<Message, Connection>(M, C));
		    				}
		    			
		    				if ((ThisMovingAngle>=Math.PI/2)&&(OtherMovingAngle<Math.PI/2))
		    				{
		    					HighPriority.add(new Tuple<Message, Connection>(M, C));
		    				}
		    			}
		    			
		    			/*Only Encountered Node Follows Trajectory*/
		    			else if((this.getVerticalDistance(Input)!=0)&&(OtherRouter.getVerticalDistance(Input)==0))
		    			{
		    				double OtherMovingAngle=OtherRouter.getMovingAngle(Input.get(1).getX(), Input.get(1).getY());

		    				if(OtherMovingAngle<(Math.PI/2))
		    				{
		    					HighPriority.add(new Tuple<Message, Connection>(M, C));
		    				}
		    			}
		    		
		    			/*Only Message Carrier Follows Trajectory*/
		    			else if((this.getVerticalDistance(Input)==0)&&(OtherRouter.getVerticalDistance(Input)!=0))
		    			{
		    				if(
		    				(OtherRouter.getTrajectoryDiversity(Input)<OtherRouter.getMaxTrajectoryDiversity(Input))&&
		    				(OtherRouter.checkTriangleAngle(Input)==true)
		    				&&
		    			    (getMovingAngle(Input.get(1).getX(), Input.get(1).getY())>=(Math.PI/2))
		    			    )
		    				{
		    					MidPriority.add(new Tuple<Message, Connection>(M, C));
		    				}
		    			}
		    		}
		    		
		    		/****Both Have Different Road Segment****/
			    	else if ((this.TrajectoryAssociation)&&(OtherRouter.TrajectoryAssociation)&&(!OtherRouter.IsWithTrajectory(M).equals(this.IsWithTrajectory(M))))
			    	{
			    		if(/*Find The Node With Closest Index of Trajectory*/
			    		(getTrajectoryForwardingProgress(IsWithTrajectory(M), M)<OtherRouter.getTrajectoryForwardingProgress(OtherRouter.IsWithTrajectory(M), M))&&
			    		(OtherRouter.checkTriangleAngle(OtherRouter.IsWithTrajectory(M))==true)
			    		)
			    		{
			    			List<Coord> TargetInput = new ArrayList<Coord>();
					    	
			    			for(Coord X:OtherRouter.IsWithTrajectory(M))	
			    			{
			    				TargetInput.add(X);	
			    			}
			    		
			    			double OtherMovingAngle=OtherRouter.getMovingAngle(TargetInput.get(1).getX(), TargetInput.get(1).getY());

			    			if(
			    			(OtherRouter.getTrajectoryDiversity(TargetInput)<OtherRouter.getMaxTrajectoryDiversity(TargetInput))&&
			    			(OtherRouter.getVerticalDistance(TargetInput)!=0)&&
			    		    (OtherRouter.checkTriangleAngle(TargetInput)==true)
			    		    )
			    			{
			    				MidPriority.add(new Tuple<Message, Connection>(M, C));
			    			}
			    			
			    			else if(
			    			(OtherMovingAngle<Math.PI/2)&&
			    			(OtherRouter.getVerticalDistance(TargetInput)==0)
			    		    )
			    			{	
			    				HighPriority.add(new Tuple<Message, Connection>(M, C));		
			    			}		
			    		}
			    	}
		    	}
		    }
		}
		
		Collections.sort(LowPriority, new HighTupleComparator());	
		Collections.sort(MidPriority, new MidTupleComparator());	
		Collections.sort(HighPriority, new LowTupleComparator());	
		 
		if(tryMessagesForConnected(HighPriority)!=null) 
			return tryMessagesForConnected(HighPriority);
		else if(tryMessagesForConnected(MidPriority)!=null) 
			return tryMessagesForConnected(MidPriority);
		else
			return tryMessagesForConnected(LowPriority);   
	}
	
	@Override
	protected Message getOldestMessage(boolean excludeMsgBeingSent)
	{
		Collection<Message> Messages = this.getMessageCollection();
		List<Message> HighBin = new ArrayList<Message>();
		List<Message> MidBin = new ArrayList<Message>();
		List<Message> LowBin = new ArrayList<Message>();
		
		if(!this.IsNodeMobile())
			System.out.println("Error: Node Stopped");
		
		{
    		for (Message M : Messages) 	
    		{		
    			if(this.IsWithTrajectory(M)!=null)
    			{
    				if(this.getVerticalDistance(IsWithTrajectory(M))!=0)
    					MidBin.add(M);
    				else
    					HighBin.add(M);
    			}
			
    			else
    				LowBin.add(M);	
    		}
		}
		
		Collections.sort(HighBin,new HighDropComparator());		
		Collections.sort(MidBin,new MidDropComparator());	
		Collections.sort(LowBin,new LowDropComparator());	

		if(LowBin.size()!=0)
			return LowBin.get(LowBin.size()-1);
		else if (MidBin.size()!=0)
			return MidBin.get(MidBin.size()-1);
		else
			return HighBin.get(HighBin.size()-1);
	}
	
	private class HighDropComparator implements Comparator<Message> 
	{	
		public  HighDropComparator()	
		{}
			
		public int compare(Message Msg1, Message Msg2) 
		{	     	
			double Priority1=0; 	
			double Priority2=0;

			double ThisSpeed=getHost().getPath() == null ? 0 : getHost().getPath().getSpeed();
		 	
			Priority1 = Msg1.getTtl()*60-getHost().getLocation().distance(getHost().destination.clone())/ThisSpeed;
			Priority2 = Msg2.getTtl()*60-getHost().getLocation().distance(getHost().destination.clone())/ThisSpeed;
		
			return (int)(Priority2-Priority1);
		}		
	}
	
	private class LowDropComparator implements Comparator<Message> 
	{	
		public  LowDropComparator()	
		{}
			
		public int compare(Message Msg1, Message Msg2) 
		{	     	
			double Priority1=0; 	
			double Priority2=0;

			/*Msg1*/
			if(checkStartPointAngle(Msg1))
				Priority1 = Msg1.getTtl()/Msg1.FirstDelegation;
			else
				Priority1 = Msg1.getTtl()/Msg1.SecondDelegation;
			
			/*Msg2*/
			if(checkStartPointAngle(Msg2))
				Priority2 = Msg2.getTtl()/Msg2.FirstDelegation;			
			else
				Priority2 = Msg2.getTtl()/Msg2.SecondDelegation;
			
			return (int)(Priority2-Priority1);
		}		
	}
	
	private class MidDropComparator implements Comparator<Message> 
	{	
		public  MidDropComparator()	
		{}
			
		public int compare(Message Msg1, Message Msg2) 
		{	     	
			double Priority1=0; 	
			double Priority2=0;

			Priority1 = Msg1.getTtl()/getTrajectoryDiversity(IsWithTrajectory(Msg1));
			Priority2 = Msg2.getTtl()/getTrajectoryDiversity(IsWithTrajectory(Msg2));
			
			return (int)(Priority2-Priority1);
		}		
	}
	
	
	public int getTrajectoryForwardingProgress(List<Coord> List, Message MSG)
	{
		Coord TargetCoord=List.get(1);
		
		for(int i=0; i<=(MSG.msgTRA.size()-1); i++)
		{
			if(MSG.msgTRA.get(i).equals(TargetCoord))
				return i;
		}
		
		System.out.println("Error: No Point");
		return Integer.MAX_VALUE;
	}
	
	public double getTrajectoryDistance(Message m, List<Coord> input)
	{
		double sum=0;
		int record=0;

		for(int i=0;i<m.msgTRA.size()-1;i++)
		{
			Coord c1=m.msgTRA.get(i);
			
			if(c1.equals(input.get(1)))
			{
				record=i;
			}
		}
		
		if(record==(m.msgTRA.size()-1))
			return 0;
	
		for(int i=record;i<(m.msgTRA.size()-1);i++)
		{
			Coord c1=m.msgTRA.get(i);
			Coord c2=m.msgTRA.get(i+1);
		
			sum=sum+c1.distance(c2);
		}
		
		return sum;
	}
  
	@Override
	public TORRouter replicate()     
	{
		   return new TORRouter(this); 
	}

	public double getTrajectoryProximity(double DesX, double DesY)
	{
		if(this.getHost().path!=null)
		{
			Path ThisPath=this.getHost().path;
			int PathSize=ThisPath.getCoords().size();
			int Number=0;
			double SumAngle=0;
			
			for(int i=getStartingPoint(); i<=(PathSize-2); i++)
			{
				double X=ThisPath.coords.get(i).getX();
				double Y=ThisPath.coords.get(i).getY();
				
				double NX = ThisPath.coords.get(i+1).getX();
				double NY = ThisPath.coords.get(i+1).getY();
				
				SumAngle=SumAngle+this.getAverageMovingAngle(DesX, DesY, X, Y, NX, NY);
				Number++;
			}
			
			return SumAngle/Number;
		}
		
		return Integer.MAX_VALUE;
	}
	
	private double getAverageMovingAngle(double DesX, double DesY, double TargetX, double TargetY, double NX, double NY)
	{
		double MoveAngle=0;   
		
		double DesChangeX=DesX-TargetX;	   
		double DesChangeY=DesY-TargetY;   

		double NewChangeX=NX-TargetX;	   
		double NewChangeY=NY-TargetY;
		
		if((NewChangeX==0)&&(NewChangeY==0))  
		{
			/*System.out.println("node is stopped");*/
		}
		
		else if(((NewChangeX*DesChangeX)<=0)&&((NewChangeY*DesChangeY)>=0))   
		{
			MoveAngle=this.getSin(NewChangeX,NewChangeY)+  
			this.getSin(DesChangeX,DesChangeY);
				    
			if(MoveAngle>Math.PI)
				System.out.println("angle is larger than 180"); 
		}
			        
		else if(((NewChangeX*DesChangeX)>=0)&&((NewChangeY*DesChangeY)>=0))   
		{
			MoveAngle=this.getCos(NewChangeX,NewChangeY)-   
			this.getCos(DesChangeX,DesChangeY);  
		
			MoveAngle=Math.abs(MoveAngle);
					      
			if(MoveAngle>Math.PI)			 
				System.out.println("angle is larger than 180"); 			         
		}
			         
		else if(((NewChangeX*DesChangeX)<=0)&&((NewChangeY*DesChangeY)<=0))     
		{           	
			MoveAngle=this.getSin(NewChangeX,NewChangeY)	    
			+(0.5*Math.PI)+ 	        
			this.getCos(DesChangeX,DesChangeY);
			    	            	     
			if (MoveAngle>Math.PI)      
			{     
				MoveAngle=(2*Math.PI)-MoveAngle;      
			}  
			
			if(MoveAngle>Math.PI)    	      
				System.out.println("angle is larger than 180");        	      
		}
					    
		else if(((NewChangeX*DesChangeX)>=0)&&((NewChangeY*DesChangeY)<=0))     
		{
			MoveAngle=this.getCos(NewChangeX,NewChangeY)+       
			this.getCos(DesChangeX,DesChangeY);

			if(MoveAngle>Math.PI)   
				System.out.println("angle is larger than 180");    
		} 
		
		return MoveAngle;  	   
	}
		

	public  void updateMsgDelegation(DTNHost host)
	{	
		Collection<Message> c1 = this.getMessageCollection();
		TORRouter otherRouter =(TORRouter)host.getRouter();
		Collection<Message> c2 = otherRouter.getMessageCollection();

		for (Message m1:c1)
		{
			for (Message m2:c2)
			{	
				if(m1.getId().equals(m2.getId()))
				{
					double FirstThisValue=getMessage(m1.getId()).FirstDelegation;	
					double FirstOtherValue=otherRouter.getMessage(m2.getId()).FirstDelegation;
					double MinFirst=Math.min(FirstThisValue, FirstOtherValue);

					double SecondThisValue=getMessage(m1.getId()).SecondDelegation;	
					double SecondOtherValue=otherRouter.getMessage(m2.getId()).SecondDelegation;
				
					double MinSecond=Math.min(SecondThisValue, SecondOtherValue);

					if(m1.getId().equals(m2.getId()))
					{  
						getMessage(m2.getId()).FirstDelegation=MinFirst;
						otherRouter.getMessage(m1.getId()).FirstDelegation=MinFirst;

						getMessage(m2.getId()).SecondDelegation=MinSecond;
						otherRouter.getMessage(m1.getId()).SecondDelegation=MinSecond;
				
					}		
				}
		
			}
		}
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

	public void changedConnection(Connection con) 
	{		
		if (con.isUp())  
		{	
			DTNHost otherHost = con.getOtherNode(getHost());	
			updateMsgDelegation(otherHost);	
			TORRouter OtherRouter= (TORRouter) otherHost.getRouter();
			
			this.ackedMessageIds.addAll(OtherRouter.ackedMessageIds);	
			OtherRouter.ackedMessageIds.addAll(this.ackedMessageIds);
	
			deleteAckedMessages();
			OtherRouter.deleteAckedMessages();
		}

		else if (!con.isUp())  
		{}
	}


	protected void transferDone(Connection con) 
	{
		String msgId = con.getMessage().getId();
		Message msg = getMessage(msgId);
       
		if (msg == null)  // message has been dropped from the buffer after..
			return; // ..start of transfer -> no need to reduce amount of copies
            
		if (msg.getTo() == con.getOtherNode(getHost()))   
		{ 
			this.ackedMessageIds.add(msg.getId()); // yes, add to ACKed messages
			this.deleteMessage(msg.getId(), false); // delete from buffer  
		}
			
		else	   
		{
			if(msg.Delete==1)
				this.deleteMessage(con.getMessage().getId(), false);
			
			if(msg.FirstLabel==1)  
			{
				msg.FirstDelegation=msg.FirstValue;
				msg.FirstLabel=-1;
			}  
			
			else if(msg.SecondLabel==1)  
			{
				msg.SecondDelegation=msg.SecondValue;
				msg.SecondLabel=-1;
			} 
		}
	
	}

	protected void transferAborted(Connection con) 
	{ 	
		String msgId = con.getMessage().getId();
			
		/* get this router's copy of the message */
		Message msg = getMessage(msgId);

		if (msg == null)  // message has been dropped from the buffer after..
			return; // ..start of transfer -> no need to reduce amount of copies

		this.getMessage(msg.getId()).Delete=-1;		
		this.getMessage(msg.getId()).FirstLabel=-1;
		this.getMessage(msg.getId()).SecondLabel=-1;	
	}
		
	public Message messageTransferred(String id, DTNHost from) 	
	{
		Message msg = super.messageTransferred(id, from);
		
		if(msg.Delete==1)  
		{
			msg.Delete=-1;
		} 
		
		if(msg.FirstLabel==1)  
		{
			msg.FirstDelegation=msg.FirstValue;
			msg.FirstLabel=-1;
		}  
		
		else if(msg.SecondLabel==1)  
		{
			msg.SecondDelegation=msg.SecondValue;
			msg.SecondLabel=-1;
		} 
		
		if (isDeliveredMessage(msg))   
		{  	
			this.ackedMessageIds.add(id);
		}
		
		return msg;
	}

	private class LowTupleComparator implements Comparator <Tuple<Message, Connection>> 
	{
		public int compare(Tuple<Message, Connection> tuple1,Tuple<Message, Connection> tuple2) 
		{
			Message M1 = tuple1.getKey();
			Message M2 = tuple2.getKey();
			
			double Priority1,Priority2;
			
			if(M1.FirstLabel==1)
				Priority1=M1.getTtl()/M1.FirstDelegation;
			else
				Priority1=M1.getTtl()/M1.SecondDelegation;
			
			if(M2.FirstLabel==1)
				Priority2=M2.getTtl()/M2.FirstDelegation;
			else
				Priority2=M2.getTtl()/M2.SecondDelegation;

			return (int)(Priority2-Priority1);
		}
	}
	
	private class MidTupleComparator implements Comparator <Tuple<Message, Connection>> 
	{
		public int compare(Tuple<Message, Connection> Tuple1, Tuple<Message, Connection> Tuple2) 
		{
			Message M1 = Tuple1.getKey();
			Message M2 = Tuple2.getKey();
			
			TORRouter R1 = (TORRouter)Tuple1.getValue().getOtherNode(getHost()).getRouter();
			TORRouter R2 = (TORRouter)Tuple2.getValue().getOtherNode(getHost()).getRouter();
			
			double Priority1 = R1.getTrajectoryDiversity(R1.IsWithTrajectory(M1))/M1.getTtl();
			double Priority2 = R2.getTrajectoryDiversity(R2.IsWithTrajectory(M2))/M2.getTtl();

			return (int)(Priority2-Priority1);
		}
	}
	
	private class HighTupleComparator implements Comparator <Tuple<Message, Connection>> 
	{
		public int compare(Tuple<Message, Connection> Tuple1,Tuple<Message, Connection> Tuple2) 
		{
			DTNHost N1 = Tuple1.getValue().getOtherNode(getHost());
			DTNHost N2 = Tuple2.getValue().getOtherNode(getHost());
			
			double Speed1=N1.getPath() == null ? 0 : N1.getPath().getSpeed();
			double Speed2=N2.getPath() == null ? 0 : N2.getPath().getSpeed();
			
			double Distance1 = N1.getLocation().distance(N1.destination);
			double Distance2 = N2.getLocation().distance(N2.destination);
			
			double TTL1 = Tuple1.getKey().getTtl()*60;
			double TTL2 = Tuple2.getKey().getTtl()*60;
			
			double P1 = TTL1-(Distance1/Speed1);
			double P2 = TTL2-(Distance2/Speed2);
			 
			return (int)(P2-P1);
		}
	}
	
	
	/****Geographic Calculation****/
	public double getCos(double XChange, double YChange)
	{    
		double X = Math.abs(XChange);   
		double Y = Math.abs(YChange); 
		double Z = Math.sqrt(X*X+Y*Y);   
		return Math.acos(X/Z);   
	}

	public double getSin(double XChange, double YChange)
	{  
		double X=Math.abs(XChange); 
		double Y=Math.abs(YChange);  
		double Z = Math.sqrt(X*X+Y*Y);  
		return Math.asin(X/Z);
	}
	
	public double getDistanceToDes(double X, double Y)
    {
    	double DistX = X-this.getHost().getLocation().getX();
	    double DistY = Y-this.getHost().getLocation().getY();
	    double Dist = this.getSqrt(DistX, DistY);	    
	    return Dist;
    }

	public double getChangeX()	
	{
		return VariationX;	
	}
		
	public double getChangeY()	
	{
		return VariationY;	
	}

	private double getMovingAngle(double DesX, double DesY)
	{   
		double MoveAngle=Integer.MAX_VALUE;
		
		double DesChangeX=DesX-this.getHost().getLocation().getX();	   
		double DesChangeY=DesY-this.getHost().getLocation().getY();   
		
		if((getChangeX()==0)&&(getChangeY()==0))  
		{
			System.out.println("Node is Stopped");
			System.out.println(this.getHost());
		}

		else if(((this.getChangeX()* DesChangeX)<=0)&&((this.getChangeY()*DesChangeY)>=0))   
		{
			MoveAngle=this.getSin(this.getChangeX(),this.getChangeY())+  
			this.getSin(DesChangeX,DesChangeY);
				    
			if(MoveAngle>Math.PI)
				System.out.println("Angle is Larger Than 180"); 
		}
			        
		else if(((this.getChangeX()*DesChangeX)>=0)&&((this.getChangeY()*DesChangeY)>=0))   
		{
			MoveAngle=this.getCos(this.getChangeX(),this.getChangeY())-   
			this.getCos(DesChangeX,DesChangeY);  
		
			MoveAngle=Math.abs(MoveAngle);
					      
			if(MoveAngle>Math.PI)			 
				System.out.println("Angle is Larger Than 180"); 			         
		}
			         
		else if(((this.getChangeX()* DesChangeX)<=0) &&((this.getChangeY()* DesChangeY)<=0))     
		{  	    
			MoveAngle=this.getSin(this.getChangeX(),this.getChangeY())	    
			+(0.5*Math.PI)
			+ this.getCos(DesChangeX,DesChangeY);
			    	            	     
			if (MoveAngle>Math.PI)      
			{     
				MoveAngle=(2*Math.PI)- MoveAngle;      
			}  
			
			if(MoveAngle>Math.PI)    	      
				System.out.println("Angle is Larger Than 180");        	      
		}
					    
		else if(((this.getChangeX()*DesChangeX)>=0)&&((this.getChangeY()*DesChangeY)<=0))     
		{
			MoveAngle=this.getCos(this.getChangeX(),this.getChangeY())
			+ this.getCos(DesChangeX,DesChangeY);

			if(MoveAngle>Math.PI)   
				System.out.println("Angle is Larger Than 180");    
		} 
		
		return MoveAngle;  	   
	}
    
	public double getSqrt(double XValue, double YValue )  
	{  	
		double X = XValue; 	
		double Y = YValue;	
		double Z = Math.sqrt(X*X+Y*Y);
	    	
		return Z;		
	}
	
	/*Is Node Mobile*/
	public boolean IsNodeMobile()
	{
		double X = this.getChangeX();
		double Y = this.getChangeY();
		
		if((X==0)&&(Y==0))
			return false;
		else
			return true;
	}
	/****Geographic Calculation****/
}