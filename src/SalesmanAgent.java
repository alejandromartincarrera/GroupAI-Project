import java.awt.Graphics;

import java.util.Arrays;

import jade.core.Agent;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;

import org.jgrapht.Graph;
import org.jgrapht.ext.JGraphXAdapter;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleWeightedGraph;
import org.jgrapht.alg.connectivity.ConnectivityInspector;

public class SalesmanAgent extends Agent {
    int pos;
    int index;
    Graph<Integer, DefaultWeightedEdge> graph;
    int[] distances;
    boolean[] visited;

    public SalesmanAgent(int index, int[] agentsPos, Graph<Integer, DefaultWeightedEdge> graph){
        this.index=index;
        this.pos=agentsPos[index];
        this.graph = graph;
        distances = new int[graph.vertexSet().size()];
        visited = new boolean[graph.vertexSet().size()];

        Arrays.fill(distances, Integer.MAX_VALUE);
        for (DefaultWeightedEdge edge : graph.edgesOf(this.pos)){
            if (graph.getEdgeWeight(edge) <distances[graph.getEdgeTarget(edge)]) {
                distances[graph.getEdgeTarget(edge)] = graph.getEdgeWeight(edge);
                //when the agent moves we will have to update the distances to their former neighbors by adding the edge that it has justs crossed
            }
        }
        distances[pos]=0;

        Arrays.fill(visited, false);
        for (int i=0 ; i<agentsPos.length; i++) {
            visited[agentsPos[i]]=true;
        }



    }

    protected void setup() {

    }

    protected void takeDown() {
        try {
            DFService.deregister(this);
        }
        catch (FIPAException fe) {
            fe.printStackTrace();
        }
        //myGui.dispose();
        System.out.println("Seller agent " + getAID().getName() + " terminated.");
    }
}
