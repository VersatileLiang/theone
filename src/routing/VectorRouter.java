/* 
 * Copyright 2010 Aalto University, ComNet

 * Released under GPLv3. See LICENSE.txt for details. 
 */
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

import routing.ActiveRouter;
import util.Tuple;
import core.*;

/**
 * Energy level-aware variant of Epidemic router.
 */
public class VectorRouter extends ActiveRouter implements ModuleCommunicationListener
{   
	/*---------------energy configuration-----------------*/
	/** Initial units of energy -setting id ({@value}). Can be either a 
	 * single value, or a range of two values. In the latter case, the used
	 * value is a uniformly distributed random value between the two values. */
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
	/*-----------------------------------*/	
	/** identifier for the initial number of copies setting ({@value})*/ 

	public static final String VDF_NS = "VectorRouter";
	public static final String weight = "weightvalue";
	protected int timewin;
	protected double weightvalue;
	
	public double lastlocation =0;
	public double variationx=0;
	public double variationy=0;
	public Map<DTNHost, Integer> vectortable;
	
	
	
	/**
	 * Constructor. Creates a new message router based on the settings in
	 * the given Settings object.
	 * @param s The settings object
	 */
	public VectorRouter(Settings s) 
	{
		super(s);
		initPreds();
		Settings VDFSettings = new Settings (VDF_NS);
		weightvalue = VDFSettings.getDouble(weight);


		/*energy configuration*/
		this.initEnergy = s.getCsvDoubles(INIT_ENERGY_S);
		
		if (this.initEnergy.length != 1 && this.initEnergy.length != 2) 
		   {
			throw new SettingsError(INIT_ENERGY_S + " setting must have " + 
					"either a single value or two comma separated values");
		   }
		
		this.scanEnergy = s.getDouble(SCAN_ENERGY_S);
		this.transmitEnergy = s.getDouble(TRANSMIT_ENERGY_S);
		this.scanInterval  = s.getDouble(SimScenario.SCAN_INTERVAL_S);
		
		if (s.contains(WARMUP_S)) 
		   {
			this.warmupTime = s.getInt(WARMUP_S);
			if (this.warmupTime == -1) 
			   {
				this.warmupTime = new Settings(report.Report.REPORT_NS).
					getInt(report.Report.WARMUP_S);
			   }
		    }
		else 
		    {
			this.warmupTime = 0;
		    }
		/*-----------------------------------*/
	}
	
	/**
	 * Sets the current energy level into the given range using uniform 
	 * random distribution.
	 * @param range The min and max values of the range, or if only one value
	 * is given, that is used as the energy level
	 */
	
	/**
	 * Copy constructor.
	 * @param r The router prototype where setting values are copied from
	 */
	protected VectorRouter(VectorRouter r) 
	{
		super(r);
		initPreds();
		this.timewin=r.timewin;
		this.weightvalue=r.weightvalue;
		/*------------energy configuration---------------*/
		this.initEnergy = r.initEnergy;
		setEnergy(this.initEnergy);
		this.scanEnergy = r.scanEnergy;
		this.transmitEnergy = r.transmitEnergy;
		this.scanInterval = r.scanInterval;
		this.warmupTime  = r.warmupTime;
		this.comBus = null;
		this.lastScanUpdate = 0;
		this.lastUpdate = 0;
		/*-----------------------------------------------*/
	}
	
	
	@Override
	protected int checkReceiving(Message m,DTNHost from) 
	{
		if (this.currentEnergy < 0)   
			return DENIED_UNSPECIFIED;
		   
		else 
		{     
			if (isTransferring()) 
			   return TRY_LATER_BUSY; // only one connection at a time

			if ( hasMessage(m.getId()) || isDeliveredMessage(m) )
			   return DENIED_OLD; // already seen this message -> reject it
					
			if (m.getTtl() <= 0 && m.getTo() != getHost()) 
			   return DENIED_TTL; 
			
			 /* remove oldest messages but not the ones being sent */
	        if (!makeRoomForMessage(m.getSize())) 
			   return DENIED_NO_SPACE; // couldn't fit into buffer -> reject
				
		    return RCV_OK;
		 }
	}
	
