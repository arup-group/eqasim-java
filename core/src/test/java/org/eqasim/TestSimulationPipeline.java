package org.eqasim;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.eqasim.core.analysis.run.RunLegAnalysis;
import org.eqasim.core.analysis.run.RunPublicTransportLegAnalysis;
import org.eqasim.core.analysis.run.RunTripAnalysis;
import org.eqasim.core.scenario.cutter.RunScenarioCutter;
import org.eqasim.core.simulation.EqasimConfigurator;
import org.eqasim.core.simulation.analysis.EqasimAnalysisModule;
import org.eqasim.core.simulation.mode_choice.AbstractEqasimExtension;
import org.eqasim.core.simulation.mode_choice.EqasimModeChoiceModule;
import org.eqasim.core.simulation.mode_choice.epsilon.AdaptConfigForEpsilon;
import org.eqasim.core.simulation.mode_choice.parameters.ModeParameters;
import org.eqasim.core.simulation.modes.drt.analysis.run.RunDrtPassengerAnalysis;
import org.eqasim.core.simulation.modes.drt.analysis.run.RunDrtVehicleAnalysis;
import org.eqasim.core.simulation.modes.drt.utils.AdaptConfigForDrt;
import org.eqasim.core.simulation.modes.drt.utils.CreateDrtVehicles;
import org.eqasim.core.simulation.modes.feeder_drt.analysis.run.RunFeederDrtPassengerAnalysis;
import org.eqasim.core.simulation.modes.feeder_drt.mode_choice.FeederDrtModeAvailabilityWrapper;
import org.eqasim.core.simulation.modes.feeder_drt.utils.AdaptConfigForFeederDrt;
import org.eqasim.core.simulation.modes.transit_with_abstract_access.mode_choice.TransitWithAbstractAccessModeAvailabilityWrapper;
import org.eqasim.core.simulation.modes.transit_with_abstract_access.utils.AdaptConfigForTransitWithAbstractAccess;
import org.eqasim.core.simulation.modes.transit_with_abstract_access.utils.CreateAbstractAccessItemsForTransitLines;
import org.eqasim.core.standalone_mode_choice.RunStandaloneModeChoice;
import org.eqasim.core.standalone_mode_choice.StandaloneModeChoiceConfigurator;
import org.eqasim.core.tools.*;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.Person;
import org.matsim.contribs.discrete_mode_choice.model.DiscreteModeChoiceTrip;
import org.matsim.contribs.discrete_mode_choice.model.mode_availability.ModeAvailability;
import org.matsim.core.config.CommandLine;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.ControllerConfigGroup;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.misc.CRCChecksum;

import com.google.inject.Inject;
import com.google.inject.Provider;

public class TestSimulationPipeline {

    @Before
    public void setUp() throws IOException {
        URL fixtureUrl = getClass().getClassLoader().getResource("melun");
        FileUtils.copyDirectory(new File(fixtureUrl.getPath()), new File("melun_test/input"));
        FileUtils.forceMkdir(new File("melun_test/exports"));
    }

    @After
    public void tearDown() throws IOException {
        FileUtils.deleteDirectory(new File("melun_test"));
    }

    private void runMelunSimulation(String configPath, String outputPath) {
        runMelunSimulation(configPath, outputPath, null, null);
    }

    private void runMelunSimulation(String configPath, String outputPath, String inputPlansFile, Integer lastIteration) {
        EqasimConfigurator eqasimConfigurator = new EqasimConfigurator();
        Config config = ConfigUtils.loadConfig(configPath, eqasimConfigurator.getConfigGroups());
        ((ControllerConfigGroup) config.getModules().get(ControllerConfigGroup.GROUP_NAME)).setOutputDirectory(outputPath);
        if(inputPlansFile != null) {
            config.plans().setInputFile(inputPlansFile);
        }
        if(lastIteration != null) {
            config.controller().setLastIteration(lastIteration);
        }
        eqasimConfigurator.addOptionalConfigGroups(config);

        Scenario scenario = ScenarioUtils.createScenario(config);
        eqasimConfigurator.configureScenario(scenario);
        ScenarioUtils.loadScenario(scenario);
        eqasimConfigurator.adjustScenario(scenario);


        Controler controller = new Controler(scenario);
        eqasimConfigurator.configureController(controller);
        controller.addOverridingModule(new EqasimModeChoiceModule());
        controller.addOverridingModule(new EqasimAnalysisModule());
        controller.addOverridingModule(new AbstractEqasimExtension() {
            @Override
            protected void installEqasimExtension() {
                bind(ModeParameters.class);
                bindModeAvailability("DefaultModeAvailability").toProvider(new Provider<>() {
                    @Inject
                    private Config config;

                    @Override
                    public ModeAvailability get() {
                        FeederDrtModeAvailabilityWrapper feederDrtModeAvailabilityWrapper = new FeederDrtModeAvailabilityWrapper(config, new TestModeAvailability());
                        return new TransitWithAbstractAccessModeAvailabilityWrapper(config, feederDrtModeAvailabilityWrapper);
                    }
                }).asEagerSingleton();
            }
        });
        controller.run();
    }

