package multiagent.shipment;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Array;
import java.net.*;
import java.util.*;

import org.jgrapht.*;
import org.jgrapht.io.*;
import org.jgrapht.alg.shortestpath.JohnsonShortestPaths;
import org.jgrapht.graph.*;
import scala.Int;
import scala.Tuple2;

public final class PathHandler
{
    private JohnsonShortestPaths shortestPaths;
    private SimpleGraph<Integer,Integer> graph = new SimpleGraph(DefaultEdge.class);
    private Random rnd = new Random();
    public PathHandler(String path)
    {
        List<Integer> verts = new LinkedList<>();
        HashSet<Tuple2<Integer, Integer>> edges = new HashSet();
        try {
            BufferedReader abc = new BufferedReader(new FileReader(path));
            String line = abc.readLine();
            while(line != null){
                String[] splited = line.split("\\s+");
                int v1 = Integer.parseInt(splited[0]);
                int v2 = Integer.parseInt(splited[1]);
                verts.add(v1);
                verts.add(v2);
                edges.add(new Tuple2<>(v1, v2));
                line = abc.readLine();
            }
            abc.close();
        } catch (Exception name) {
        }
        verts.sort(Comparator.naturalOrder());
        for (Integer vert: verts) {
            graph.addVertex(vert);
        }
        for (Tuple2<Integer, Integer> edge: edges) {
            graph.addEdge(edge._1, edge._2);
        }
        shortestPaths = new JohnsonShortestPaths(graph);
    }

    public void printGraph(String path){
        DOTExporter exporter = new DOTExporter();
        try {
            exporter.exportGraph(graph, new FileWriter(path));
        }catch (Exception e){}
    }

    public int getPathLength(int v1, int v2){
        GraphPath path = shortestPaths.getPath(v1, v2);
        Integer length = path.getLength();

        return path.getLength();
    }

    public List<Integer> getPath(int v1, int v2){
        GraphPath path = shortestPaths.getPath(v1, v2);
        List<Integer> verts = path.getVertexList();
        return verts;
    }

    public Integer getRandomPosition() {
        Set<Integer> set = graph.vertexSet();
        Integer[] arr = set.toArray(new Integer[0]);
        return arr[rnd.nextInt(set.size())];
    }
}