	/*reducing the energy*/
	protected void reduceEnergy(double amount) 
	{
		if (SimClock.getTime() < this.warmupTime) 
		   {
			return;
		   }
		
		comBus.updateDouble(ENERGY_VALUE_ID, -amount);
		if (this.currentEnergy < 0) 
		   {
			comBus.updateProperty(ENERGY_VALUE_ID, 0.0);
		   }
	}
	
	protected void setEnergy(double range[]) 
	{
		if (range.length == 1) 
		   {
			this.currentEnergy = range[0];
		   }
		else 
		   {
			if (rng == null) 
			   {
				rng = new Random((int)(range[0] + range[1]));
			   }
			this.currentEnergy = range[0] + 
			rng.nextDouble() * (range[1] - range[0]);
		   }
	}
	
	protected void reduceSendingAndScanningEnergy() 
	{
		double simTime = SimClock.getTime();
		
		if (this.comBus == null) 
		   {
			this.comBus = getHost().getComBus();
			this.comBus.addProperty(ENERGY_VALUE_ID, this.currentEnergy);
			this.comBus.subscribe(ENERGY_VALUE_ID, this);
		   }
		
		if (this.currentEnergy <= 0) 
		   {
			/* turn radio off */
			this.comBus.updateProperty(NetworkInterface.RANGE_ID, 0.0);
			return; /* no more energy to start new transfers */
		   }
		
		if (simTime > this.lastUpdate && sendingConnections.size() > 0) 
		   {
			/* sending data */
			reduceEnergy((simTime - this.lastUpdate) * this.transmitEnergy);
		   }
		this.lastUpdate = simTime;
		
		if (simTime > this.lastScanUpdate + this.scanInterval) 
		   {
			/* scanning at this update round */
			reduceEnergy(this.scanEnergy);
			this.lastScanUpdate = simTime;
		   }
	}
	/*---------------------------------------------------------*/
	
	public boolean createNewMessage(Message msg) 
	{
		makeRoomForNewMessage(msg.getSize());
		msg.setTtl(this.msgTtl);
		
		msg.updateMetNode(getHost());
		/*delegation information for message*/
		addToMessages(msg, true);
		
		msg.drop=(int) this.getmovingdirection();
		msg.delegation=(int) SimClock.getTime();
								
		return true;
	}
			
	public void update() 
	{
		super.update();	
		/*update energy consumption*/
		reduceSendingAndScanningEnergy();
		
		/*update recentlocation*/		
		updatelocation();

		if (!canStartTransfer() || isTransferring()) 
			return; // nothing to transfer or is currently transferring 
		
		/* try messages that could be delivered to final recipient */
		if (exchangeDeliverableMessages() != null) 
			return;
		
		tryOtherMessages();
	}
						
	public  Tuple<Message, Connection> tryOtherMessages() 
	{
		Collection<Message> msgCollection = getMessageCollection();
		List<Tuple<Message, Connection>> approach = new ArrayList<Tuple<Message, Connection>>(); 
		List<Tuple<Message, Connection>> spray = new ArrayList<Tuple<Message, Connection>>(); 
		
		List<Message> list = new ArrayList<Message>();
		 for(Connection c : getConnections())
		    {
	    	DTNHost other = c.getOtherNode(getHost());
	        VectorRouter otherRouter = (VectorRouter)other.getRouter();
	        
		    for (Message m : msgCollection)
		        {   
		    	
		    	double thisspeed=this.getHost().getPath() == null ? 0 : this.getHost().getPath().getSpeed();  
	       		  
		    	 double otherspeed=other.getPath() == null ? 0 : other.getPath().getSpeed();
						
				double encounterangle=this.getpairwiseangle(other);
				
				double number=this.getnumberofmessage(other);
			
				double maxspeed=Math.max(thisspeed, otherspeed);
				
				double speeddiff=Math.abs(thisspeed-otherspeed);
				
				double speedportion=speeddiff/maxspeed;
				
				int thisvalue=(int)((weightvalue*this.getanglenumber(encounterangle)+(1-weightvalue)*speedportion)*number);
								    	
		    	 if (otherRouter.hasMessage(m.getId())) 
						continue;
		    	 
		    	 else if (otherRouter.isTransferring()) 
						continue; // skip hosts that are transferring

		    	 else if (thisvalue>0)
		    	 spray.add(new Tuple<Message, Connection>(m,c));
	    	    		 
	    	    	 }
	    	    	 
	    	     } 	 	 
		 Collections.sort(spray, new lowTupleComparator());	

	     return tryMessagesForConnected(spray);  		    	             
	}

	
	public double getdiffuseangle(double i)
	{
		if (Math.abs(i)>Math.PI)
			return 2*Math.PI-Math.abs(i);
		
		else
			return Math.abs(i);		
	}
	
