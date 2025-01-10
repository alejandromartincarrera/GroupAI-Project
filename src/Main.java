import com.mxgraph.layout.mxCircleLayout;
import com.mxgraph.swing.mxGraphComponent;
import org.jgrapht.Graphj;
import org.jgrapht.ext.JGraphXAdapter;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleWeightedGraph;
import org.jgrapht.alg.connectivity.ConnectivityInspector;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.util.Map;
import java.util.Random;
import java.util.List;
import java.util.Set;



public class Main {

    //load csv file if exists, if not create random graph and save it in a csv file - done
    //create random number of agents and place them in random positions

    // Parameters for the graph
    public static final int numNodes = 10;  // Number of nodes
    int numEdges = 15;  // Number of edges
    int minWeight = 1;  // Minimum weight
    int maxWeight = 10; // Maximum weight

    public static final int numAgents=3;

    // Create and initialize the graph
    Graph<Integer, DefaultWeightedEdge> graph = new SimpleWeightedGraph<>(DefaultWeightedEdge.class);
    String csvFilePath = "graph.csv";

    int[] agentsPos;
    SalesmanAgent[] agents;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            Main main = new Main();
            main.loadOrCreateGraph();
            main.ensureConnectivity();  // Ensure all nodes are connected
            main.visualizeGraph();  // Visualize the graph
            System.out.println("Graph:");
            main.printGraph();  // Print the graph for console output
            if (main.isBidirectional()) {
                System.out.println("The graph is bidirectional.");
            } else {
                System.out.println("The graph is not bidirectional.");
            }

            int[] agentsPos = new int[numAgents];
            main.asignRandomPos(agentsPos);
            SalesmanAgent[] agents = new SalesmanAgent[numAgents];
            main.createAgents(agents, agentsPos, graph);
        });
    }

    public void asignRandomPos(int[] agentsPos) {
        Random random = new Random();
        for (int i=0; i<numAgents; i++){
            int randomValue = random.nextInt(numNodes);
            agentsPos[i]=randomValue;
        }
    }

    public void createAgents(SalesmanAgent[] agents, int[] agentsPos, Graph<Integer, DefaultWeightedEdge> graph){
        for (int i=0; i<numAgents; i++) {
            agents[i]= new SalesmanAgent(i, agentsPos, graph);
        }
    }

    // Load graph from CSV if it exists, otherwise create a random graph
    public void loadOrCreateGraph() {
        File file = new File(csvFilePath);

        if (file.exists()) {
            // Load the graph from the CSV file
            System.out.println("Loading graph from CSV...");
            graph = readGraphFromCSV(csvFilePath);
        } else {
            // Create a random graph and save it to CSV
            System.out.println("CSV file not found, creating a random graph...");
            createRandomGraph();
            writeGraphToCSV(csvFilePath);
        }
    }

    // Write the graph to a CSV file
    public void writeGraphToCSV(String filePath) {
        try (PrintWriter pw = new PrintWriter(new FileWriter(filePath))) {
            for (DefaultWeightedEdge edge : graph.edgeSet()) {
                int source = graph.getEdgeSource(edge);
                int target = graph.getEdgeTarget(edge);
                double weight = graph.getEdgeWeight(edge);
                pw.println(source + "," + target + "," + weight);
            }
            System.out.println("Graph saved to CSV.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Read the graph from a CSV file
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
            System.out.println("Graph loaded from CSV.");
        } catch (IOException e) {
            e.printStackTrace();
        }
        return graph;
    }


    public void createRandomGraph() {
        // Add nodes
        for (int i = 0; i < numNodes; i++) {
            graph.addVertex(i);
        }

        // Randomly connect all nodes to form a graph
        Random random = new Random();
        for (int i = 0; i < numNodes; i++) {
            // Pick a random target node from [0, numNodes - 1]
            int target = random.nextInt(numNodes);
            // Avoid self-loops
            while (target == i) {
                target = random.nextInt(numNodes);
            }
            DefaultWeightedEdge edge = graph.addEdge(i, target);
            //this is unidirectional , you can go from i to target but not from target to i
            if (edge != null) {
                double weight = minWeight + (maxWeight - minWeight) * random.nextDouble();
                String formattedWeight = String.format("%.2f", weight); // Format to 2 decimal places
                graph.setEdgeWeight(edge,  Double.parseDouble(formattedWeight));
            }
        }


        // Add additional edges to meet the desired number of edges
        int additionalEdges = numEdges - (numNodes - 1); // Remaining edges to add
        while (additionalEdges > 0) {
            int source = random.nextInt(numNodes);
            int target = random.nextInt(numNodes);

            // Avoid self-loops and duplicate edges
            if (source != target && !graph.containsEdge(source, target)) {
                DefaultWeightedEdge edge = graph.addEdge(source, target);
                if (edge != null) {
                    double weight = minWeight + (maxWeight - minWeight) * random.nextDouble();
                    String formattedWeight = String.format("%.2f", weight); // Format to 2 decimal places
                    graph.setEdgeWeight(edge,  Double.parseDouble(formattedWeight));
                    additionalEdges--;
                }
            }
        }
    }

    // Step 2: Ensure all nodes are connected (check for unconnected components and add edges between them)
    public void ensureConnectivity() {
        // Check if the graph is connected
        ConnectivityInspector<Integer, DefaultWeightedEdge> inspector = new ConnectivityInspector<>(graph);
        List<Set<Integer>> connectedComponents = inspector.connectedSets();

        // If there is more than one connected component, add edges to connect them
        if (connectedComponents.size() > 1) {
            // Pick the first two components
            Set<Integer> firstComponent = connectedComponents.get(0);
            Set<Integer> secondComponent = connectedComponents.get(1);

            // Pick a node from each component to create an edge
            Integer source = firstComponent.iterator().next();  // Pick an arbitrary node from the first component
            Integer target = secondComponent.iterator().next();  // Pick an arbitrary node from the second component

            DefaultWeightedEdge edge = graph.addEdge(source, target);
            if (edge != null) {
                double weight = minWeight + (maxWeight - minWeight) * new Random().nextDouble();
                String formattedWeight = String.format("%.2f", weight); // Format to 2 decimal places
                graph.setEdgeWeight(edge,  Double.parseDouble(formattedWeight));
            }
        }
    }

    public void visualizeGraph() {
        // Create and display the GUI
        GraphVisualizerFrame frame = new GraphVisualizerFrame(graph);
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

    public boolean isBidirectional() {
        for (DefaultWeightedEdge edge : graph.edgeSet()) {
            Integer source = graph.getEdgeSource(edge);
            Integer target = graph.getEdgeTarget(edge);

            if (!graph.containsEdge(target, source)) {
                System.out.println("Missing reverse edge: " + source + " <--> " + target);
                return false;  // Return false if the reverse edge is not found
            }
        }
        return true;  // Return true if all edges have a reverse
    }

    // Visualize the graph using JGraphX
    class GraphVisualizerFrame extends JFrame {
        public GraphVisualizerFrame(Graph<Integer, DefaultWeightedEdge> graph) {
            JGraphXAdapter<Integer, DefaultWeightedEdge> graphAdapter = new JGraphXAdapter<>(graph);

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
        }
    }
}
