package app.ageguessr.features.people.IngestPeople;

import java.time.Duration;

/** Waiting, behind an interface so tests can check the waits without actually sleeping. */
@FunctionalInterface
interface Sleeper {

    void sleep(Duration duration) throws InterruptedException;

    Sleeper REAL = duration -> Thread.sleep(duration);
}