	public double getschedulingpriority(List<Tuple<Message, Connection>> inputtuple)
	{
	   double sum=0;
	   
	   for (Tuple<Message, Connection> t : inputtuple) 
	       {
		    Message message = t.getKey();		
			double r=message.getinittl()-message.getTtl();
			double s=message.getSize();
			
			DTNHost hostthis = t.getValue().getOtherNode(getHost());
	
			double b= hostthis.getRouter().getFreeBufferSize();
			double gra=(s*b)/(r*r);
			sum=sum+gra;
		   }
	   
	   return sum;
	}
	
	public double getmovingdirection()
	{   double v=Math.sqrt(variationx*variationx+variationy*variationy);
	    double direction=0;
		if(this.variationy>=0) 
			direction=Math.acos(variationx/v);
		else
			direction=Math.PI+Math.acos(variationx/v);
		
		return direction;
	}
	
	
private double getpairwiseangle(DTNHost host)
{
	   double encounterangle=0;
	   VectorRouter otherRouter = (VectorRouter)host.getRouter();
	   
	   
	   if((getchangex()==0)&&(getchangey()==0)||(otherRouter.getchangex()==0)&&(otherRouter.getchangey()==0))
		   System.out.println("some node is stopped");
			   
	   if(((this.getchangex()* otherRouter.getchangex())<=0)
			   &&((this.getchangey()* otherRouter.getchangey())>=0)) 
	   {
		   encounterangle=this.getsin(this.getchangex(),this.getchangey())+ 
		   otherRouter.getsin(otherRouter.getchangex(),otherRouter.getchangey());
	   
		   if(encounterangle>Math.PI)
			  System.out.println("angle is larger than 180");
	   }
	      
	   else if(((this.getchangex()* otherRouter.getchangex())>=0)
			   &&((this.getchangey()* otherRouter.getchangey())>=0))
	   {
		   encounterangle=this.getcos(this.getchangex(),this.getchangey())- 
		   otherRouter.getcos(otherRouter.getchangex(),otherRouter.getchangey());
		   
		   double tem=Math.abs(encounterangle);
		   encounterangle=tem;
		   
		   if(encounterangle>Math.PI)	   
			   System.out.println("angle is larger than 180");   
	   }

	   else if(((this.getchangex()* otherRouter.getchangex())<=0)
			   &&((this.getchangey()* otherRouter.getchangey())<=0))  
	   { 
		   encounterangle=this.getsin(this.getchangex(),this.getchangey())
		   +(0.5*Math.PI)+  
		   otherRouter.getcos(otherRouter.getchangex(),otherRouter.getchangey());

		   if (encounterangle>Math.PI)  
		   {encounterangle=(2*Math.PI)-encounterangle;
		   }      
	   }

	   else if(((this.getchangex()* otherRouter.getchangex())>=0) 
			   &&((this.getchangey()* otherRouter.getchangey())<=0))
	   {
		   encounterangle=this.getcos(this.getchangex(),this.getchangey())+  
		   otherRouter.getcos(otherRouter.getchangex(),otherRouter.getchangey());
		   
		   if(encounterangle>Math.PI)
		   {					   
			   System.out.println("angle is larger than 180");
		   } 
	   } 
	      
	   return encounterangle;
}
        
	@Override
	public VectorRouter replicate() 
	       {
		   return new VectorRouter(this);
	       }
	
	public double getdistancetodes(double x, double y)
    {
    	double distx=x-this.getHost().getLocation().getX();;
	    double disty=y-this.getHost().getLocation().getY();
	    double dist=this.getsqrt(distx, disty);	    
	    return dist;
    	
    }
	
