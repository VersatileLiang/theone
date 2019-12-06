package movement;

import core.Coord;
import core.Settings;
import movement.map.DijkstraPathFinder;
import movement.map.MapNode;
import movement.map.PointsOfInterest;
import movement.map.SimMap;

import java.util.ArrayList;
import java.util.List;

public class MyMovement extends MapBasedMovement implements SwitchableMovement{

    /** the Dijkstra shortest path finder */
    private DijkstraPathFinder pathFinder;

    /** Points Of Interest handler */
    private PointsOfInterest pois;

    public MyMovement(Settings settings) {
        super(settings);
        this.pathFinder = new DijkstraPathFinder(getOkMapNodeTypes());
        this.pois = new PointsOfInterest(getMap(), getOkMapNodeTypes(),
                settings, rng);
    }

    public MyMovement(Settings settings, SimMap newMap, int nrofMaps) {
        super(settings, newMap, nrofMaps);
    }

    protected MyMovement(MyMovement mbm) {
        super(mbm);
        this.pathFinder = mbm.pathFinder;
        this.pois = mbm.pois;
    }

    @Override
    public Path getPath() {
        Path p = new Path(generateSpeed());
        MapNode to = pois.selectDestination();

        List<MapNode> nodePath = pathFinder.getShortestPath(lastMapNode, to);

        // this assertion should never fire if the map is checked in read phase
        assert nodePath.size() > 0 : "No path from " + lastMapNode + " to " +
                to + ". The simulation map isn't fully connected";

        for (MapNode node : nodePath) { // create a Path from the shortest path
            p.addWaypoint(node.getLocation());
        }

        lastMapNode = to;

        return p;
    }
    public List<Coord> getTORpath(Coord fromlocation, Coord deslocation)
    {
        List<Coord> trajactory = new ArrayList<Coord>();

        MapNode fromnode=map.getNodeByCoord(fromlocation);
        MapNode tonode=map.getNodeByCoord(deslocation);

        List<MapNode> TRAnode = new ArrayList<MapNode>();
        TRAnode=pathFinder.getShortestPath(fromnode, tonode);

        for (MapNode node : TRAnode)
        { // create a Path from the shortest path
            trajactory.add(node.getLocation());
        }

        return trajactory;
    }
    @Override
    public MyMovement replicate() {
        return new MyMovement(this);
    }
}
