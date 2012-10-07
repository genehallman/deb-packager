package jenkins.plugins.debpackager;

import hudson.Extension;
import hudson.FilePath;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;

import java.io.IOException;

import org.kohsuke.stapler.DataBoundConstructor;

public class DebScript extends AbstractDescribableImpl<DebScript> {

    private String value;
    private String data;

    public String getValue() {
        return value;
    }

    public String getData() {
        return data;
    }

    @DataBoundConstructor
    public DebScript(String value, String data) {
        this.value = value;
        this.data = data;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<DebScript> {
        public String getDisplayName() {
            return "Debian Maintainers Script";
        }
    }

    public String getScriptContents(FilePath workspace) throws IOException {
        if (value.equalsIgnoreCase("file")) {
            FilePath refScript = workspace.child(data);
            return refScript.readToString();
        } else if (value.equalsIgnoreCase("script")) {
            return data;
        }
        return null;
    }

    public boolean hasContents() {
        return value.equalsIgnoreCase("file") || value.equalsIgnoreCase("script");
    }

    public void create(String scriptName, FilePath debianPath, FilePath workspace)
            throws IOException, InterruptedException {
        if (this.hasContents()) {
            FilePath preinstFile = debianPath.child(scriptName);
            preinstFile.write(getScriptContents(workspace), "UTF-8");
            preinstFile.chmod(Integer.parseInt("755", 8));
        }
    }

    public String toString() {
        return String.format("{value: %s, data: %s, hasContents(): %b}", this.value, this.data,
                this.hasContents());
    }
}
