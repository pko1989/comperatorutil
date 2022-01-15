package de.koesselp;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.math.NumberUtils;
import org.xmlunit.builder.DiffBuilder;
import org.xmlunit.builder.Input;
import org.xmlunit.diff.Comparison;
import org.xmlunit.diff.ComparisonResult;
import org.xmlunit.diff.ComparisonType;
import org.xmlunit.diff.DefaultNodeMatcher;
import org.xmlunit.diff.Diff;
import org.xmlunit.diff.Difference;
import org.xmlunit.diff.DifferenceEvaluator;
import org.xmlunit.diff.ElementSelectors;

/**
 * Hello world!
 *
 */
public class App {
    public static void main(String[] args) throws FileNotFoundException, IOException {
        System.out.println("************************************************************");
        System.out.println("Start comparing:");
        System.out.println("");
        System.out.println((new File("ControlXML")).getAbsolutePath());
        List<File> inputXMLFiles = Arrays
                .asList((new File("ControlXML")).listFiles(file -> file.getName().endsWith(".xml")));
        List<File> outputXMLFiles = Arrays
                .asList((new File("TestXML")).listFiles(file -> file.getName().endsWith(".xml")));

        Properties props = new Properties();

        try (FileInputStream propInputStream = new FileInputStream("comparator_configs.properties")) {
            props.load(propInputStream);
        }

        final double epsilon = Double.parseDouble(props.getProperty("epsilon"));

        Set<String> nodeNamesToIgnore = Stream.of(props.getProperty("ignored-nodes").split(";"))
                .filter(s -> !s.trim().isEmpty()).map(String::trim).collect(Collectors.toSet());

        if (!nodeNamesToIgnore.isEmpty()) {
            System.out.println("Ignored Nodes present, see report.txt for details");
            System.out.println("");
        }

        try (FileOutputStream fileOutputStream = new FileOutputStream("report.txt")) {
            fileOutputStream.write(
                    "***********************************REPORT**********************************\n\n".getBytes());
            fileOutputStream.write("######################################\n".getBytes());
            fileOutputStream.write(("Comparison precision: " + epsilon + "\n").getBytes());
            
            if (!nodeNamesToIgnore.isEmpty()) {
                fileOutputStream.write(("Ignored Nodes:\n" + nodeNamesToIgnore + "\n").getBytes());
            }
            
            fileOutputStream.write("######################################\n\n".getBytes());

            for (File inputXML : inputXMLFiles) {
                Optional<File> outputXML = outputXMLFiles.stream()
                        .filter(xmlfile -> xmlfile.getName().equals(inputXML.getName())).findAny();

                if (!outputXML.isPresent()) {
                    String notFoundMessage = "Skipped  " + inputXML.getName() + ". (Not present in outputs)\n";
                    fileOutputStream.write(notFoundMessage.getBytes());
                    System.out.println(inputXML.getName() + " NOT PRESENT!");
                } else {
                    Diff diff = DiffBuilder.compare(Input.fromFile(inputXML)).withTest(Input.fromFile(outputXML.get()))
                            .checkForIdentical()
                            .ignoreWhitespace()
                            .withNodeFilter(node -> !nodeNamesToIgnore.contains(node.getNodeName()))
                            .withNodeMatcher(new DefaultNodeMatcher(ElementSelectors.byName))
                            .withDifferenceEvaluator(getDifferenceEvaluator(epsilon))
                            .build();

                    if (!diff.hasDifferences()) {
                        String okMessage = inputXML.getName() + ": OK!\n";
                        fileOutputStream.write(okMessage.getBytes());
                        System.out.println(inputXML.getName() + ": OK!");
                    } else {
                        String errorMessageBeginn = "---------------------------------\n";
                        errorMessageBeginn += inputXML.getName() + " has following differences: \n";
                        fileOutputStream.write(errorMessageBeginn.getBytes());
                        System.out.println(inputXML.getName() + ": DIFFERENCES, see report.txt!");

                        for (Difference difference : diff.getDifferences()) {
                            String diffMessage = String.format("Difference at: %s. Expected: %s. But was: %s  %n",
                                    difference.getComparison().getControlDetails().getXPath(),
                                    difference.getComparison().getControlDetails().getValue(),
                                    difference.getComparison().getTestDetails().getValue());
                            fileOutputStream.write(diffMessage.getBytes());
                        }

                        String errorMessageEnd = "---------------------------------\n";
                        fileOutputStream.write(errorMessageEnd.getBytes());
                    }
                }
            }

        }
        System.out.println("");
        System.out.println("Comparing finished");
        System.out.println("************************************************************");
    }

    private static DifferenceEvaluator getDifferenceEvaluator(double epsilon) {
        return new DifferenceEvaluator() {

            @Override
            public ComparisonResult evaluate(Comparison comparison, ComparisonResult outcome) {
                if (outcome != ComparisonResult.EQUAL) {
                    if (ComparisonType.TEXT_VALUE == comparison.getType() && NumberUtils
                            .isCreatable(comparison.getControlDetails().getValue().toString())&& NumberUtils
                            .isCreatable(comparison.getTestDetails().getValue().toString())) {
                        double testValue = Double.parseDouble(comparison.getTestDetails().getValue().toString());
                        double controlValue = Double.parseDouble(comparison.getControlDetails().getValue().toString());
                        return Math.abs(testValue - controlValue) < epsilon ? ComparisonResult.EQUAL : outcome;
                    }
                }

                return outcome;
            }

        };
    }
}
