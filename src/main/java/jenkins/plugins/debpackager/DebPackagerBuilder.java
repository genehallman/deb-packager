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
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;

import java.io.IOException;

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
    private final String moveWorkspacePath;
    private final String dependencies;
    private final String maintainer;
    private final String preinst;
    private final String postinst;
    private final String prerm;
    private final String postrm;

    @DataBoundConstructor
    public DebPackagerBuilder(String packageName, String versionFormat, String moveWorkspacePath,
            String dependencies, String maintainer, String preinst, String postinst, String prerm,
            String postrm) {
        this.packageName = packageName;
        this.versionFormat = versionFormat;
        this.moveWorkspacePath = moveWorkspacePath;
        this.dependencies = dependencies;
        this.maintainer = maintainer;
        this.preinst = preinst;
        this.postinst = postinst;
        this.prerm = prerm;
        this.postrm = postrm;
    }

    /**
     * We'll use this from the <tt>config.jelly</tt>.
     */
    public String getPackageName() {
        return packageName;
    }

    public String getVersionFormat() {
        return versionFormat;
    }

    public String getMoveWorkspacePath() {
        return moveWorkspacePath;
    }

    public String getDependencies() {
        return dependencies;
    }

    public String getMaintainer() {
        return maintainer;
    }

    public String getPreinst() {
        return preinst;
    }

    public String getPostinst() {
        return postinst;
    }

    public String getPrerm() {
        return prerm;
    }

    public String getPostrm() {
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
        try {
            FilePath workspace = build.getWorkspace();
            String packageNameSub = getParameterString(packageName, build, listener);
            String version = getParameterString(versionFormat, build, listener);
            String dependenciesSub = getParameterString(dependencies, build, listener);
            String debPkgName = packageNameSub + "_" + version;

            // 1a. create folder to house package ("workspace/.packaged")
            FilePath packagePath = workspace.child(".packaged");
            if (packagePath.exists()) {
                packagePath.deleteRecursive();
            }
            packagePath.mkdirs();

            // 1b. create the "moveToPath" directory(s)
            FilePath moveToPath = moveWorkspacePath == null ? packagePath : packagePath
                    .child(moveWorkspacePath);
            moveToPath.mkdirs();

            // 1c. copy the workspace to the future install directory
            workspace.copyRecursiveTo("**/*", packagePath.getName(), moveToPath);

            // 2. make the debian directory
            FilePath debianPath = packagePath.child("DEBIAN");
            debianPath.mkdirs();

            // 3. make control file
            FilePath controlFile = debianPath.child("control");
            controlFile.write(
                    makeControlFile(debianPath, packageNameSub, version, dependenciesSub,
                            maintainer, build.getEnvironment(listener)), "UTF-8");

            // 4. save postinst, preinst, postrm, prerm to files
            FilePath preinstFile = debianPath.child("preinst");
            FilePath postinstFile = debianPath.child("postinst");
            FilePath prermFile = debianPath.child("prerm");
            FilePath postrmFile = debianPath.child("postrm");
            preinstFile.write(preinst, "UTF-8");
            postinstFile.write(postinst, "UTF-8");
            prermFile.write(prerm, "UTF-8");
            postrmFile.write(postrm, "UTF-8");

            preinstFile.chmod(Integer.parseInt("755", 8));
            postinstFile.chmod(Integer.parseInt("755", 8));
            prermFile.chmod(Integer.parseInt("755", 8));
            postrmFile.chmod(Integer.parseInt("755", 8));

            // 5. chmod all directories to 755

            // 6. set DEB_PKG_NAME env var
            build.getEnvironment(listener).put("DEB_PKG_NAME", debPkgName);

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
        sb.append("Version: " + version.replace("_", "-") + "\n");
        sb.append("Section: devel\n");
        sb.append("Priority: optional\n");
        sb.append("Architecture: any\n");
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
            return "Deb Packager";
        }
    }
}
