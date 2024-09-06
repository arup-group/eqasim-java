package org.eqasim.core.scenario.routing;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eqasim.core.misc.ParallelProgress;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.Population;

import com.google.inject.Provider;

public class PopulationRouter {
	private final int numberOfThreads;
	private final int batchSize;
	private final boolean replaceExistingRoutes;
	private final Provider<PlanRouter> routerProvider;
	private final Set<String> modes;

	public PopulationRouter(int numberOfThreads, int batchSize, boolean replaceExistingRoutes, Set<String> modes,
			Provider<PlanRouter> routerProvider) {
		this.numberOfThreads = numberOfThreads;
		this.batchSize = batchSize;
		this.routerProvider = routerProvider;
		this.replaceExistingRoutes = replaceExistingRoutes;
		this.modes = modes;
	}

	public void run(Population population) throws InterruptedException {
		List<Thread> threads = new LinkedList<>();

		Iterator<? extends Person> personIterator = population.getPersons().values().iterator();
		ParallelProgress progress = new ParallelProgress("Routing population ...", population.getPersons().size());

		final AtomicBoolean errorOccured = new AtomicBoolean(false);

		for (int i = 0; i < numberOfThreads; i++) {
			Thread thread = new Thread(new Worker(personIterator, progress));

			thread.setUncaughtExceptionHandler((t, e) -> {
				e.printStackTrace();
				errorOccured.set(true);
			});

			threads.add(thread);
		}

		threads.forEach(Thread::start);
		progress.start();

		for (Thread thread : threads) {
			thread.join();
		}

		progress.close();

		if (errorOccured.get()) {
			throw new RuntimeException("Found errors in routing threads");
		}
	}

	private class Worker implements Runnable {
		private final Iterator<? extends Person> personIterator;
		private final ParallelProgress progress;

		Worker(Iterator<? extends Person> personIterator, ParallelProgress progress) {
			this.personIterator = personIterator;
			this.progress = progress;
		}

        @Override
		public void run() {
			List<Person> localTasks = new LinkedList<>();
			PlanRouter router = routerProvider.get();
			Map<Plan, RuntimeException> badPlans = new HashMap<>();

			do {
				localTasks.clear();

				synchronized (personIterator) {
					while (personIterator.hasNext() && localTasks.size() < batchSize) {
						localTasks.add(personIterator.next());
					}
				}

				RuntimeException lastException = null;
				int goodPlanCount = 0;
				for (Person person : localTasks) {
					for (Plan plan : person.getPlans()) {
						try {
							router.run(plan, replaceExistingRoutes, modes);
							System.out.println(String.format("Plan '%s' is good...", plan));
							goodPlanCount++;
						} catch (RuntimeException e) {
							badPlans.put(plan, e);
							System.err.println(String.format("Error routing plan for agent %s", person.getId()));
							System.err.println(String.format("The problematic plan is %s", plan.getId()));
							System.err.println(String.format("The problematic plan is %s", plan));
							lastException = e;
						}
					}
				}
				if (lastException != null) {
					System.err.println(String.format(
							"!!! %s plans had errors (there were %s good plans)",
							badPlans.size(),
							goodPlanCount)
					);
					System.err.println(badPlans);
					throw lastException;
				}

				progress.update(localTasks.size());
			} while (localTasks.size() > 0);
		}
	}
}
