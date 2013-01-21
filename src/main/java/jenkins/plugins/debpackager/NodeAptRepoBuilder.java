package jenkins.plugins.debpackager;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.tasks.Shell;

import java.io.IOException;

import org.kohsuke.stapler.DataBoundConstructor;

public class NodeAptRepoBuilder extends Builder {
    String command = "\n"
            + "echo DEB_PKG_NAME=$DEB_PKG_NAME \n"
            + "echo DEBIAN_REPO_BUCKET=$DEBIAN_REPO_BUCKET \n"
            + "echo DEBIAN_REPO_DISTRIBUTION=$DEBIAN_REPO_DISTRIBUTION \n"
            + "echo DEB_REPO_SERVER_AND_PORT=$DEB_REPO_SERVER_AND_PORT \n"
            + "echo PKG_CHAR=$PKG_CHAR \n"
            + "echo PKG_NAME=$PKG_NAME \n"
            + "cp .packaged.deb $DEB_PKG_NAME.deb \n"
            + "s3cmd put $DEB_PKG_NAME.deb s3://$DEBIAN_REPO_BUCKET/ \n"
            + "rm $DEB_PKG_NAME.deb \n"
            + "curl http://$DEB_REPO_SERVER_AND_PORT/ubuntu/dists/$DEBIAN_REPO_DISTRIBUTION/main/add/$DEB_PKG_NAME.deb";

    @DataBoundConstructor
    public NodeAptRepoBuilder() {
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
            throws InterruptedException, IOException {
        String fullName = build.getEnvironment(listener).get("DEB_PKG_NAME");
        String pkgName = fullName.split("_")[0];
        String pkgChar = String.valueOf(pkgName.charAt(0));

        String com = command.replaceAll("\\$PKG_CHAR", pkgChar).replaceAll("\\$PKG_NAME", pkgName);

        return new Shell(com).perform(build, launcher, listener);
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
            return "Deb Packager - Node Apt Repo Builder";
        }
    }
}
