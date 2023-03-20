package org.eqasim.ile_de_france.drt;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
// import the dependecies for eqasim
import org.eqasim.ile_de_france.IDFConfigurator;
import org.eqasim.ile_de_france.mode_choice.IDFModeChoiceModule;
import org.matsim.api.core.v01.Scenario;
// import the dependecies for drt
import org.matsim.contrib.drt.run.DrtControlerCreator;
import org.matsim.contrib.drt.run.MultiModeDrtConfigGroup;
import org.matsim.contrib.drt.run.MultiModeDrtModule;
import org.matsim.contrib.dvrp.run.DvrpConfigGroup;
import org.matsim.contrib.dvrp.run.DvrpModule;
import org.matsim.vis.otfvis.OTFVisConfigGroup;

import org.matsim.core.config.CommandLine;
import org.matsim.core.config.CommandLine.ConfigurationException;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.ConfigGroup;
import org.matsim.core.controler.Controler;
import org.matsim.core.scenario.ScenarioUtils;


public class RunMatsim_combine {
	private final static Logger logger = LogManager.getLogger(RunMatsim_combine.class);

	public static void main(String[] args) throws ConfigurationException {
		// load config

		// configure eqasim
		CommandLine cmd = new CommandLine.Builder(args) //
				.requireOptions("config-path") //
				.allowPrefixes("mode-choice-parameter", "cost-parameter") //
				.build();
				
		IDFConfigurator configurator = new IDFConfigurator();
		Config config = ConfigUtils.loadConfig(cmd.getOptionStrict("config-path"), configurator.getConfigGroups());
		

		cmd.applyConfiguration(config);

		Scenario scenario = ScenarioUtils.createScenario(config);
		configurator.configureScenario(scenario);
		ScenarioUtils.loadScenario(scenario);
		configurator.adjustScenario(scenario);

		// create MATSim controler
		Controler controler = DrtControlerCreator.createControler(config, false);
		// Controler controller = new Controler(scenario);
		// configurator.configureController(controller);
		// controller.addOverridingModule(new EqasimAnalysisModule());
		// controller.addOverridingModule(new EqasimModeChoiceModule());
		// controller.addOverridingModule(new IDFModeChoiceModule(cmd));
		controler.addOverridingModule(new MultiModeDrtModule());
		controler.addOverridingModule(new DvrpModule());

		// start simulation
		controler.run();
	}
}
