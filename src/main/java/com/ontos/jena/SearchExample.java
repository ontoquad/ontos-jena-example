package com.ontos.jena;

import com.hp.hpl.jena.ontology.OntClass;
import com.hp.hpl.jena.ontology.OntModel;
import com.hp.hpl.jena.ontology.OntModelSpec;
import com.hp.hpl.jena.ontology.OntResource;
import com.hp.hpl.jena.query.Dataset;
import com.hp.hpl.jena.rdf.model.*;
import com.hp.hpl.jena.reasoner.ValidityReport;
import com.hp.hpl.jena.reasoner.rulesys.OWLFBRuleReasonerFactory;
import com.hp.hpl.jena.reasoner.rulesys.OWLMicroReasonerFactory;
import com.hp.hpl.jena.reasoner.rulesys.OWLMiniReasonerFactory;
import com.hp.hpl.jena.tdb.TDBFactory;
import com.hp.hpl.jena.util.FileManager;
import com.hp.hpl.jena.vocabulary.RDF;
import com.hp.hpl.jena.vocabulary.RDFS;
import com.ontos.jena.assembler.ConnectionDescr;
import com.ontos.jena.impl.OntosFactory;

import java.net.URI;
import java.util.*;

public class SearchExample {
    private static final String base = "http://www.co-ode.org/ontologies/pizza/pizza.owl#";

    private static final Resource Pizza = ResourceFactory.createResource(base + "Pizza");

    private static final Resource CheeseyPizza = ResourceFactory.createResource(base + "CheeseyPizza");
        private static final Resource MeatyPizza = ResourceFactory.createResource(base + "MeatyPizza");

    private static final Resource SpicyPizza = ResourceFactory.createResource(base + "SpicyPizza");

    private static final Property hasTopping = ResourceFactory.createProperty(base + "hasTopping");

    public static void main(String args[]){
        Model model = getPizzaModel();
        InfModel infmodel = ModelFactory.createInfModel(OWLMicroReasonerFactory.theInstance().create(null), model);


//        infmodel.prepare();

//        validate(infmodel);


/*
        testStatements(model, infmodel, null, RDF.type, Pizza);

        testStatements(model, infmodel, null, RDF.type, MeatyPizza);
        testStatements(model, infmodel, null, RDF.type, CheeseyPizza);

        testStatements(model, infmodel, null, RDF.type, SpicyPizza);
*/

        OntModelSpec ontModelSpec = new OntModelSpec(OntModelSpec.OWL_MEM_MICRO_RULE_INF);
        OntModel ontmodel = ModelFactory.createOntologyModel(ontModelSpec, model);
        OntClass pizzaClass = ontmodel.getOntClass(Pizza.getURI());
        listInstances(pizzaClass);
        for (OntClass ontClass : pizzaClass.listSubClasses(true).toSet()) {
            listInstances(ontClass);
        }

    }

    private static void listInstances(OntClass cls) {
        System.out.println("Instances of " + cls.getLocalName());
        for (OntResource inst : cls.listInstances().toSet()) {
            System.out.println("--- " + inst.getLocalName());
        }

    }

    private static void validate(InfModel infmodel) {
        ValidityReport report = infmodel.validate();
        System.out.println("Model is " + (report.isValid() ? "" : "not" ) + " consistent.");
        Iterator<ValidityReport.Report> it = report.getReports();
        while (it.hasNext()) {
            ValidityReport.Report next = it.next();
            System.out.println(next);
        }
    }

    private static void testStatements(Model model, InfModel infmodel, Resource s, Property p, RDFNode o) {
        int cnt;
        long st;
        System.out.println("*** Explicit statements: ");
        st = System.currentTimeMillis();
        cnt = printStatements(model, s, p, o);
        System.out.println("*** " + cnt + " statements found in "+(System.currentTimeMillis()-st)+"ms.\n");

        System.out.println("*** Implicit statements:");
        st = System.currentTimeMillis();
        cnt = printStatements(infmodel, s, p, o);
        System.out.println("*** " + cnt + " statements found in "+(System.currentTimeMillis()-st)+"ms.\n");

    }

    private static Model getPizzaModel() {
        Model pizza_model = getOntoQuadModel("pizza_ontology");

        if (pizza_model.size() == 0) {
            FileManager.get().readModel(pizza_model, "pizza-latest.owl");
            createInstances(pizza_model);
        }

        return pizza_model;
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

    private static int printStatements(Model model, Resource s, Property p, RDFNode o) {
        int i = 0;
        StmtIterator it = model.listStatements(s, p, o);
        while (it.hasNext()) {
            Statement next = it.next();
            i++;
            System.out.println(next);
        }
        return i;
    }

    private static Model getOntoQuadModel(String graph) {
        Properties props = new Properties();
        String name = "auto-commit";
        String value = "true";
        props.put(name, value);

        ConnectionDescr connDescr = new ConnectionDescr("margot://192.168.3.103:2777/sparql",props);
        Store store = OntosFactory.getDefaultStoreFactory().openStore(connDescr.getUrl(), connDescr.getProperties());

        Dataset dataset = OntosFactory.connectDataset(store);
        return dataset.getNamedModel(graph);
    }

}
