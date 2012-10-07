package jenkins.plugins.debpackager;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;

import org.kohsuke.stapler.DataBoundConstructor;

public class RepreproBuilder extends Builder {

    @DataBoundConstructor
    public RepreproBuilder() {
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) {
        int retval = -1;
        listener.getLogger().println("Deb Packager - adding package to reprepro...");
        try {
            retval = launcher
                    .launch()
                    .cmds(new String[] { "reprepro", "--keepunreferencedfiles", "-Vb",
                            "/var/lib/reprepro", "includedeb", "livefyre", ".packaged.deb" })
                    .envs(build.getEnvironment(listener)).stdout(listener)
                    .pwd(build.getWorkspace()).join();
        } catch (Exception e) {
            e.printStackTrace(listener.getLogger());
        }

        listener.getLogger().println("Deb Packager - finished reprepro");
        return retval == 0;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
        @SuppressWarnings("rawtypes")
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        public String getDisplayName() {
            return "Deb Packager - Reprepro";
        }
    }
}
