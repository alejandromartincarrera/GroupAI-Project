import java.awt.Graphics;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

import java.util.Arrays;
import java.util.stream.Collectors;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;

import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import org.jgrapht.Graph;
import org.jgrapht.ext.JGraphXAdapter;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleWeightedGraph;
import org.jgrapht.alg.connectivity.ConnectivityInspector;

public class SalesmanAgent extends Agent {
    public static final String csvFilePath = "graph.csv";

    int pos;
    int numAgents;
    int index;
    Graph<Integer, DefaultWeightedEdge> graph;
    int[] distances;
    Set<Integer> visited;

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
        //reads graph
        this.graph = this.readGraphFromCSV(this.csvFilePath);
        //initialize structures
        this.distances = new int[graph.vertexSet().size()];
        Arrays.fill(distances, Integer.MAX_VALUE);

        this.visited = new HashSet<Integer>();
        //registers itself as agent publicly
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("SalesmanAgent");
        sd.setName("JADE-node-agent");
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
        }
        catch (FIPAException fe) {
            fe.printStackTrace();
        }
        //waits to know its position and the positions of all agents
        MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.INFORM);
        ACLMessage msg = this.blockingReceive(mt);
        if (msg != null) {
            String content = msg.getContent();
            //parses the ints of content. It will have this format = "number of agents, index of this agent, pos agent 1, pos agent 2, ...., pos agent n"
            String[] parts = content.split(",");
            int i=0;
            for (String part : parts) {
                try {
                    int n = Integer.parseInt(part.trim());
                    if (i==0) {
                        this.numAgents=n;
                    }
                    else if(i==1) {
                        this.index=n;
                    }
                    else {
                        //saves all initial positions of agents as visited
                        this.visited.add(n);
                        if (i-2==this.index) {
                            this.pos=n;
                            this.distances[this.pos]=0;
                        }
                    }
                } catch (NumberFormatException e) {
                    System.out.println("Invalid integer: " + part);
                    // Handle the exception as needed
                }
            }

        }

        //launches the Stretegy
        addBehaviour(new Strategy());

    }

    private class Strategy extends CyclicBehaviour {
        public void action() {
            //calculate new distances to neighbors

            //search for the nearest not-visited vertex
        }

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



    public Graph<Integer, DefaultWeightedEdge> readGraphFromCSV(String filePath) {
        Graph<Integer, DefaultWeightedEdge> graph = new SimpleWeightedGraph<>(DefaultWeightedEdge.class);
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] values = line.split(",");
                int source = Integer.parseInt(values[0]);
                int target = Integer.parseInt(values[1]);
                double weight = Double.parseDouble(values[2]);

                graph.addVertex(source);
                graph.addVertex(target);
                DefaultWeightedEdge edge = graph.addEdge(source, target);
                graph.setEdgeWeight(edge, weight);

                DefaultWeightedEdge edge2 = graph.addEdge(target, source);
                graph.setEdgeWeight(edge2, weight);
            }
            System.out.println("Graph loaded from CSV.");
        } catch (IOException e) {
            e.printStackTrace();
        }
        return graph;
    }
}