	/**
	 * Called by the combus is the energy value is changed
	 * @param key The energy ID
	 * @param newValue The new energy value
	 */
	public void moduleValueChanged(String key, Object newValue) 
	{
		this.currentEnergy = (Double)newValue;
	}

	public void updatelocation()   
	{	
		double timeDiff = (SimClock.getTime() - this.lastlocation) ;
		if(timeDiff>1)
		  {   		
			double locationx=this.getHost().getLocation().getX();   
			double locationy=this.getHost().getLocation().getY();    
			updatechangex();    
			updatechangey();	
			this.getHost().getLocation().setOldLocation(locationx, locationy);   
			this.lastlocation = SimClock.getTime();  
			}
		else
			return;
	}

	   
	public double getcos(double xchange, double ychange)
	{    
		double x=Math.abs(xchange);   
		double y=Math.abs(ychange); 
		double z = Math.sqrt(x*x+y*y);   
		return Math.acos(x/z);   
	}


	public double getsin(double xchange, double ychange)
	{  
		double x=Math.abs(xchange); 
		double y=Math.abs(ychange);  
		double z = Math.sqrt(x*x+y*y);  
		return Math.asin(x/z);
	}
		 
	public double getchangex()	
	{return variationx;	
	}
		
	public double getchangey()	
	{return variationy;	
	}
		
	private void updatechangex()
	{	
		variationx= this.getHost().getLocation().getX()-this.getHost().getLocation().getOldX();
	}
	
	private void updatechangey()
	{	
		variationy= this.getHost().getLocation().getY()-this.getHost().getLocation().getOldY();
	}
	
        /*---------------------------------------------------------------*/
		/*-----------for this algorithm used----------------------*/
	private double getangle(double desx, double desy)
	{
			   
		double moveangle=0;   
		double deschangex=desx-this.getHost().getLocation().getX();	   
		double deschangey=desy-this.getHost().getLocation().getY();   
			   
		if((getchangex()==0)&&(getchangey()==0))  
			System.out.println("node is stopped");

		if(((this.getchangex()* deschangex)<=0)&&((this.getchangey()* deschangey)>=0))   
		{
			moveangle=this.getsin(this.getchangex(),this.getchangey())+  
			this.getsin(deschangex,deschangey);
				    
			if(moveangle>Math.PI)
				System.out.println("angle is larger than 180"); 
		}
			        
		else if(((this.getchangex()* deschangex)>=0)&&((this.getchangey()* deschangey)>=0))   
		{
			moveangle=this.getcos(this.getchangex(),this.getchangey())-   
			this.getcos(deschangex,deschangey);  
		
			moveangle=Math.abs(moveangle);
					      
			if(moveangle>Math.PI)			 
			System.out.println("angle is larger than 180"); 			         
		}
			         
		else if(((this.getchangex()* deschangex)<=0) &&((this.getchangey()* deschangey)<=0))     
		{
		            	    
			moveangle=this.getsin(this.getchangex(),this.getchangey())	    
			+(0.5*Math.PI)+ 	        
			this.getcos(deschangex,deschangey);
			    	            	     
			if (moveangle>Math.PI)      
			{     
				moveangle=(2*Math.PI)- moveangle;      
			}  
			
			if(moveangle>Math.PI)    	      
				System.out.println("angle is larger than 180");        	      
		}
					    
		else if(((this.getchangex()* deschangex)>=0)&&((this.getchangey()* deschangey)<=0))     
		{
			moveangle=this.getcos(this.getchangex(),this.getchangey())+       
			this.getcos(deschangex,deschangey);

			if(moveangle>Math.PI)   
			System.out.println("angle is larger than 180");    
		} 
		
		return moveangle;  	   
	}
		
		/*--------------------------------------------------------------*/

	    /*-----------------------calculation the link duration------------------------*/
	    public double getsqrt(double xvalue, double yvalue ) 
	    {
	    	double x =xvalue;
	    	double y =yvalue;
	    	double z = Math.sqrt(x*x+y*y);
	    	return z;		
	    }

