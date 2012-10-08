package jenkins.plugins.debpackager;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.ParametersAction;
import hudson.model.StringParameterValue;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;

import java.io.IOException;
import java.util.List;

import javax.servlet.ServletException;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Sample {@link Builder}.
 * 
 * <p>
 * When the user configures the project and enables this builder,
 * {@link DescriptorImpl#newInstance(StaplerRequest)} is invoked and a new
 * {@link DebPackagerBuilder} is created. The created instance is persisted to
 * the project configuration XML by using XStream, so this allows you to use
 * instance fields (like {@link #name}) to remember the configuration.
 * 
 * <p>
 * When a build is performed, the
 * {@link #perform(AbstractBuild, Launcher, BuildListener)} method will be
 * invoked.
 * 
 * @author Kohsuke Kawaguchi
 */
public class DebPackagerBuilder extends Builder {

    private final String packageName;
    private final String versionFormat;
    private final List<CopyPath> copyToPaths;
    private final String dependencies;
    private final String maintainer;
    private final DebScript preinst;
    private final DebScript postinst;
    private final DebScript prerm;
    private final DebScript postrm;

    @DataBoundConstructor
    public DebPackagerBuilder(String packageName, String versionFormat, List<CopyPath> copyToPaths,
            String dependencies, String maintainer, DebScript preinst, DebScript postinst,
            DebScript prerm, DebScript postrm) {
        this.packageName = packageName;
        this.versionFormat = versionFormat;
        this.copyToPaths = copyToPaths;
        this.dependencies = dependencies;
        this.maintainer = maintainer;
        this.preinst = preinst;
        this.postinst = postinst;
        this.prerm = prerm;
        this.postrm = postrm;
    }

    public String getPackageName() {
        return packageName;
    }

    public String getVersionFormat() {
        return versionFormat;
    }

    public List<CopyPath> getCopyToPaths() {
        return copyToPaths;
    }

    public String getDependencies() {
        return dependencies;
    }

    public String getMaintainer() {
        return maintainer;
    }

    public DebScript getPreinst() {
        return preinst;
    }

    public DebScript getPostinst() {
        return postinst;
    }

    public DebScript getPrerm() {
        return prerm;
    }

    public DebScript getPostrm() {
        return postrm;
    }

    private String getParameterString(String original, AbstractBuild<?, ?> build,
            BuildListener listener) {
        ParametersAction parameters = build.getAction(ParametersAction.class);
        if (parameters != null) {
            original = parameters.substitute(build, original);
        }

        try {
            original = Util.replaceMacro(original, build.getEnvironment(listener));
        } catch (Exception e) {
        }

        return original;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) {
        listener.getLogger().println("Deb Packager - starting to structure package...");
        listener.getLogger().println(this);
        try {
            FilePath workspace = build.getWorkspace();
            String packageNameSub = getParameterString(packageName, build, listener);
            String version = getParameterString(versionFormat, build, listener).replace("_", "-");
            String dependenciesSub = getParameterString(dependencies, build, listener);
            String debPkgName = packageNameSub + "_" + version;

            // 1a. create folder to house package ("workspace/.packaged")
            FilePath packagePath = workspace.child(".packaged");
            if (packagePath.exists()) {
                FilePathUtils.sudoDeleteRecursive(packagePath);
            }
            packagePath.mkdirs();

            // 1b. remove old .packaged.deb file if we have one
            FilePath oldPackageFile = workspace.child(".packaged.deb");
            if (oldPackageFile.exists()) {
                oldPackageFile.delete();
            }

            // 1c. create the "moveToPath" directory(s)
            if (copyToPaths != null) {
                for (CopyPath cpPath : copyToPaths) {
                    listener.getLogger().println(cpPath.toString());
                    cpPath.copy(workspace, packagePath, listener);
                }
            }

            // 2. make the debian directory
            FilePath debianPath = packagePath.child("DEBIAN");
            debianPath.mkdirs();

            // 3. make control file
            FilePath controlFile = debianPath.child("control");
            controlFile.write(
                    makeControlFile(debianPath, packageNameSub, version, dependenciesSub,
                            maintainer, build.getEnvironment(listener)), "UTF-8");

            // 4. save postinst, preinst, postrm, prerm to files
            if (preinst != null) {
                preinst.create("preinst", debianPath, workspace);
            }
            if (postinst != null) {
                postinst.create("postinst", debianPath, workspace);
            }
            if (prerm != null) {
                prerm.create("prerm", debianPath, workspace);
            }
            if (postrm != null) {
                postrm.create("postrm", debianPath, workspace);
            }

            // 5 chown packagePath
            FilePathUtils.chown(packagePath, "root", "root");

            // 6. set DEB_PKG_NAME env var
            // build.getEnvironment(listener).put("DEB_PKG_NAME", debPkgName);
            build.addAction(new ParametersAction(new StringParameterValue("DEB_PKG_NAME",
                    debPkgName)));

            // new Shell("export DEB_PKG_NAME=" + debPkgName).perform(build,
            // launcher, listener);

            listener.getLogger().println("Deb Packager - finished");
        } catch (Exception e) {
            e.printStackTrace(listener.getLogger());
            return false;
        }

        return true;
    }

    private String makeControlFile(FilePath debianDir, String packageNameSub, String version,
            String dependenciesSub, String maintainer, EnvVars env) {
        StringBuilder sb = new StringBuilder();
        sb.append("Package:" + packageNameSub + "\n");
        sb.append("Version: " + version + "\n");
        sb.append("Section: devel\n");
        sb.append("Priority: optional\n");
        sb.append("Architecture: all\n");
        if (dependenciesSub != null && !dependenciesSub.isEmpty()) {
            sb.append("Depends: " + dependenciesSub + "\n");
        }
        sb.append("Maintainer: " + maintainer + "\n");
        sb.append("Description: " + env.get("JOB_NAME") + " (built by jenkins)\n");
        for (String key : env.keySet()) {
            sb.append(" " + key + " - " + env.get(key) + "\n");
        }
        return sb.toString();
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    /**
     * Descriptor for {@link DebPackageBuilder}. Used as a singleton. The class
     * is marked as public so that it can be accessed from views.
     */
    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
        public FormValidation doCheckPackageName(@QueryParameter String value) throws IOException,
                ServletException {
            if (value.length() == 0)
                return FormValidation.error("Please set a name");
            return FormValidation.ok();
        }

        public FormValidation doCheckVersionFormat(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error("Please set a version format");
            return FormValidation.ok();
        }

        @SuppressWarnings("rawtypes")
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        public String getDisplayName() {
            return "Deb Packager - Structurer";
        }
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("packageName = " + packageName + "\n");
        sb.append("versionFormat = " + versionFormat + "\n");
        sb.append("copyToPaths = " + copyToPaths + "\n");
        sb.append("dependencies = " + dependencies + "\n");
        sb.append("maintainer = " + maintainer + "\n");
        sb.append("preinst = " + preinst + "\n");
        sb.append("postinst = " + postinst + "\n");
        sb.append("prerm = " + prerm + "\n");
        sb.append("postrm = " + postrm + "\n");
        return sb.toString();
    }
}
