package hudson.cli;

import hudson.Extension;
import hudson.model.AbstractBuildExt;
import hudson.scm.ChangeLogSetExt;
import hudson.scm.ChangeLogSetExt.Entry;
import hudson.util.QuotedStringTokenizer;
import hudson.util.export.Flavor;
import hudson.util.export.Model;
import hudson.util.export.ModelBuilder;
import org.kohsuke.args4j.Option;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

/**
 * Retrieves a change list for the specified builds.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension
public class ListChangesCommand extends AbstractBuildRangeCommand {

    @Override
    public String getShortDescription() {
        return "Dumps the changelog for the specified build(s)";
    }

//    @Override
//    protected void printUsageSummary(PrintStream stderr) {
//        TODO
//    }
    enum Format {

        XML, CSV, PLAIN
    }
    @Option(name = "-format", usage = "Controls how the output from this command is printed.")
    public Format format = Format.PLAIN;

    @Override
    protected int act(List<AbstractBuildExt<?, ?>> builds) throws IOException {
        // Loading job for this CLI command requires Item.READ permission.
        // No other permission check needed.
        switch (format) {
            case XML:
                PrintWriter w = new PrintWriter(stdout);
                w.println("<changes>");
                for (AbstractBuildExt build : builds) {
                    w.println("<build number='" + build.getNumber() + "'>");
                    ChangeLogSetExt<?> cs = build.getChangeSet();
                    Model p = new ModelBuilder().get(cs.getClass());
                    p.writeTo(cs, Flavor.XML.createDataWriter(cs, w));
                    w.println("</build>");
                }
                w.println("</changes>");
                w.flush();
                break;
            case CSV:
                for (AbstractBuildExt build : builds) {
                    ChangeLogSetExt<?> cs = build.getChangeSet();
                    for (Entry e : cs) {
                        stdout.printf("%s,%s\n",
                                QuotedStringTokenizer.quote(e.getAuthor().getId()),
                                QuotedStringTokenizer.quote(e.getMsg()));
                    }
                }
                break;
            case PLAIN:
                for (AbstractBuildExt build : builds) {
                    ChangeLogSetExt<?> cs = build.getChangeSet();
                    for (Entry e : cs) {
                        stdout.printf("%s\t%s\n", e.getAuthor(), e.getMsg());
                        for (String p : e.getAffectedPaths()) {
                            stdout.println("  " + p);
                        }
                    }
                }
                break;
        }

        return 0;
    }
}