    private void runAnalyses() throws CommandLine.ConfigurationException, IOException {
        RunTripAnalysis.main(new String[]{
                "--events-path", "melun_test/output/output_events.xml.gz",
                "--network-path", "melun_test/input/network.xml.gz",
                "--output-path", "melun_test/output/eqasim_trips_post_sim.csv"
        });

        assert CRCChecksum.getCRCFromFile("melun_test/output/eqasim_trips.csv") == CRCChecksum.getCRCFromFile("melun_test/output/eqasim_trips_post_sim.csv");

        RunLegAnalysis.main(new String[]{
                "--events-path", "melun_test/output/output_events.xml.gz",
                "--network-path", "melun_test/input/network.xml.gz",
                "--output-path", "melun_test/output/eqasim_legs_post_sim.csv"

        });

        assert CRCChecksum.getCRCFromFile("melun_test/output/eqasim_legs.csv") == CRCChecksum.getCRCFromFile("melun_test/output/eqasim_legs_post_sim.csv");

        RunPublicTransportLegAnalysis.main(new String[]{
                "--events-path", "melun_test/output/output_events.xml.gz",
                "--schedule-path", "melun_test/input/transit_schedule.xml.gz",
                "--output-path", "melun_test/output/eqasim_pt_post_sim.csv"
        });

        assert CRCChecksum.getCRCFromFile("melun_test/output/eqasim_pt.csv") == CRCChecksum.getCRCFromFile("melun_test/output/eqasim_pt_post_sim.csv");
    }

    private void runExports() throws Exception {
        ExportTransitLinesToShapefile.main(new String[]{
                "--schedule-path", "melun_test/input/transit_schedule.xml.gz",
                "--network-path", "melun_test/input/network.xml.gz",
                "--crs", "EPSG:2154",
                "--output-path", "melun_test/exports/lines.shp"
        });

        ExportTransitLinesToShapefile.main(new String[]{
                "--schedule-path", "melun_test/input/transit_schedule.xml.gz",
                "--network-path", "melun_test/input/network.xml.gz",
                "--crs", "EPSG:2154",
                "--modes", "rail",
                "--output-path", "melun_test/exports/lines_rail.shp"
        });

        ExportTransitLinesToShapefile.main(new String[]{
                "--schedule-path", "melun_test/input/transit_schedule.xml.gz",
                "--network-path", "melun_test/input/network.xml.gz",
                "--crs", "EPSG:2154",
                "--transit-lines", "IDFM:C02364,IDFM:C00879",
                "--output-path", "melun_test/exports/lines_line_ids.shp"
        });

        ExportTransitLinesToShapefile.main(new String[]{
                "--schedule-path", "melun_test/input/transit_schedule.xml.gz",
                "--network-path", "melun_test/input/network.xml.gz",
                "--crs", "EPSG:2154",
                "--transit-routes", "IDFM:TRANSDEV_AMV:27719-C00637-14017001,IDFM:SNCF:42048-C01728-9e8c577f-7ff9-4fe7-93e7-3c3854aa5ecf",
                "--output-path", "melun_test/exports/lines_route_ids.shp"
        });

        ExportTransitStopsToShapefile.main(new String[]{
                "--schedule-path", "melun_test/input/transit_schedule.xml.gz",
                "--crs", "EPSG:2154",
                "--output-path", "melun_test/exports/stops.shp"
        });

        ExportNetworkToShapefile.main(new String[]{
                "--network-path", "melun_test/input/network.xml.gz",
                "--crs", "EPSG:2154",
                "--output-path", "melun_test/exports/network.shp"
        });

        ExportActivitiesToShapefile.main(new String[]{
                "--plans-path", "melun_test/input/population.xml.gz",
                "--output-path", "melun_test/exports/activities.shp",
                "--crs", "EPSG:2154"
        });

        ExportPopulationToCSV.main(new String[]{
                "--plans-path", "melun_test/input/population.xml.gz",
                "--output-path", "melun_test/exports/persons.csv"
        });

        ExportNetworkRoutesToGeopackage.main(new String[]{
                "--plans-path", "melun_test/output/output_plans.xml.gz",
                "--network-path", "melun_test/input/network.xml.gz",
                "--output-path", "melun_test/exports/network_routes.gpkg",
                "--crs", "EPSG:2154"
        });
    }
    
