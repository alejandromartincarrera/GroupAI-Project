
package jadelab2;

import java.awt.Graphics;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

import java.util.Arrays;
import java.util.stream.Collectors;

import jade.core.AID;
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
    public AID[] sellerAgents;


/*    public SalesmanAgent(int index, int[] agentsPos, Graph<Integer, DefaultWeightedEdge> graph){
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
 */

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
            System.out.println(getAID().getLocalName()+": message received: "+content);

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
                    i+=1;
                } catch (NumberFormatException e) {
                    System.out.println("Invalid integer: " + part);
                    // Handle the exception as needed
                }
            }

        }

        //search and save all sellerAgents
        DFAgentDescription template = new DFAgentDescription();
        ServiceDescription sd2 = new ServiceDescription();
        sd2.setType("SalesmanAgent");
        template.addServices(sd2);
        try
        {
            DFAgentDescription[] result = DFService.search(this, template);
            this.sellerAgents = new AID[result.length];
            for (int i = 0; i < result.length; ++i)
            {
                this.sellerAgents[i] = result[i].getName();
            }
        }
        catch (FIPAException fe)
        {
            fe.printStackTrace();
        }

        //launches the Stretegy
        addBehaviour(new Strategy());

    }

    private class Strategy extends CyclicBehaviour {
        public void action() {
            SalesmanAgent myAgent = (SalesmanAgent) this.myAgent;
            //calculate new distances to neighbors
            calculateDistances();
            //search for the nearest not-visited vertex
            int min = searchMin();
            if (min<0){
                //if there are any not-visited neighbors
                min = Integer.MAX_VALUE;
            }
            int myMin = distances[min];

            //send minimum distance to all agents
            for (int i=0; i<sellerAgents.length; i++){
                if (i!=myAgent.index) {
                    ACLMessage proposition = new ACLMessage(ACLMessage.PROPOSE);
                    proposition.addReceiver(sellerAgents[i]);
                    proposition.setContent(Integer.toString(myMin));
                    proposition.setConversationId(Integer.toString(i));
                    proposition.setReplyWith("cfp" + System.currentTimeMillis()); //unique value
                    this.myAgent.send(proposition);
                    System.out.println(getAID().getLocalName()+": message sent to "+sellerAgents[i].getLocalName());
                }
            }

            //wait for all proposals
            int newMin = Integer.MAX_VALUE;
            int count=0;
            List<AID> minAgents = new ArrayList<AID>();
            while (count<sellerAgents.length-1){
                MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.PROPOSE);
                ACLMessage msg = this.myAgent.blockingReceive(mt);
                String content = msg.getContent();
                int proposedMin = Integer.parseInt(content);
                if (proposedMin<newMin) {
                    newMin=proposedMin;
                    minAgents.clear();
                    minAgents.add(msg.getSender());
                }
                else if(proposedMin==newMin) {
                    minAgents.add(msg.getSender());
                }
                count+=1;
            }
            boolean winner = false;
            if (myMin<newMin) {
                //I'm the one with minimum distance
                winner = true;
            }
            else if (newMin==myMin) {
                //there are more agents with the same min distance
                //we draw random numbers until one gets the least number
                Random random = new Random();
                boolean end = false;
                while (!end) {
                    int randomValue = random.nextInt(1000);
                    for (int i=0; i<minAgents.size(); i++) {
                        ACLMessage proposition = new ACLMessage(ACLMessage.PROPOSE);
                        proposition.addReceiver(minAgents.get(i));
                        proposition.setContent(Integer.toString(randomValue));
                        //proposition.setConversationId(Integer.toString(i));
                        proposition.setReplyWith("cfp" + System.currentTimeMillis()); //unique value
                        this.myAgent.send(proposition);
                        System.out.println(getAID().getLocalName()+": message sent to "+minAgents.get(i).getLocalName());
                    }
                    count=0;
                    List<AID> minAgents2 = new ArrayList<AID>();
                    while (count<minAgents.size()-1){
                        MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.PROPOSE);
                        ACLMessage msg = this.myAgent.blockingReceive(mt);
                        String content = msg.getContent();
                        int proposedRandom = Integer.parseInt(content);
                        if (proposedRandom<randomValue) {
                            newMin=proposedRandom;
                            minAgents2.clear();
                            minAgents2.add(msg.getSender());
                        }
                        else if(proposedRandom==newMin) {
                            minAgents2.add(msg.getSender());
                        }
                        count+=1;
                    }
                    minAgents = minAgents2;
                    if (randomValue<newMin) {
                        //I drew the smallest random number
                        end= true;
                        winner = true;
                    }
                    else if (randomValue == newMin) {
                        //new round needed
                        end=false;
                    }
                    else {
                        //I lost
                        end=true;
                        winner=false;
                    }
                }
            }
            else {
                //there are agents with less distance than me
                winner=false;
            }

            if (winner) {
                //move
                System.out.println(getAID().getLocalName()+": I won the round "+Integer.toString(myMin));

                try {
                    Thread.sleep(3000);
                }
                catch (Exception ex){
                    System.out.println(ex);
                }
            }
            else {
                System.out.println(getAID().getLocalName()+": I lost the round "+Integer.toString(myMin));
                try {
                    Thread.sleep(1000);
                }
                catch (Exception ex){
                    System.out.println(ex);
                }
            }


        }

    }

    public void calculateDistances() {
        for (DefaultWeightedEdge edge : graph.edgesOf(this.pos)){
            if (graph.getEdgeWeight(edge) <distances[graph.getEdgeTarget(edge)]) {
                distances[graph.getEdgeTarget(edge)] = (int) graph.getEdgeWeight(edge);
                //when the agent moves we will have to update the distances to their former neighbors by adding the edge that it has justs crossed
            }
            if (graph.getEdgeWeight(edge) <distances[graph.getEdgeSource(edge)]) {
                distances[graph.getEdgeSource(edge)] = (int) graph.getEdgeWeight(edge);
            }
        }
    }

    public int searchMin(){
        int min = Integer.MAX_VALUE;
        int j = -1;
        for (int i=0; i<this.distances.length; i++) {
            if ((!this.visited.contains(i)) && this.distances[i]<min) {
                j = i;
                min = this.distances[i];
            }
        }
        return j;
    }





    protected void takeDown() {
        try {
            DFService.deregister(this);
        }
        catch (FIPAException fe) {
            fe.printStackTrace();
        }
        //myGui.dispose();
        System.out.println(getAID().getLocalName()+": Seller agent " + getAID().getName() + " terminated.");
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

            }
            System.out.println(getAID().getLocalName()+": Graph loaded from CSV.");
        } catch (IOException e) {
            e.printStackTrace();
        }
        return graph;
    }


    public AID[] getSellerAgents() {
        return this.sellerAgents;
    }
}
