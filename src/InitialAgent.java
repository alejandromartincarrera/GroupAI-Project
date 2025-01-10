
package jadelab2;

import jade.core.Agent;
import jade.domain.DFService;
import jade.domain.FIPAException;

import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.*;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;

import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.Graph;
import org.jgrapht.ext.JGraphXAdapter;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleWeightedGraph;
import org.jgrapht.alg.connectivity.ConnectivityInspector;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.HashSet;
import java.util.Random;

public class InitialAgent extends Agent {
    private AID[] sellerAgents;

    private Graph<Integer, DefaultWeightedEdge> graph;

    public static final String csvFilePath = "graph.csv";




    protected void setup() {

        //we need to wait a bit for the SalesmanAgents to register themselves on DF
        try {
            Thread.sleep(1000);
        }
        catch (Exception ex){
            System.out.println(ex);
        }

        //searches for all the SalesmanAgents and saves its names
        DFAgentDescription template = new DFAgentDescription();
        ServiceDescription sd = new ServiceDescription();
        sd.setType("SalesmanAgent");
        template.addServices(sd);
        try
        {
            DFAgentDescription[] result = DFService.search(this, template);
            System.out.println(getAID().getLocalName() + ": the following agents have been found");
            sellerAgents = new AID[result.length];
            for (int i = 0; i < result.length; ++i)
            {
                sellerAgents[i] = result[i].getName();
                System.out.println(getAID().getLocalName()+": "+sellerAgents[i].getLocalName());
            }
        }
        catch (FIPAException fe)
        {
            fe.printStackTrace();
        }

        //loads graph
        this.graph = readGraphFromCSV(this.csvFilePath);
        //calculate random positions for agents
        int[] positions = new int[this.sellerAgents.length];
        asignRandomPos(positions);
        //save a String of "pos agent 1, pos agent 2, ... , pos agent n"
        String posContent="";
        for (int i=0; i<positions.length; i++) {
            if (i<positions.length-1){
                posContent+=Integer.toString(positions[i])+",";
            }
            else{
                posContent+=Integer.toString(positions[i]);
            }
        }
        //send each agent a message with positions
        for (int i=0; i<positions.length; i++) {
            ACLMessage informPos = new ACLMessage(ACLMessage.INFORM);
            informPos.addReceiver(this.sellerAgents[i]);
            informPos.setContent(Integer.toString(this.sellerAgents.length) + "," + Integer.toString(i) + "," + posContent);
            informPos.setConversationId(Integer.toString(i));
            informPos.setReplyWith("cfp"+System.currentTimeMillis()); //unique value
            this.send(informPos);
            System.out.println(getAID().getLocalName()+" message sent");
        }

    System.out.println(getAID().getLocalName()+": end of initialization");

    }

    protected void takeDown() {
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

    public void asignRandomPos(int[] agentsPos) {
        Set<Integer> drawnNumbers = new HashSet<Integer>();
        Random random = new Random();
        for (int i=0; i<this.sellerAgents.length; i++){
            boolean end = false;
            int randomValue=0;
            while (!end) {
                randomValue = random.nextInt(this.graph.vertexSet().size());
                if (!drawnNumbers.contains(randomValue)){
                    end=true;
                }
            }
            drawnNumbers.add(randomValue);
            agentsPos[i]=randomValue;
        }
    }
}