    private void runCutter() throws Exception {
    	RunScenarioCutter.main(new String[] {
    		"--config-path", "melun_test/input/config.xml",
    		"--events-path", "melun_test/output/output_events.xml.gz",
            "--output-path", "melun_test/cutter",
            "--prefix", "center_",
            "--extent-path", "melun_test/input/center.shp"
    	});
    }

    @Test
    public void testDrt() throws IOException, CommandLine.ConfigurationException {
        CreateDrtVehicles.main(new String[]{
                "--network-path", "melun_test/input/network.xml.gz",
                "--output-vehicles-path", "melun_test/input/drt_vehicles_a.xml.gz",
                "--vehicles-number", "50",
                "--vehicle-id-prefix", "vehicle_drt_a_"
        });

        CreateDrtVehicles.main(new String[]{
                "--network-path", "melun_test/input/network.xml.gz",
                "--output-vehicles-path", "melun_test/input/drt_vehicles_b.xml.gz",
                "--vehicles-number", "50",
                "--vehicle-id-prefix", "vehicle_drt_b_"
        });

        AdaptConfigForDrt.main(new String[]{
                "--input-config-path", "melun_test/input/config.xml",
                "--output-config-path", "melun_test/input/config_drt.xml",
                "--mode-names", "drt_a,drt_b",
                "--vehicles-paths", "melun_test/input/drt_vehicles_a.xml.gz,melun_test/input/drt_vehicles_b.xml.gz"
        });

        runMelunSimulation("melun_test/input/config_drt.xml", "melun_test/output_drt");

        RunDrtPassengerAnalysis.main(new String[]{
                "--events-path", "melun_test/output_drt/output_events.xml.gz",
                "--network-path", "melun_test/output_drt/output_network.xml.gz",
                "--modes", "drt_a,drt_b",
                "--output-path", "melun_test/output_drt/eqasim_drt_passenger_rides_standalone.csv"
        });

        RunDrtVehicleAnalysis.main(new String[]{
                "--events-path", "melun_test/output_drt/output_events.xml.gz",
                "--network-path", "melun_test/output_drt/output_network.xml.gz",
                "--movements-output-path", "melun_test/output_drt/eqasim_drt_vehicle_movements_standalone.csv",
                "--activities-output-path", "melun_test/output_drt/eqasim_drt_vehicle_activities_standalone.csv"
        });
    }

    @Test
    public void testFeeder() throws IOException, CommandLine.ConfigurationException {
        CreateDrtVehicles.main(new String[]{
                "--network-path", "melun_test/input/network.xml.gz",
                "--output-vehicles-path", "melun_test/input/feeder_drt_vehicles_a.xml.gz",
                "--vehicles-number", "50",
                "--vehicle-id-prefix", "vehicle_drt_feeder_a_"
        });

        CreateDrtVehicles.main(new String[]{
                "--network-path", "melun_test/input/network.xml.gz",
                "--output-vehicles-path", "melun_test/input/feeder_drt_vehicles_b.xml.gz",
                "--vehicles-number", "50",
                "--vehicle-id-prefix", "vehicle_drt_feeder_b_"
        });

        AdaptConfigForDrt.main(new String[]{
                "--input-config-path", "melun_test/input/config.xml",
                "--output-config-path", "melun_test/input/config_feeder.xml",
                "--mode-names", "drt_for_feeder_a,drt_for_feeder_b",
                "--vehicles-paths", "melun_test/input/feeder_drt_vehicles_a.xml.gz,melun_test/input/feeder_drt_vehicles_b.xml.gz"
        });


        AdaptConfigForFeederDrt.main(new String[]{
                "--input-config-path", "melun_test/input/config_feeder.xml",
                "--output-config-path", "melun_test/input/config_feeder.xml",
                "--mode-names", "feeder_a,feeder_b",
                "--base-drt-modes", "drt_for_feeder_a,drt_for_feeder_b",
                "--access-egress-transit-stop-modes", "rail|tram|subway"
        });

        runMelunSimulation("melun_test/input/config_feeder.xml", "melun_test/output_feeder");

        RunFeederDrtPassengerAnalysis.main(new String[]{
                "--config-path", "melun_test/input/config_feeder.xml",
                "--events-path", "melun_test/output_feeder/output_events.xml.gz",
                "--network-path", "melun_test/output_feeder/output_network.xml.gz",
                "--output-path", "melun_test/output_feeder/eqasim_feeder_drt_trips_standalone.csv"
        });
    }

