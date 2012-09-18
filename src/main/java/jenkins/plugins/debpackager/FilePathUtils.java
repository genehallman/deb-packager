package jenkins.plugins.debpackager;

import hudson.FilePath;
import hudson.FilePath.FileCallable;
import hudson.remoting.VirtualChannel;

import java.io.File;
import java.io.IOException;

public class FilePathUtils {

    public static void chown(FilePath filepath, final String owner, final String group)
            throws IOException, InterruptedException {
        chown(filepath, owner, group, false);
    }

    @SuppressWarnings("serial")
    public static void chown(FilePath filepath, final String owner, final String group,
            final boolean recursive) throws IOException, InterruptedException {

        if (!isUnix())
            return;
        filepath.act(new FileCallable<Void>() {
            public Void invoke(File f, VirtualChannel channel) throws IOException,
                    InterruptedException {
                chown(f, owner, group, recursive);
                return null;
            }
        });
    }

    private static int chown(File f, String owner, String group, boolean recursive)
            throws IOException, InterruptedException {
        String command = String.format("sudo chown %s %s:%s %s", (recursive ? "-R" : ""), owner,
                group, f.getAbsolutePath());
        Process p = Runtime.getRuntime().exec(command);
        return p.waitFor();
    }

    public static boolean isUnix() {
        return File.pathSeparatorChar != ';';
    }
}
