package jenkins.plugins.debpackager;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;

import org.kohsuke.stapler.DataBoundConstructor;

public class CopyPath extends AbstractDescribableImpl<CopyPath> {

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

    @DataBoundConstructor
    public CopyPath(String include, String exclude, String to) {
        this.include = include;
        this.exclude = exclude;
        this.to = to;
    }

    public String toString() {
        return String.format("{include: %s, exclude: %s, to: %s}", this.include, this.exclude,
                this.to);
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<CopyPath> {
        public String getDisplayName() {
            return "";
        }
    }
}