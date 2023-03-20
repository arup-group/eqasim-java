package org.eqasim.ile_de_france.drt;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Scenario;
import org.matsim.contrib.drt.run.DrtControlerCreator;
import org.matsim.contrib.drt.run.MultiModeDrtConfigGroup;
import org.matsim.contrib.dvrp.run.DvrpConfigGroup;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.Controler;


public class RunMatsimDRT {
    private final static Logger logger = LogManager.getLogger(RunMatsimDRT.class);

    public static void main(String[] args) {
        
        //load config
        Config config = ConfigUtils.loadConfig(args[0], new MultiModeDrtConfigGroup(),
                        new DvrpConfigGroup());
		run(config, false);

    }

	public static void run(Config config, boolean otfvis) {
		//Creates a MATSim Controler and preloads all DRT related packages
		Controler controler = DrtControlerCreator.createControler(config, otfvis);

		//starts the simulation
		controler.run();
	}
    
}