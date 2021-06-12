package client;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class SimultaneousJobsStats {
    private final AtomicInteger jobsToStart;
    private final AtomicBoolean oneJobFinished;
    private final List<SingleSimultaneousJobStats> registeredJobs;

    public SimultaneousJobsStats(int jobsToStart) {
        this.jobsToStart = new AtomicInteger(jobsToStart);
        this.oneJobFinished = new AtomicBoolean(false);
        this.registeredJobs = new ArrayList<>();
    }

    public SingleSimultaneousJobStats registerStats() {
        var newJobStat = new SingleSimultaneousJobStats();
        registeredJobs.add(newJobStat);
        return newJobStat;
    }

    public long getAllJobsAverageStat() {
        long runsSum = 0;
        long runsCount = 0;
        for (SingleSimultaneousJobStats job : registeredJobs) {
            runsSum += job.nanosTotal.get();
            runsCount += job.runsTotal.get();
        }
        return runsSum / runsCount;
    }

    public int getJobsToStart() {
        return jobsToStart.get();
    }

    public int decrementJobsToStart() {
        return jobsToStart.decrementAndGet();
    }

    public boolean isOneJobFinished() {
        return oneJobFinished.get();
    }

    public void setOneJobFinished() {
        oneJobFinished.set(true);
    }

    public class SingleSimultaneousJobStats {
        private final AtomicLong nanosTotal;
        private final AtomicInteger runsTotal;

        public SingleSimultaneousJobStats() {
            this.nanosTotal = new AtomicLong(0);
            runsTotal = new AtomicInteger(0);
        }

        public void pushStat(long executionTime) {
            if (getJobsToStart() > 0 || isOneJobFinished()) {
                return;
            }
            nanosTotal.addAndGet(executionTime);
            runsTotal.incrementAndGet();
        }

        public void start() {
            if (decrementJobsToStart() < 0) {
                throw new IllegalStateException("More jobs started than registered");
            }
        }

        public void finish() {
            setOneJobFinished();
        }
    }
}
