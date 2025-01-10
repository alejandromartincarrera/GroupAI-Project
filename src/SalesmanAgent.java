
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

    public int pos;
    public int numAgents;
    public int index;
    public Graph<Integer, DefaultWeightedEdge> graph;
    public double[] distances;
    public Set<Integer> visited;
    public AID[] sellerAgents;

    public Map<Integer, List<Integer>> minRoutes;
    public Set<Integer> frontierNodes;


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
        this.distances = new double[graph.vertexSet().size()];
        Arrays.fill(distances, Double.MAX_VALUE);

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
                            this.visited.add(this.pos);
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
            if (myAgent.visited.size()==myAgent.graph.vertexSet().size()){
                System.out.println(getAID().getLocalName()+": all nodes have been visited");
                doDelete();
            }
            //calculate new distances to neighbors
            calculateDistances();
            //search for the nearest not-visited vertex
            int min = searchMin();
            double myMin;
            if (min<0){
                //if there are any not-visited neighbors
                myMin = Integer.MAX_VALUE;
            }
            else {
                myMin = distances[min];
            }

            //send minimum distance to all agents
            for (int i=0; i<sellerAgents.length; i++){
                if (i!=myAgent.index) {
                    ACLMessage proposition = new ACLMessage(ACLMessage.PROPOSE);
                    proposition.addReceiver(sellerAgents[i]);
                    proposition.setContent(Double.toString(myMin));
                    proposition.setConversationId(Integer.toString(i));
                    proposition.setReplyWith("cfp" + System.currentTimeMillis()); //unique value
                    this.myAgent.send(proposition);
                    System.out.println(getAID().getLocalName()+": message sent to "+sellerAgents[i].getLocalName());
                }
            }

            //wait for all proposals
            double newMin = Double.MAX_VALUE;
            int count=0;
            List<AID> minAgents = new ArrayList<AID>();
            while (count<sellerAgents.length-1){
                MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.PROPOSE);
                ACLMessage msg = this.myAgent.blockingReceive(mt);
                String content = msg.getContent();
                double proposedMin = Double.parseDouble(content);
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
                    int newMin2=Integer.MAX_VALUE;
                    while (count<minAgents.size()-1){
                        MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.PROPOSE);
                        ACLMessage msg = this.myAgent.blockingReceive(mt);
                        String content = msg.getContent();
                        int proposedRandom = Integer.parseInt(content);
                        if (proposedRandom<newMin2) {
                            newMin2=proposedRandom;
                            minAgents2.clear();
                            minAgents2.add(msg.getSender());
                        }
                        else if(proposedRandom==newMin2) {
                            minAgents2.add(msg.getSender());
                        }
                        count+=1;
                    }
                    minAgents = minAgents2;
                    if (randomValue<newMin2) {
                        //I drew the smallest random number
                        end= true;
                        winner = true;
                    }
                    else if (randomValue == newMin2) {
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
                List<Integer> route = minRoutes.get(min);
                int nextDestination;
                for (int j = route.size()-1; j >=0; j--){
                    nextDestination = route.get(j);
                    if (nextDestination != myAgent.pos) {
                        //search the weight of the edge that we are going to cross
                        double weight=0;
                        DefaultWeightedEdge edge = graph.getEdge(myAgent.pos, nextDestination);
                        if (edge != null) {
                            weight = graph.getEdgeWeight(edge);
                        }
                        else{
                            System.out.println(getAID().getLocalName()+": error Edge does not exist");
                        }
                        //change routes and min distances of frontier nodes
                        for (int z: frontierNodes) {
                            List<Integer> l = minRoutes.get(z);
                            if (l.size()==1) {
                                //the only element in the route is my current position
                                l.add(nextDestination);
                                distances[z] += weight;
                            }
                            else {
                                if (l.get(l.size()-2)==nextDestination) {
                                    //we are following the route
                                    l.remove(l.size()-1);
                                    distances[z]-=weight;
                                }
                                else{
                                    //we are not following the route
                                    l.add(nextDestination);
                                    distances[z]+=weight;
                                }
                            }
                        }
                    }
                }
                //and our final destination, that does not belong to the route list
                nextDestination = min;
                if (nextDestination != myAgent.pos) {
                    //search the weight of the edge that we are going to cross
                    double weight=0;
                    DefaultWeightedEdge edge = graph.getEdge(myAgent.pos, nextDestination);
                    if (edge != null) {
                        weight = graph.getEdgeWeight(edge);
                    }
                    else{
                        System.out.println(getAID().getLocalName()+": error Edge does not exist");
                    }
                    //change routes and min distances of frontier nodes
                    for (int z: frontierNodes) {
                        List<Integer> l = minRoutes.get(z);
                        if (l.size()==1) {
                            //the only element in the route is my current position
                            l.add(nextDestination);
                            distances[z] += weight;
                        }
                        else {
                            if (l.get(l.size()-2)==nextDestination) {
                                //we are following the route
                                l.remove(l.size()-1);
                                distances[z]-=weight;
                            }
                            else{
                                //we are not following the route
                                l.add(nextDestination);
                                distances[z]+=weight;
                            }
                        }
                    }
                }

                System.out.println(getAID().getLocalName()+": I won the round "+Double.toString(myMin));
                //updateDistances(min);

                //change the status of variables
                myAgent.distances[min]=0;
                myAgent.pos=min;
                myAgent.visited.add(min);
                myAgent.frontierNodes.remove(min);
                myAgent.minRoutes.remove(min);
                //comunicate new position to the other agents
                for (int i=0; i<sellerAgents.length; i++){
                    if (i!=myAgent.index) {
                        ACLMessage proposition = new ACLMessage(ACLMessage.INFORM);
                        proposition.addReceiver(sellerAgents[i]);
                        proposition.setContent(Integer.toString(myAgent.pos));
                        proposition.setConversationId(Integer.toString(i));
                        proposition.setReplyWith("cfp" + System.currentTimeMillis()); //unique value
                        this.myAgent.send(proposition);
                        System.out.println(getAID().getLocalName()+": message sent to "+sellerAgents[i].getLocalName());
                    }
                }
            }
            else {
                //wait for the result of moving
                System.out.println(getAID().getLocalName()+": I lost the round "+Double.toString(myMin));

                //wait to receive the newPosition
                MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.INFORM);
                ACLMessage msg = this.myAgent.blockingReceive(mt);
                String content = msg.getContent();
                int newPosition = Integer.parseInt(content);
                //save changes
                myAgent.visited.add(newPosition);
                myAgent.frontierNodes.remove(newPosition);
                myAgent.minRoutes.remove(min);

            }


        }

    }

    //not used for now
    public void updateDistances(int min) {
        for (DefaultWeightedEdge edge : graph.edgesOf(this.pos)){
            if (graph.getEdgeTarget(edge)!=this.pos) {
                if (distances[graph.getEdgeTarget(edge)]==graph.getEdgeWeight(edge)){
                    distances[graph.getEdgeTarget(edge)]+=distances[min];
                }
            }
        }
    }

    public void calculateDistances() {
        for (DefaultWeightedEdge edge : graph.edgesOf(this.pos)){
            int end;
            if (graph.getEdgeTarget(edge) != this.pos) {
                end = graph.getEdgeTarget(edge);
            }
            else {
                end = graph.getEdgeSource(edge);
            }

            if (distances[end]==Double.MAX_VALUE){
                frontierNodes.add(end);
                List <Integer> l = new ArrayList<Integer>();
                l.add(this.pos);
                minRoutes.put(end, l);
                distances[end] = graph.getEdgeWeight(edge);
            }
            else if (graph.getEdgeWeight(edge) <distances[end]) {
                List <Integer> l = new ArrayList<Integer>();
                l.add(this.pos);
                minRoutes.put(end, l);
                distances[end] = graph.getEdgeWeight(edge);
                //when the agent moves we will have to update the distances to their former neighbors by adding the edge that it has justs crossed
            }
        }
    }

    public int searchMin(){
        double min = Integer.MAX_VALUE;
        int j = -1;
        for (int i : frontierNodes) {
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
