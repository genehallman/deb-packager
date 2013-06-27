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

public class ManualRepoBuilder extends Builder {
    String command = "\n"
            + "echo DEB_PKG_NAME=$DEB_PKG_NAME \n"
            + "echo DEBIAN_REPO_BASE=$DEBIAN_REPO_BASE \n"
            + "echo DEBIAN_REPO_DISTRIBUTION=$DEBIAN_REPO_DISTRIBUTION \n"
            + "echo PKG_CHAR=$PKG_CHAR \n"
            + "echo PKG_NAME=$PKG_NAME \n"
            + "/usr/bin/lockfile-create -v --retry 10 /var/run/jenkins/debpackager\n"
            + "if [ $? -ne 0 ]; then echo 'Cannot aquire lock!'; exit; fi\n"
            + "lockfile-touch /var/run/jenkins/debpackager &\n"
            + "LOCK_UPDATER=\"$!\"\n"
            + "sudo mkdir -p $DEBIAN_REPO_BASE/pool/main/$PKG_CHAR/$PKG_NAME \n"
            + "sudo cp .packaged.deb $DEBIAN_REPO_BASE/pool/main/$PKG_CHAR/$PKG_NAME/$DEB_PKG_NAME.deb \n"
            + "cd $DEBIAN_REPO_BASE \n"
            + "sudo pip install https://github.com/genehallman/pydpkg-lite/tarball/master#egg=pydpkg-lite \n"
            + "sudo sh -c \"dpkg.py $DEBIAN_REPO_BASE dists/$DEBIAN_REPO_DISTRIBUTION/main/binary-all/Packages > dists/$DEBIAN_REPO_DISTRIBUTION/main/binary-all/Packages_new\" \n"
            + "sudo mv dists/$DEBIAN_REPO_DISTRIBUTION/main/binary-all/Packages_new dists/$DEBIAN_REPO_DISTRIBUTION/main/binary-all/Packages \n"
            + "sudo sh -c \"cat dists/$DEBIAN_REPO_DISTRIBUTION/main/binary-all/Packages | gzip -9 > dists/$DEBIAN_REPO_DISTRIBUTION/main/binary-all/Packages.gz\" \n"
            + "kill \"${LOCK_UPDATER}\"\n"
            + "/usr/bin/lockfile-remove -v /var/run/jenkins/debpackager\n"
            + "cd - \n";

    @DataBoundConstructor
    public ManualRepoBuilder() {
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
            return "Deb Packager - Manual Repo Builder";
        }
    }
}