	    public double Aparameter(DTNHost host)
	    {   VectorRouter otherRouter = (VectorRouter)host.getRouter();
	    	double localspeed = getHost().getPath() == null ? 0 : getHost().getPath().getSpeed();
	    	double otherspeed=otherRouter.getHost().getPath()==null?0:otherRouter.getHost().getPath().getSpeed();
	    	double localx = this.getchangex();
	    	double localy = this.getchangey();
	    	double otherx = otherRouter.getchangex();
	    	double othery = otherRouter.getchangey();
	    	double localcos = localx/this.getsqrt(localx, localy);
	    	double othercos = otherx/this.getsqrt(otherx, othery);
	    	double value = localspeed*localcos-otherspeed*othercos;
	    	return value;
	    }
	    
	    public double Bparameter(DTNHost host)
	    {   VectorRouter otherRouter = (VectorRouter)host.getRouter();
	    	double localx=this.getHost().getLocation().getX();
	    	double otherx=otherRouter.getHost().getLocation().getX();
	    	double value = localx-otherx;
	    	return value;
	    }
	    
	    public double Cparameter(DTNHost host)
	    {    VectorRouter otherRouter = (VectorRouter)host.getRouter();
	         double localspeed = getHost().getPath() == null ? 0 : getHost().getPath().getSpeed();
    	     double otherspeed=otherRouter.getHost().getPath()==null?0:otherRouter.getHost().getPath().getSpeed();
    	     double localx = this.getchangex();
    	     double localy = this.getchangey();
    	     double otherx = otherRouter.getchangex();
    	     double othery = otherRouter.getchangey();
    	     double localsin = localy/this.getsqrt(localx, localy);
    	     double othersin = othery/this.getsqrt(otherx, othery);
    	     double value = localspeed*localsin-otherspeed*othersin;
    	     return value;
	    }
	    
	    public double Dparameter(DTNHost host)
	    {   VectorRouter otherRouter = (VectorRouter)host.getRouter();
	    	double localy=this.getHost().getLocation().getY();
	    	double othery=otherRouter.getHost().getLocation().getY();
	    	double value = localy-othery;
	    	return value;
	    }
	    
	    
	    public double getconnectionduration (DTNHost host)
	    { double q = Aparameter(host)*Bparameter(host)+Cparameter(host)*Dparameter(host);
	      double w = Aparameter(host)*Aparameter(host)+Cparameter(host)*Cparameter(host);
	      double e = Aparameter(host)*Dparameter(host)-Cparameter(host)*Bparameter(host);
	      /*change the transmission range here  10 - default transmission*/
	      double r= w*getminimumtransmission(host)*getminimumtransmission(host)-e*e;
	      double t = (Math.sqrt(r)-q)/w;
	      /*
	      System.out.println("-------------------");
	      System.out.println(this.getHost());
	      System.out.println(host);
	      System.out.println(t);
	      */
	      return t;	
	    }
	    
	    public double getminimumtransmission(DTNHost host)
	    { VectorRouter otherRouter = (VectorRouter)host.getRouter();
	      double local=getHost().getInterface(1).getTransmitRange();
	      double other=otherRouter.getHost().getInterface(1).getTransmitRange();
	      double minimum=Math.min(local, other);
	      return minimum;
	    }
	    
	    /*----------------------------------------------------------------------*/

		public  void updatemsgdelegation(DTNHost host)
		{
			Collection<Message> c1 = this.getMessageCollection();
			
			VectorRouter otherRouter = (VectorRouter)host.getRouter();
			Collection<Message> c2 = otherRouter.getMessageCollection();
			
			for (Message m1:c1)
			{
				for (Message m2:c2)
				{
					
					double thisdelegation=getMessage(m1.getId()).delegation;
					double otherdelegation=otherRouter.getMessage(m2.getId()).delegation;
					
					double thisadddelegation=getMessage(m1.getId()).adddelegation;
					double otheradddelegation=otherRouter.getMessage(m2.getId()).adddelegation;
					
					double mindelegation=Math.min(thisdelegation, otherdelegation);
					double maxadddelegation=Math.max(thisadddelegation, otheradddelegation);
					
					if(m1.getId().equals(m2.getId()))
					{
						
							
							otherRouter.getMessage(m2.getId()).delegation=mindelegation;
							otherRouter.getMessage(m2.getId()).adddelegation=(int) maxadddelegation;
					
							getMessage(m1.getId()).delegation= mindelegation;
							getMessage(m1.getId()).adddelegation=(int) maxadddelegation;
							
					}		
				}
			}
		}
			