    @Test
    public void testEpsilon() throws CommandLine.ConfigurationException {
        AdaptConfigForEpsilon.main(new String[]{
                "--input-config-path", "melun_test/input/config.xml",
                "--output-config-path", "melun_test/input/config_epsilon.xml"
        });

        runMelunSimulation("melun_test/input/config_epsilon.xml", "melun_test/output_epsilon");
    }

    @Test
    public void testTransitWithAbstractAccess() throws CommandLine.ConfigurationException {
        CreateAbstractAccessItemsForTransitLines.main(new String[]{
                "--transit-schedule-path", "melun_test/input/transit_schedule.xml.gz",
                "--output-path", "melun_test/input/access_items.xml",
                "--radius", "1000",
                "--average-speed", "18",
                "--route-modes", "rail",
                "--use-routed-distance", "true",
                "--access-type", "bus",
                "--frequency", "60"
        });

        AdaptConfigForTransitWithAbstractAccess.main(new String[]{
                "--input-config-path", "melun_test/input/config.xml",
                "--output-config-path", "melun_test/input/config_abstract_access.xml",
                "--mode-name", "ptWithAbstractAccess",
                "--accesses-file-path", "melun_test/input/access_items.xml"
        });


        runMelunSimulation("melun_test/input/config_abstract_access.xml", "melun_test/output_abstract_access");
    }

    @Test
    public void testPipeline() throws Exception {
        runMelunSimulation("melun_test/input/config.xml", "melun_test/output");
        runStandaloneModeChoice();
        runAnalyses();
        runExports();
        runCutter();
    }


    public void runStandaloneModeChoice() throws CommandLine.ConfigurationException, IOException, InterruptedException {
        RunStandaloneModeChoice.main(new String[] {
                "--config-path", "melun_test/input/config.xml",
                "--recorded-travel-times-path", "melun_test/output/eqasim_travel_times.bin",
                "--write-input-csv-trips", "true",
                "--write-output-csv-trips", "true",
                "--config:standaloneModeChoice.outputDirectory", "melun_test/output_mode_choice",
                "--mode-choice-configurator-class", TestModeChoiceConfigurator.class.getName(),
                "--simulate-after", TestRunSimulation.class.getName()
        });
    }

    public static class TestRunSimulation {
        public static void main(String[] args) throws CommandLine.ConfigurationException {
            CommandLine commandLine = new CommandLine.Builder(args)
                    .requireOptions("config-path")
                    .build();

            String configPath = commandLine.getOptionStrict("config-path");
            String outputDirectory = commandLine.getOption("config:controler.outputDirectory").orElse("generic_output");
            Optional<String> lastIterationOption = commandLine.getOption("config:controler.lastIteration");
            Integer lastIteration = lastIterationOption.isPresent() ? Integer.parseInt(lastIterationOption.get()) : null;
            String inputPlansFile = commandLine.getOption("config:plans.inputPlansFile").orElse(null);
            new TestSimulationPipeline().runMelunSimulation(configPath, outputDirectory, inputPlansFile, lastIteration);
        }
    }

    private static class TestModeAvailability implements ModeAvailability {
        @Override
        public Collection<String> getAvailableModes(Person person, List<DiscreteModeChoiceTrip> trips) {
            Set<String> modes = new HashSet<>();
            modes.add(TransportMode.walk);
            modes.add(TransportMode.pt);
            modes.add(TransportMode.car);
            modes.add(TransportMode.bike);
            // Add special mode "car_passenger" if applicable
            Boolean isCarPassenger = (Boolean) person.getAttributes().getAttribute("isPassenger");
            if (isCarPassenger) {
                modes.add("car_passenger");
            }
            return modes;
        }
    }

    public static class TestModeChoiceConfigurator extends StandaloneModeChoiceConfigurator {

        public TestModeChoiceConfigurator(Config config, CommandLine commandLine) {
            super(config, commandLine);
        }

        public List<AbstractModule> getSpecificModeChoiceModules() {
            return List.of(new AbstractEqasimExtension() {
                @Override
                public void installEqasimExtension() {
                    bind(ModeParameters.class);
                    bindModeAvailability("DefaultModeAvailability").to(TestModeAvailability.class);
                }
            });
        }
    }
}
