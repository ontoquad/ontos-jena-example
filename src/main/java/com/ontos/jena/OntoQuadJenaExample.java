package com.ontos.jena;

import com.hp.hpl.jena.ontology.OntClass;
import com.hp.hpl.jena.ontology.OntModel;
import com.hp.hpl.jena.ontology.OntModelSpec;
import com.hp.hpl.jena.ontology.OntResource;
import com.hp.hpl.jena.query.*;
import com.hp.hpl.jena.rdf.model.*;
import com.hp.hpl.jena.reasoner.rulesys.OWLMicroReasonerFactory;
import com.hp.hpl.jena.sparql.core.assembler.AssemblerUtils;
import com.hp.hpl.jena.sparql.core.assembler.DatasetAssemblerVocab;
import com.hp.hpl.jena.util.FileManager;
import com.hp.hpl.jena.util.PrintUtil;
import com.hp.hpl.jena.vocabulary.RDF;
import com.hp.hpl.jena.vocabulary.RDFS;
import com.ontos.jena.impl.OntosFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * User: narefyev
 * Date: 04.12.13
 * Time: 16:50
 * To change this template use File | Settings | File Templates.
 */
public class OntoQuadJenaExample {
    private static final Logger logger = LoggerFactory.getLogger(OntoQuadJenaExample.class);

    private static final String pizzaBaseGraphName = "pizza-ontology";
    private static final String pizzaInducedGraphName = "pizza-induced";

    private static final String base = "http://www.co-ode.org/ontologies/pizza/pizza.owl#";

    private static final Resource Pizza = ResourceFactory.createResource(base + "Pizza");

    private static final Resource CheeseyPizza = ResourceFactory.createResource(base + "CheeseyPizza");
    private static final Resource MeatyPizza = ResourceFactory.createResource(base + "MeatyPizza");

    private static final Resource SpicyPizza = ResourceFactory.createResource(base + "SpicyPizza");

    private static final Property hasTopping = ResourceFactory.createProperty(base + "hasTopping");

    public static void main(String args[]){
        // Optional: initialize printing facilities
        PrintUtil.init();
        PrintUtil.registerPrefix("pizza", base);

        String serverURL = args.length > 0 ? args[0] : null;
        Dataset dataset = getOntoQuadDataset(serverURL);

        // get Model implementation for OntoQuad, load data to OntoQuad if necessary
        Model model = getPizzaModel(dataset);

        // do some queries over data explicitly stored in OntoQuad (raw data) via RDF API
        logger.info("*** RDF API: Raw data ***");
        testRDFAPI(model);

        // create inferred model, i.e. model containing raw data as well as data which raw data logically entails;
        // here we use elements of Inference API: OWL Micro reasoner will be used to obtained entailed triples
        InfModel infmodel = ModelFactory.createInfModel(OWLMicroReasonerFactory.theInstance().create(null), model);
        logger.info("*** RDF API: Inferred data ***");
        // do some queries over inferred data
        testRDFAPI(infmodel);


        // create OntModel upon raw model using OWL Micro reasoner
        OntModelSpec ontModelSpec = new OntModelSpec(OntModelSpec.OWL_MEM_MICRO_RULE_INF);
        OntModel ontmodel = ModelFactory.createOntologyModel(ontModelSpec, model);
        // do some queries using Ontology API
        logger.info("*** Ontology API: Inferred data ***");
        testOntologyAPI(ontmodel);


        //copy induced triples to separate graph in OntoQuad
        copyInducedTriples(infmodel, model, dataset.getNamedModel(pizzaInducedGraphName));

        // do some SPARQL queries
        logger.info("*** SPARQL ***");
        testSPARQL(dataset);

    }

    private static void testRDFAPI(Model model) {
        for (Resource cls : Arrays.asList(Pizza, MeatyPizza, CheeseyPizza, SpicyPizza)) {
            logger.info("Triples for pattern (* rdf:type " + cls.getLocalName() + "):");
            // Find triples with rdf:type as a predicate and cls as an object, subjects of found triples represent
            // instances of class cls.
            StmtIterator it = model.listStatements(null, RDF.type, cls);
            while (it.hasNext()) {
                Statement next = it.next();
                logger.info("--- " + PrintUtil.print(next));
            }
        }
    }

    private static void copyInducedTriples(InfModel infmodel, Model basemodel, Model resmodel) {
        resmodel.removeAll();
        logger.info("Copying induced triples...");
        StmtIterator it = infmodel.listStatements(null, null, (RDFNode) null);
        int copied_cnt = 0, all_cnt = 0;
        while (it.hasNext()) {
            Statement next = it.next();
            all_cnt++;
            if (!basemodel.contains(next)) {
                resmodel.add(next);
                copied_cnt++;
            }
        }
        logger.info("{} triples are processed, {} induced triples are copied, {} triples are in the base model",
                all_cnt, copied_cnt, basemodel.size());
    }