		public  void updatemsggossip(DTNHost host)
		{
			Collection<Message> c1 = this.getMessageCollection();
			
			VectorRouter otherRouter = (VectorRouter)host.getRouter();
			Collection<Message> c2 = otherRouter.getMessageCollection();
			
			for (Message m1:c1)
			    {
				for (Message m2:c2)
				    {
				    if(m1.getId().equals(m2.getId()))
					  {  
				      /*only the local router updates its information since this function is called two times*/
					  this.getMessage(m1.getId()).receivingrequest++;
					  }		
				    }
			    }
		}
		
	
		
		
		
		/*update the message carrier */
		public  void updatemsgcount(DTNHost host)
		{
			Collection<Message> c1 = this.getMessageCollection();
			
			VectorRouter otherRouter = (VectorRouter)host.getRouter();
			Collection<Message> c2 = otherRouter.getMessageCollection();
			
			for (Message m1:c1)
			    {
				for (Message m2:c2)
				    {
					if(m1.getId().equals(m2.getId()))
					  {
					   for (DTNHost h: m2.MetNode)
						   {this.getMessage(m1.getId()).updateMetNode(h);
						  
						   }
					   for (DTNHost h: m1.MetNode)
						   {otherRouter.getMessage(m2.getId()).updateMetNode(h);}
					  }		
				    }
			    }
		}
		
		public void changedConnection(Connection con) 
		{
			
			
			if (con.isUp()) 
			   {
				
				DTNHost otherHost = con.getOtherNode(getHost());
				VectorRouter otherRouter = (VectorRouter)otherHost.getRouter();
				double thisspeed=this.getHost().getPath() == null ? 0 : this.getHost().getPath().getSpeed();  
	       		  
		    	 double otherspeed=otherHost.getPath() == null ? 0 : otherHost.getPath().getSpeed();
						
				double encounterangle=this.getpairwiseangle(otherHost);
				
				double number=this.getnumberofmessage(otherHost);
			
				double maxspeed=Math.max(thisspeed, otherspeed);
				
				double speeddiff=Math.abs(thisspeed-otherspeed);
				
				double speedportion=speeddiff/maxspeed;
				
				int thisvalue=(int)((weightvalue*this.getanglenumber(encounterangle)+(1-weightvalue)*speedportion)*number);
				
				
				this.updatenumber(otherHost, thisvalue);
				otherRouter.updatenumber(this.getHost(), thisvalue);
			   }
			
			else if (!con.isUp())
			        {DTNHost otherHost = con.getOtherNode(getHost());
					VectorRouter otherRouter = (VectorRouter)otherHost.getRouter();
				this.clearnumber(otherHost);
				otherRouter.clearnumber(this.getHost());
			        }
		}
		
		private void updatenumber(DTNHost host, int value) {
			vectortable.put(host, value);
		}
		
		private void clearnumber(DTNHost host) {
			vectortable.remove(host);
		}
		
		public double getanglenumber(double angle)
		{double output=0;
		 if(angle<=0.5*Math.PI)
			 output=(2/Math.PI)*angle;
		 else
			 output=-(2/Math.PI)*(Math.PI-angle);
		 
		 return output;
			
		}

		public  double getnumberofmessage(DTNHost host)
		{
			Collection<Message> c1 = this.getMessageCollection();
			
			VectorRouter otherRouter = (VectorRouter)host.getRouter();
			Collection<Message> c2 = otherRouter.getMessageCollection();
			
			double number=0;
			
			for (Message m1:c1)
			{
				for (Message m2:c2)
				{
					
					if(!m1.getId().equals(m2.getId()))
					{
						number=number+1;	
					}		
				}
			}
			return number;
		}

