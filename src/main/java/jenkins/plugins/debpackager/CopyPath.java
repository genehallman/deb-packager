package jenkins.plugins.debpackager;

import hudson.Extension;
import hudson.FilePath;
import hudson.model.AbstractDescribableImpl;
import hudson.model.BuildListener;
import hudson.model.Descriptor;

import java.io.IOException;

import org.kohsuke.stapler.DataBoundConstructor;

public class CopyPath extends AbstractDescribableImpl<CopyPath> {

    private String workingDir;
    private String include;
    private String exclude;
    private String to;

    public String getInclude() {
        return include;
    }

    public String getExclude() {
        return exclude;
    }

    public String getTo() {
        return to;
    }

    public String getWorkingDir() {
        return workingDir;
    }

    @DataBoundConstructor
    public CopyPath(String workingDir, String include, String exclude, String to) {
        this.workingDir = workingDir;
        this.include = include;
        this.exclude = exclude;
        this.to = to;
    }

    public String toString() {
        return String.format("{workingDir: %s, include: %s, exclude: %s, to: %s}", this.workingDir,
                this.include, this.exclude, this.to);
    }

    public void copy(FilePath workspace, FilePath packagePath, BuildListener listener)
            throws IOException, InterruptedException {
        FilePath moveToPath = packagePath.child(to);
        moveToPath.mkdirs();

        FilePath workingPath = workspace;

        if (!workingDir.isEmpty()) {
            workingPath = workspace.child(workingDir);
            workingPath.mkdirs();
        }
        FilePathUtils.copyRecursiveWithPermissions(workingPath, include, exclude + ", .packaged/",
                moveToPath, listener);
        // workingPath.copyRecursiveTo(include, exclude +
        // ", .packaged/",moveToPath);

    }

    @Extension
    public static class DescriptorImpl extends Descriptor<CopyPath> {
        public String getDisplayName() {
            return "Copy Path";
        }
    }
}