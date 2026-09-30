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
 * Explorer-facing jobs facade. UI code intentionally does not call the SDK directly.
 * Job monitoring delegates to the Java SDK's JobMonitor implementation.
 */
public final class JobService {
    private final JobGet get;
    private final JobSubmit submit;
    private final JobMonitor monitor;

    public JobService(ZosConnection connection) {
        this.get = new JobGet(connection);
        this.submit = new JobSubmit(connection);
        this.monitor = new JobMonitor(connection);
    }

    public List<Job> listMine() throws ZosmfRequestException {
        return get.getAll();
    }

    public List<Job> list(String owner, String prefix) throws ZosmfRequestException {
        if (!owner.isBlank() && !prefix.isBlank()) return get.getByOwnerAndPrefix(owner, prefix);
        if (!owner.isBlank()) return get.getByOwner(owner);
        if (!prefix.isBlank()) return get.getByPrefix(prefix);
        return get.getAll();
    }

    public Job submitDataSet(String dataSet) throws ZosmfRequestException {
        return submit.submit(dataSet);
    }

    public Job waitForOutput(Job job) throws ZosmfRequestException {
        return monitor.waitByOutputStatus(job);
    }

    public Job waitForOutput(String jobName, String jobId) throws ZosmfRequestException {
        return monitor.waitByOutputStatus(jobName, jobId);
    }

    public Job waitForStatus(Job job, JobStatus.Type status) throws ZosmfRequestException {
        return monitor.waitByStatus(job, status);
    }

    public boolean waitForMessage(Job job, String message) throws ZosmfRequestException {
        return monitor.waitByMessage(job, message);
    }

    public boolean isRunning(Job job) throws ZosmfRequestException {
        return monitor.isRunning(new zowe.client.sdk.zosjobs.input.JobMonitorInputData.Builder(
                job.getJobName(), job.getJobId()).build());
    }

    public String getJcl(Job job) throws ZosmfRequestException {
        return get.getJclByJob(job);
    }

    public List<JobFile> getSpoolFiles(Job job) throws ZosmfRequestException {
        return get.getSpoolFilesByJob(job);
    }

    public String getSpool(JobFile file) throws ZosmfRequestException {
        return get.getSpoolContent(file);
    }
}