		/*
		public double[] xgetlocation(DTNHost host, DTNHost neighbor,  double x, double y, double ra)
		{
			double number=0;
			double sumx=0;
			double sumy=0;
			double max=0;
			
			GEORouter otherRouter=(GEORouter)neighbor.getRouter(); 
			
			double [] shuzu;
			shuzu=new double [2];
			
			Iterator<DTNHost> it = neighborlocation.keySet().iterator();   
			  while (it.hasNext())
			  {   
			      DTNHost key=null;   
			      key=(DTNHost)it.next();   
			      if (key != host)
			      {
			    	  Map<DTNHost, locationwithtime> map = neighborlocation.get(key);
			          if (map.containsKey(host))
			             { 
			        	  double hostx = map.get(host).getxlocation(); 
			        	  double hosty = map.get(host).getylocation();
			        	  double time = map.get(host).gettime();  
			        	  double dist=Math.sqrt((x-hostx)*(x-hostx)+(y-hosty)*(y-hosty));
			        	  
			        	  if(dist<=ra)
	            		   {   
	            			   sumx=sumx+time*hostx;
	            			   sumy=sumy+time*hosty;
	            			   number=number+1;
	            			   if(time>max)
	            				   max=time;
	            		   }
			               
			        	  else if(otherRouter.neighborlocation.containsKey(key))
			               {
			            	   Map<DTNHost, locationwithtime> othermap =otherRouter.neighborlocation.get(key);
			            	   if(otherRouter.neighborlocation.get(key).containsKey(host)) 
			            	   { 
			            		   double otherhostx = othermap.get(host).getxlocation();
			            		   double otherhosty = othermap.get(host).getylocation();    
			            		   double othertime = othermap.get(host).gettime();  
			            		   double otherdist=Math.sqrt((x-otherhostx)*(x-otherhostx)+(y-otherhosty)*(y-otherhosty));
	
			            		   if (otherdist<=ra)
			            		   {
			            			   if(time!=othertime)
			            			   {
			            			   sumx=sumx+othertime*otherhostx;
			            			   sumy=sumy+othertime*otherhosty;
			            			   number=number+1;
			            			   if(time>max)
			            				   max=time;
			            			   }
			            		   }
			            	   } 
			               }
			             }
			          else
			          {
			        	  if(otherRouter.neighborlocation.containsKey(key))
			               {
			            	   Map<DTNHost, locationwithtime> othermap =otherRouter.neighborlocation.get(key);
			            	   if(otherRouter.neighborlocation.get(key).containsKey(host)) 
			            	   { 
			            		   double otherhostx = othermap.get(host).getxlocation();
			            		   double otherhosty = othermap.get(host).getylocation();    
			            		   double othertime = othermap.get(host).gettime();  
			            		   double otherdist=Math.sqrt((x-otherhostx)*(x-otherhostx)+(y-otherhosty)*(y-otherhosty));
	
			            		   if (otherdist<=ra)
			            		   {
			            			   if(time!=othertime)
			            			   {
			            			   sumx=sumx+othertime*otherhostx;
			            			   sumy=sumy+othertime*otherhosty;
			            			   number=number+1;
			            			   if(time>max)
			            				   max=time;
			            			   }
			            		   }
			            	   }
			               }
			          }
			  }
			  
			  if (location.containsKey(host))
			     {
				  double localtime= location.get(host).gettime(); 
			      double localsumx= location.get(host).getxlocation();
			      double localsumy= location.get(host).getylocation();
			      
			      double dist=Math.sqrt((x-localsumx)*(x-localsumx)+(y-localsumy)*(y-localsumy));
	               
	               if(dist<=ra)
	               {
	            	   sumx=sumx+localtime*localsumx;
	            	   sumy=sumy+localtime*localsumy;
	            	   
	            	   number=number+1;
	            	   if(localtime>max)
	            		   max=localtime;
	               } 
			     }
			  
			  double averagex=sumx/(max*number);
			  double averagey=sumy/(max*number);

			  shuzu[0]=averagex;
			  shuzu[1]=averagey;
			  return shuzu;
		}
		
		
		
		
		public double getlocationrecenttime(DTNHost host)
		{
			double max=0;
			
			Iterator<DTNHost> it = neighborlocation.keySet().iterator();   
			  while (it.hasNext())
			  {   
			      DTNHost key=null;   
			      key=(DTNHost)it.next();   
			      if (key != host)
			      {
			    	  Map<DTNHost, locationwithtime> map = neighborlocation.get(key);
			          if (map.containsKey(host))
			             { 
			               double time = map.get(host).gettime();  
			               if(time>max)
				              max=time;
			             }
				  }
			  }
			  
			  if (location.containsKey(host))
			     {double localtime=location.get(host).gettime();   
			      if(localtime>max)
		               max=localtime;
			      } 
			  
			  return max;
		}
		
		*/
		
	
	
	    
	    

	  
	         
	    
	   
	    
	    
	    
	    /*----------------------------------------------*/
	    /*initialize the container*/
		private void initPreds() 
		{
		    this.vectortable=new HashMap<DTNHost, Integer>();
		}
			
