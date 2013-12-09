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
import java.util.Arrays;

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

        String serverURL = args.length > 1 ? args[1] : "margot://192.168.3.103:2777/sparql";

        // get Model implementation for OntoQuad, load data to OntoQuad if necessary
        Model model = getPizzaModel(serverURL);

        // do some queries over data explicitly stored in OntoQuad (raw data) via RDF API
        logger.info("*** RDF API: Raw data ***");
        testRDFAPI(model);

        // create inferred model, i.e. model containing raw data as well as data which raw data logically entails;
        // here we use elements of Inference API: OWL Micro reasoner will be used to obtained entailed triples
        InfModel infmodel = ModelFactory.createInfModel(OWLMicroReasonerFactory.theInstance().create(null), model);
        logger.info("*** RDF API: Inferred data ***");
        // do some queries over inferred data
//        testRDFAPI(infmodel);

/*
        // create OntModel upon raw model using OWL Micro reasoner
        OntModelSpec ontModelSpec = new OntModelSpec(OntModelSpec.OWL_MEM_MICRO_RULE_INF);
        OntModel ontmodel = ModelFactory.createOntologyModel(ontModelSpec, model);
        // do some queries using Ontology API
        logger.info("*** Ontology API: Inferred data ***");
        testOntologyAPI(ontmodel);
*/

        //copy induced triples to separate graph in OntoQuad
//        copyInducedTriples(infmodel, model, getOntoQuadDataset(serverURL).getNamedModel(pizzaInducedGraphName));

        // do some SPARQL queries
        logger.info("*** SPARQL ***");
        testARQ(serverURL);

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
            if (!basemodel.contains(next)) {
                resmodel.add(next);
                copied_cnt++; all_cnt++;
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

    private static void testARQ(String serverURL) {
        Dataset dataset = getOntoQuadDataset(null);

        String query = "" +
        "PREFIX pizza: <"+ base + ">\n" +
        "PREFIX rdf: <"+ RDF.getURI() + ">\n" +
        "PREFIX rdfs: <"+ RDFS.getURI() + ">\n" +
        "SELECT *\n" +
                "{\n" +
                "    { GRAPH ?g { " +
                                    "?inst rdf:type ?cls." +
                "               }" +
                "       GRAPH ?g1 {" +
                                    "?cls rdfs:subClassOf pizza:Pizza." +
                "               }" +

                " } }\n" +
                "}";
        logger.info("SPARQL query:\n{}", query);
        QueryExecution qexec = QueryExecutionFactory.create(query, dataset) ;

        try {
            ResultSet resultSet = qexec.execSelect();
            ResultSetFormatter.out(resultSet);
        } finally {
            qexec.close();
        }

    }

    private static Model getPizzaModel(String server) {
        // get Model implementation for OntoQuad
        Dataset dataset = getOntoQuadDataset(server); // get dataset stored on specified server
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
            // Find configuration file dataset.ttl located in classpath
            URL config = OntoQuadJenaExample.class.getResource("/dataset.ttl");
            // Get dataset, store on server specified in config file
            dataset = (Dataset) AssemblerUtils.build(config.toString(), DatasetAssemblerVocab.tDataset);
        } else {
            // Get dataset, stored on specified server
            Store store = OntosFactory.getDefaultStoreFactory().openStore(server);
            dataset = OntosFactory.connectDataset(store);
        }

        return dataset;
    }


}
