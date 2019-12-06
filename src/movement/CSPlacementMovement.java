/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package movement;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.Vector;

import movement.map.MapNode;
import movement.map.SimMap;
import core.Coord;
import core.Settings;
import core.SettingsError;
import core.SimError;

/**
 * Map based movement model which gives out Paths that use the
 * roads of a SimMap. 
 */
public class CSPlacementMovement extends MapBasedMovement implements SwitchableMovement {

	/** Per node group setting for setting the location ({@value}) */
	public static final String LOCATION_S = "nodeLocation";
	private Coord loc; /** The location of the nodes */
	/**
	 * Creates a new movement model based on a Settings object's settings.
	 * @param s The Settings object where the settings are read from
	 */
	public CSPlacementMovement(Settings s) 
	{
		super(s);

		int coords[];
		coords = s.getCsvInts(LOCATION_S, 2);
		this.loc = new Coord(coords[0],coords[1]);
	}
	
	/**
	 * Copy constructor. 
	 * @param sm The StationaryMovement prototype
	 */
	public CSPlacementMovement(CSPlacementMovement sm) 
	{
		super(sm);
		this.loc = sm.loc;
	}
	
	/**
	 * Returns the only location of this movement model
	 * @return the only location of this movement model
	 */
	@Override
	public Coord getInitialLocation() 
	{
		List<MapNode> nodes = map.getNodes();
		
		MapNode n=nodes.get(rng.nextInt(nodes.size()));
		
		Coord nLocation;
		double max=Integer.MAX_VALUE;
				
		for(int i=0; i<=nodes.size()-1;i++)
		{	
			MapNode mp=nodes.get(i);
	
			if(mp.getLocation().distance(loc)<max)		
			{   
				max=mp.getLocation().distance(loc);    
				n=mp;
			}
		}
		
		nLocation = n.getLocation();	
		this.lastMapNode = n;
		this.initiallocation=n;
		return nLocation;
	}
	
	/**
	 * Returns a single coordinate path (using the only possible coordinate)
	 * @return a single coordinate path
	 */
	@Override
	public Path getPath() 
	{
		Path p = new Path(0);
		return p;
	}
	
	@Override
	public double nextPathAvailable() {
		return Double.MAX_VALUE;	// no new paths available
	}
	
	@Override
	public CSPlacementMovement replicate() 
	{
		return new CSPlacementMovement(this);
	}

}