		/*-------------------------------------------*/
	
		protected void transferDone(Connection con) 
		{
			String msgId = con.getMessage().getId();
			/* get this router's copy of the message */
			Message msg = getMessage(msgId);
			DTNHost other = con.getOtherNode(getHost());
			VectorRouter otherRouter= (VectorRouter)other.getRouter();

			if (msg == null)  // message has been dropped from the buffer after..
				return; // ..start of transfer -> no need to reduce amount of copies
		   
			
		}
		
		protected void transferAborted(Connection con) 
		{ 
			String msgId = con.getMessage().getId();
			/* get this router's copy of the message */
			Message msg = getMessage(msgId);

			if (msg == null)  // message has been dropped from the buffer after..
				return; // ..start of transfer -> no need to reduce amount of copies
		  
			
			this.getMessage(msg.getId()).othercopy=-1;
			this.getMessage(msg.getId()).localcopy=-1;
			this.getMessage(msg.getId()).failcount=-1;
			
		}
		
		public Message messageTransferred(String id, DTNHost from) 
		{
			Message msg = super.messageTransferred(id, from);
			
		
			
		
			
			return msg;
		}
		  	
		private class lowTupleComparator implements Comparator <Tuple<Message, Connection>> 
		{

			public int compare(Tuple<Message, Connection> tuple1,Tuple<Message, Connection> tuple2) 
		    {
				 
				 Message msg1=tuple1.getKey();
				    Message msg2=tuple2.getKey();
				    
				    Connection con1=tuple1.getValue();
				    Connection con2=tuple2.getValue();
				    
				    /*
				    if((getdiffusionspeed(msg1,con1)*msg1.getTtl())> (getdiffusionspeed(msg2,con2))*msg2.getTtl())
				    	return -1;
				    else
				    	return 1;
				    	*/
				    if(msg1.getReceiveTime()<msg2.getReceiveTime())
				    	return -1;
				    	else 
				    		return 1;
				   
			}
		}
		
		
		

		private class highDropComparator implements Comparator<Message> 
		{
			public   highDropComparator()
			{}
			public int compare(Message msg1, Message msg2) 
			{
			    if(msg1.getTtl()>msg2.getTtl())
			    	return -1;
			    else
			    	return 1;
			} 
		}
		
		

		
		public  void updatemsgdirection(DTNHost host)
		{
			Collection<Message> c1 = this.getMessageCollection();
			
			VectorRouter otherRouter = (VectorRouter)host.getRouter();
			Collection<Message> c2 = otherRouter.getMessageCollection();
			
			for (Message m1:c1)
			{
				for (Message m2:c2)
				{
					
					double thisdirection=getMessage(m1.getId()).drop;
					double otherdirection=otherRouter.getMessage(m2.getId()).drop;
					
					double thistime=getMessage(m1.getId()).delegation;
					double othertime=otherRouter.getMessage(m2.getId()).delegation;
					
				
					if(m1.getId().equals(m2.getId()))
					{
						if(thistime>othertime)
							
							{otherRouter.getMessage(m2.getId()).delegation=thistime;
							otherRouter.getMessage(m2.getId()).drop=thisdirection;
							}

					}		
				}
			}
		}
			
			
	public String toString() 
	{
		return super.toString() + " energy level = " + this.currentEnergy;
	}	
}