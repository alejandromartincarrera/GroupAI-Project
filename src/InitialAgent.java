
package jadelab2;

import com.mxgraph.layout.mxCircleLayout;
import com.mxgraph.swing.mxGraphComponent;
import org.jgrapht.Graph;
import org.jgrapht.ext.JGraphXAdapter;
import com.mxgraph.model.mxCell;
import com.mxgraph.util.mxConstants;
import com.mxgraph.model.mxCell;


import com.mxgraph.layout.mxCircleLayout;
import com.mxgraph.swing.mxGraphComponent;
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

import javax.swing.*;
import java.awt.*;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.List;

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

        GraphVisualizerFrame frame = new GraphVisualizerFrame(graph);
        visualizeGraph(frame);  // Visualize the graph
        String color1= "#FFECA1";
        String color2="#BCF9FB";
        System.out.println("Graph:");
        printGraph();  // Print the graph for console output



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

        List<String> colors = generateDistinctColors(positions.length);
        for (int i=0; i<positions.length; i++) {
            frame.changeVertexColor(positions[i], colors.get(i));
        }

        System.out.println(getAID().getLocalName()+": end of initialization");

        boolean end = false;
        while (! end) {
            //wait to receive the newPosition
            ACLMessage msg = this.blockingReceive();
            if (msg.getPerformative()==ACLMessage.INFORM) {
                String content = msg.getContent();
                int newPosition = Integer.parseInt(content);
                AID sender = msg.getSender();
                for (int i=0; i<this.sellerAgents.length; i++) {
                    if (sender.equals(this.sellerAgents[i])){
                        frame.changeVertexColor(newPosition, colors.get(i));
                    }
                }
            }
            else {
                end=true;
            }
        }


        doDelete();
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

    public class GraphVisualizerFrame extends JFrame {
        private JGraphXAdapter<Integer, DefaultWeightedEdge> graphAdapter;
        private Map<Integer, Object> vertexToCellMap; // Maps vertex to cell
        public GraphVisualizerFrame(Graph<Integer, DefaultWeightedEdge> graph) {
            graphAdapter = new JGraphXAdapter<>(graph);
            populateVertexToCellMap(graph);

            Map<String, Object> vertexStyle = graphAdapter.getStylesheet().getDefaultVertexStyle();
            vertexStyle.put("shape", "rectangle");
            vertexStyle.put("fillColor", "#FFFFFF");
            vertexStyle.put("strokeColor", "#000000");
            vertexStyle.put("strokeWidth", 1.0);
            vertexStyle.put("fontColor", "#000000");
            vertexStyle.put("fontSize", 20);

            Map<String, Object> edgeStyle = graphAdapter.getStylesheet().getDefaultEdgeStyle();
            edgeStyle.put("strokeColor", "#000000");
            edgeStyle.put("strokeWidth", 1.0);
            edgeStyle.put("fontSize", 12);

            mxCircleLayout layout = new mxCircleLayout(graphAdapter);
            layout.setRadius(350);  // Increase radius for more spacing
            layout.execute(graphAdapter.getDefaultParent());

            // Create a graph component to visualize the graph
            mxGraphComponent graphComponent = new mxGraphComponent(graphAdapter);
            getContentPane().add(graphComponent, BorderLayout.CENTER);

            // Customize the window
            setTitle("Graph Visualizer");

            System.out.println(graph.edgeSet());
            for (DefaultWeightedEdge edge : graph.edgeSet()) {
                // Retrieve the corresponding mxCell for the edge
                mxCell cell = (mxCell) graphAdapter.getEdgeToCellMap().get(edge);
                if (cell != null) {
                    // Set the edge label to its weight
                    double weight = graph.getEdgeWeight(edge);
                    System.out.println(weight);
                    cell.setValue(String.valueOf(weight));
                }
                else{
                    System.out.println("cell is null");
                }
            }

        }
        private void populateVertexToCellMap(Graph<Integer, DefaultWeightedEdge> graph) {
            vertexToCellMap = new HashMap<>();
            for (Integer vertex : graph.vertexSet()) {
                Object cell = graphAdapter.getVertexToCellMap().get(vertex);
                vertexToCellMap.put(vertex, cell);
            }
        }
        public void changeVertexColor(Integer vertex, String colorHex) {
            Object cell = vertexToCellMap.get(vertex);
            if (cell != null && cell instanceof mxCell) {
                mxCell mxCellVertex = (mxCell) cell;
                if (mxCellVertex.isVertex()) {
                    // Begin updating the graph model
                    graphAdapter.getModel().beginUpdate();
                    try {
                        // Set the new fill color
                        graphAdapter.setCellStyle(mxConstants.STYLE_FILLCOLOR + "=" + colorHex, new Object[]{cell});
                    } finally {
                        // Ensure the model is always ended properly
                        graphAdapter.getModel().endUpdate();
                    }
                }
            } else {
                System.out.println("Vertex " + vertex + " does not exist in the graph.");
            }
        }
    }

    public void visualizeGraph(GraphVisualizerFrame frame) {
        // Create and display the GUI
        //GraphVisualizerFrame frame = new GraphVisualizerFrame(graph);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(860, 860);
        frame.setVisible(true);
    }



    public void printGraph() {
        for (DefaultWeightedEdge edge : graph.edgeSet()) {
            System.out.println(graph.getEdgeSource(edge) + " <--> " + graph.getEdgeTarget(edge) +
                    " with weight " + graph.getEdgeWeight(edge));
        }
    }

    public static java.util.List<String> generateDistinctColors(int n) {
        java.util.List<String> colorList = new ArrayList<>();
        float hueStep = 1.0f / n; // Calculate the step to distribute hues evenly

        for (int i = 0; i < n; i++) {
            float hue = i * hueStep;
            float saturation = 0.7f; // Saturation between 0.5 and 1.0 for vivid colors
            float brightness = 0.9f; // Brightness between 0.7 and 1.0 for bright colors

            Color color = Color.getHSBColor(hue, saturation, brightness);
            String hex = String.format("#%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue());
            colorList.add(hex);
        }

        return colorList;
    }
}
