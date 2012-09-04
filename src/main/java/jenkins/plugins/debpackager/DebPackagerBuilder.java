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
 * {@link DebPackageBuilder} is created. The created instance is persisted to
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
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) {
        try {
            FilePath workspace = build.getWorkspace();
            String packageNameSub = getParameterString(packageName, build, listener);
            String version = getParameterString(versionFormat, build, listener);
            String dependenciesSub = getParameterString(dependencies, build, listener);
            String debPkgName = packageNameSub + "_" + version;

            // (1st) move the workspace to the future install directory
            if (moveWorkspacePath != null && !moveWorkspacePath.isEmpty()) {
                List<FilePath> children = workspace.list();
                FilePath tempPath = workspace.child("../tmp"
                        + String.valueOf(Math.abs(Math.random() * 100)) + "/");
                tempPath.mkdirs();
                listener.getLogger().println(workspace.toString());

                workspace.moveAllChildrenTo(tempPath);
                workspace.mkdirs();
                listener.getLogger().println(tempPath.toString());
                listener.getLogger().println(workspace.toString());

                FilePath pkgPath = workspace.child(moveWorkspacePath);
                pkgPath.mkdirs();

                tempPath.moveAllChildrenTo(pkgPath);
                listener.getLogger().println(pkgPath.toString());
            }

            // (2nd) make the debian directory
            FilePath debianDir = workspace.child("DEBIAN");
            debianDir.mkdirs();

            // (3rd) make control file
            FilePath controlFile = debianDir.child("control");
            controlFile.write(
                    makeControlFile(debianDir, packageNameSub, version, dependenciesSub,
                            maintainer, build.getEnvironment(listener)), "UTF-8");

            // (4th) make conffiles dir
            FilePath conffilesDir = debianDir.child("conffiles/");
            conffilesDir.mkdirs();

            // (5th) save postinst, preinst, postrm, prerm to files
            FilePath preinstFile = debianDir.child("preinst");
            FilePath postinstFile = debianDir.child("postinst");
            FilePath prermFile = debianDir.child("prerm");
            FilePath postrmFile = debianDir.child("postrm");
            preinstFile.write(preinst, "UTF-8");
            postinstFile.write(postinst, "UTF-8");
            prermFile.write(prerm, "UTF-8");
            postrmFile.write(postrm, "UTF-8");

            // (6th) set DEB_PKG_NAME env var
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
        sb.append("Package:" + packageNameSub);
        sb.append("Version: " + version);
        sb.append("Section: devel");
        sb.append("Priority: optional");
        sb.append("Architecture: all");
        sb.append("Depends: " + dependenciesSub);
        sb.append("Maintainer: " + maintainer);
        sb.append("Description: " + env.get("JOB_NAME") + " (built by jenkins)");
        for (String key : env.keySet()) {
            sb.append(" " + key + " - " + env.get(key));
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
                return FormValidation.error("Please set a name");
            return FormValidation.ok();
        }

        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        public String getDisplayName() {
            return "Deb Packaager";
        }
    }
}