    private static void testOntologyAPI(OntModel ontmodel) {
        for (Resource res : Arrays.asList(Pizza, MeatyPizza, CheeseyPizza, SpicyPizza)) {
            OntClass cls = ontmodel.getOntClass(res.getURI());
            logger.info("Instances of " + cls.getLocalName()) ;
            for (OntResource inst : cls.listInstances().toSet()) {
                logger.info("--- " + inst.getLocalName());
            }
        }
    }

    private static void testSPARQL(Dataset dataset) {
        printSparqlQueryResult(dataset, "SELECT * WHERE {?s ?p ?o} limit 10");
        printSparqlQueryResult(dataset, "SELECT * WHERE { graph <pizza-ontology> {?s ?p ?o}} limit 10");

        String prefixes =
                "PREFIX pizza: <"+ base + ">\n" +
                        "PREFIX rdf: <"+ RDF.getURI() + ">\n" +
                        "PREFIX rdfs: <"+ RDFS.getURI() + ">\n";

        String query = prefixes +
                "SELECT *\n" +
                "{\n" +
                "    GRAPH ?g { \n" +
                "                    ?inst rdf:type ?cls \n" +
                "               } \n" +
                "     GRAPH ?g1 { \n" +
                "                    ?cls rdfs:subClassOf pizza:Pizza \n" +
                "               } \n" +
                "}";

        printSparqlQueryResult(dataset, query);

        Map<String, Set<String>> map = inst2classes(dataset, query);
        System.out.println(map);

        query = prefixes +
                "SELECT *\n" +
                "{\n" +
                "                    ?inst rdf:type ?cls. \n" +
                "                    ?cls rdfs:subClassOf pizza:Pizza. \n" +
                "}";

        printSparqlQueryResult(dataset, query);
    }

    /**
     * Executes specified SPARQL query over specified dataset and prints results to stdout.
     */
    private static void printSparqlQueryResult(Dataset dataset, String query) {
        logger.info("SPARQL query:\n{}", query);
        QueryExecution qexec = QueryExecutionFactory.create(query, dataset) ;

        try {
            ResultSet resultSet = qexec.execSelect();
            ResultSetFormatter.out(resultSet);
        } finally {
            qexec.close();
        }
    }

    private static Map<String, Set<String>> inst2classes(Dataset dataset, String query){
        logger.info("SPARQL query:\n{}", query);
        QueryExecution qexec = QueryExecutionFactory.create(query, dataset) ;

        try {
            ResultSet resultSet = qexec.execSelect();

            Map<String, Set<String>> inst2classes = new HashMap<String, Set<String>>();

            while (resultSet.hasNext()) {
                QuerySolution sol = resultSet.next();
                String key = sol.get("?inst").toString();
                String value = sol.get("?cls").toString();
                Set<String> vals = inst2classes.get(key);
                if (vals==null)
                    inst2classes.put(key, vals = new HashSet<String>());
                vals.add(value);
            }

            return inst2classes;
        } finally {
            qexec.close();
        }
    }

    private static Model getPizzaModel(Dataset dataset) {
        Model model = dataset.getNamedModel(pizzaBaseGraphName); // get Model representing graph with specified name from dataset

//        model.removeAll();
        if (model.size() == 0) { // if data was not loaded yet
            logger.info("Loading data to OntoQuad...");
            // load pizza ontology from file
            FileManager.get().readModel(model, "pizza-latest.owl");
            // and create some instances
            createInstances(model);
            logger.info("Data is loaded.");
        }

        return model;
    }

    private static void createInstances(Model model) {
        model.createResource(base + "_pizza1", Pizza)
                .addProperty(hasTopping, model.createResource(model.getResource(base + "MozzarellaTopping")))
                .addProperty(hasTopping, model.createResource(model.getResource(base + "CajunSpiceTopping")));

        model.createResource(base + "_pizza2")
                .addProperty(hasTopping, model.createResource(model.getResource(base + "PeperoniSausageTopping")))
                .addProperty(hasTopping, model.createResource(model.getResource(base + "OliveTopping")))
                .addProperty(hasTopping, model.createResource(model.getResource(base + "OnionTopping")));
    }

    private static Dataset getOntoQuadDataset(String server) {
        Dataset dataset; // Dataset is a collection of several named graphs and one default graph.
        if (server==null) {
            logger.info("Server is not specified, using configuration file.");
            // Find configuration file dataset.ttl located in classpath
            URL config = OntoQuadJenaExample.class.getResource("/dataset.ttl");
            // Get dataset, store on server specified in config file
            dataset = (Dataset) AssemblerUtils.build(config.toString(), DatasetAssemblerVocab.tDataset);
        } else {
            logger.info("Server is specified: {}", server);
            // Get dataset, stored on specified server
            Store store = OntosFactory.getDefaultStoreFactory().openStore(server);
            dataset = OntosFactory.connectDataset(store);
        }

        return dataset;
    }


}
