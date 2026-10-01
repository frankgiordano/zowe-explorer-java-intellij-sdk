package org.zowe.explorerjava;

import zowe.client.sdk.core.ZosConnection;
import zowe.client.sdk.rest.exception.ZosmfRequestException;
import zowe.client.sdk.zosjobs.methods.JobGet;
import zowe.client.sdk.zosjobs.methods.JobMonitor;
import zowe.client.sdk.zosjobs.methods.JobSubmit;
import zowe.client.sdk.zosjobs.model.Job;
import zowe.client.sdk.zosjobs.model.JobFile;
import zowe.client.sdk.zosjobs.types.JobStatus;

import java.util.List;

/**
 * Explorer-facing jobs facade.
 * <p>
 * UI code intentionally does not call the SDK directly.
 */
public final class JobService {

    private final JobGet get;
    private final JobSubmit submit;
    private final JobMonitor monitor;

    public JobService(final ZosConnection connection) {
        this.get = new JobGet(connection);
        this.submit = new JobSubmit(connection);
        this.monitor = new JobMonitor(connection);
    }

    public List<Job> listMine() throws ZosmfRequestException {
        return get.getAll();
    }

    public List<Job> list(
            final String owner,
            final String prefix)
            throws ZosmfRequestException {

        if (!owner.isBlank() && !prefix.isBlank()) {
            return get.getByOwnerAndPrefix(owner, prefix);
        }

        if (!owner.isBlank()) {
            return get.getByOwner(owner);
        }

        if (!prefix.isBlank()) {
            return get.getByPrefix(prefix);
        }

        return get.getAll();
    }

    public Job submitDataSet(
            final String dataSet)
            throws ZosmfRequestException {

        return submit.submit(dataSet);
    }

    /**
     * Performs a single job-status request.
     * <p>
     * This is used by the IntelliJ explorer's cancellable background
     * monitoring instead of keeping a JobMonitor polling loop alive.
     */
    public Job getStatus(
            final String jobName,
            final String jobId)
            throws ZosmfRequestException {

        return get.getStatus(jobName, jobId);
    }

    /**
     * Performs a single job-status request.
     */
    public Job getStatus(
            final Job job)
            throws ZosmfRequestException {

        return get.getStatusByJob(job);
    }

    /*
     * Keep the JobMonitor APIs available to demonstrate/support
     * the underlying Java SDK functionality. The Explorer UI no
     * longer uses waitForOutput() for continuous monitoring.
     */

    public Job waitForOutput(
            final Job job)
            throws ZosmfRequestException {

        return monitor.waitByOutputStatus(job);
    }

    public Job waitForOutput(
            final String jobName,
            final String jobId)
            throws ZosmfRequestException {

        return monitor.waitByOutputStatus(
                jobName,
                jobId);
    }

    public Job waitForStatus(
            final Job job,
            final JobStatus.Type status)
            throws ZosmfRequestException {

        return monitor.waitByStatus(
                job,
                status);
    }

    public boolean waitForMessage(
            final Job job,
            final String message)
            throws ZosmfRequestException {

        return monitor.waitByMessage(
                job,
                message);
    }

    public boolean isRunning(
            final Job job)
            throws ZosmfRequestException {

        return monitor.isRunning(
                new zowe.client.sdk.zosjobs.input.JobMonitorInputData.Builder(
                        job.getJobName(),
                        job.getJobId())
                        .build());
    }

    public String getJcl(
            final Job job)
            throws ZosmfRequestException {

        return get.getJclByJob(job);
    }

    public List<JobFile> getSpoolFiles(
            final Job job)
            throws ZosmfRequestException {

        return get.getSpoolFilesByJob(job);
    }

    public String getSpool(
            final JobFile file)
            throws ZosmfRequestException {

        return get.getSpoolContent(file);
    }
}
