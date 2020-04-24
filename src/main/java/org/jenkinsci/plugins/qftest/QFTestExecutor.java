package org.jenkinsci.plugins.qftest;

import com.pivovarit.function.ThrowingFunction;
import htmlpublisher.HtmlPublisher;
import htmlpublisher.HtmlPublisherTarget;
import hudson.*;
import hudson.model.Descriptor;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.ArgumentListBuilder;
import jenkins.model.Jenkins;
import jenkins.util.BuildListenerAdapter;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class QFTestExecutor extends SynchronousNonBlockingStepExecution<Void> {

    final QFTestParamProvider params;

    QFTestExecutor(QFTestParamProvider params, StepContext context) {
        super(context);
        this.params = params;
    }

    @Override
    protected Void run() throws Exception {
        Imp.run(
                getContext().get(Run.class),
                getContext().get(FilePath.class),
                getContext().get(Launcher.class),
                getContext().get(TaskListener.class),
                getContext().get(EnvVars.class),
                this.params
        );

        return (Void) null;
    }

    public static class Imp {

        static private Character reduceReturnValues(@CheckForNull  Character previous, @Nonnull Character ret) {
            if (previous  == null || ret > previous) {
                //only update to first non-negative return value ) {
                return ret;
            } else {
                return previous;
            }
        }

        //TODO: return result objects which may be further processed by the different plugin flavours
        static public void run(
                @Nonnull Run<?, ?> run,
                @Nonnull FilePath workspace,
                @Nonnull Launcher launcher,
                @Nonnull TaskListener listener,
                @Nonnull EnvVars env,
                QFTestParamProvider qftParams)
                throws InterruptedException, IOException
        {


            FilePath logdir = workspace.child(qftParams.getReportDirectory());

            listener.getLogger().println("(Creating and/or clearing " + logdir.getName() + " directory");
            logdir.mkdirs();
            logdir.deleteContents();

            FilePath htmldir = logdir.child("html");
            htmldir.mkdirs();

			FilePath junitdir = logdir.child("junit");
			junitdir.mkdirs();

            FilePath qrzdir = logdir.child("qrz");
            qrzdir.mkdirs();

            ThrowingFunction<QFTestCommandLineBuilder, Proc, ?> startQFTestProc = (QFTestCommandLineBuilder args) -> {
                return launcher.new ProcStarter()
                        .cmds(args)
                        .stdout(listener)
                        .pwd(workspace)
                        .envs(env)
                        .start();
            };

            Consumer<String> resultSetter = (String resAsString) -> {
                run.setResult(Result.fromString(resAsString));
            };

            //RUN SUITES
            Character reducedQFTReturnValue = qftParams.getSuitefield().stream()
                    .peek(sf -> listener.getLogger().println(sf.toString())) //before env expansion
                    .map(sf -> new Suites(
                            env.expand(sf.getSuitename()), env.expand(sf.getCustomParam())
                    ))
                    .peek(sf -> listener.getLogger().println(sf.toString())) //after env expansion
                    .flatMap(sf -> {
                        try {
                            return sf.expand(workspace);
                        } catch (java.lang.Exception ex) {
                            Functions.printStackTrace(
                                    ex, listener.fatalError(
                                            new StringBuilder("During expansion of").append(sf).append("\n").append(ex.getMessage()).toString()
                                    ));
                            return Stream.<Suites>empty();
                        }
                    })
                    .peek(sf -> listener.getLogger().println(sf.toString())) //after path expansion
                    .map(sf -> {
                        try {

                            QFTestCommandLineBuilder args = QFTestCommandLineBuilder.newCommandLine(
                                    qftParams.getCustomPath(), launcher.isUnix(), QFTestCommandLineBuilder.RunMode.RUN
                            );

                            args.presetArg(QFTestCommandLineBuilder.PresetType.ENFORCE, "-run")
                                    .presetArg(QFTestCommandLineBuilder.PresetType.DROP, "-report")
                                    .presetArg(QFTestCommandLineBuilder.PresetType.DROP, "-report.html")
                                    .presetArg(QFTestCommandLineBuilder.PresetType.DROP, "-report.html")
                                    .presetArg(QFTestCommandLineBuilder.PresetType.DROP, "-report.junit")
                                    .presetArg(QFTestCommandLineBuilder.PresetType.DROP, "-report.xml")
                                    .presetArg(QFTestCommandLineBuilder.PresetType.DROP, "-gendoc")
                                    .presetArg(QFTestCommandLineBuilder.PresetType.DROP, "-testdoc")
                                    .presetArg(QFTestCommandLineBuilder.PresetType.DROP, "-pkgdoc")
                                    .presetArg(QFTestCommandLineBuilder.PresetType.ENFORCE, "-nomessagewindow")
                                    .presetArg(QFTestCommandLineBuilder.PresetType.ENFORCE, "-runlogdir", qrzdir.getRemote());
                            args.addSuiteConfig(workspace, sf);

                            int ret = startQFTestProc.apply(args).join();

                            listener.getLogger().println("  Finished with return value: " + ret);
                            return (char) ret;

                        } catch (java.lang.Exception ex) {
                            listener.error(ex.getMessage());
                            Functions.printStackTrace(ex, listener.fatalError(ex.getMessage()));
                            return (char) 4; //Test exception
                        }
                    })
                    .reduce(null, Imp::reduceReturnValues);

            //DETEERMINE BUILD STATUS

            if (reducedQFTReturnValue != null ) {
                switch (reducedQFTReturnValue.charValue()) {
                    case (0):
                        //run.setResult(run.getResult().combine(Result.SUCCESS));
                        resultSetter.accept(Result.SUCCESS.toString());
                        break;
                    case (1):
                        //run.setResult(run.getResult().combine(onTestWarning));
                        resultSetter.accept(qftParams.getOnTestWarning());
                        break;
                    case (2):
                        //run.setResult(run.getResult().combine(onTestError));
                        resultSetter.accept(qftParams.getOnTestError());
                        break;
                    case (3):
                        //run.setResult(run.getResult().combine(onTestException));
                        resultSetter.accept(qftParams.getOnTestException());
                        break;
                    default:
                        //run.setResult(run.getResult().combine(onTestFailure));
                        resultSetter.accept(qftParams.getOnTestFailure());
                        break;
                }
            } else {
                resultSetter.accept(qftParams.getOnTestFailure());
            }

            //PICKUP ARTIFACTS
            java.util.function.Function<FilePath, String> fp_names = (fp -> fp.getName());
            run.pickArtifactManager().archive(
                    qrzdir, launcher, new BuildListenerAdapter(listener),
                    Arrays.stream(qrzdir.list("*.q*"))
                            .collect(Collectors.toMap(fp_names, fp_names))
            );

            //CREATE REPORTS
            listener.getLogger().println("Creating reports");

            try {

                QFTestCommandLineBuilder args = QFTestCommandLineBuilder.newCommandLine(
                        qftParams.getCustomPath(), launcher.isUnix(), QFTestCommandLineBuilder.RunMode.GENREPORT
                );
                args.presetArg(QFTestCommandLineBuilder.PresetType.ENFORCE, "-runlogdir", qrzdir.getRemote());

                RunLogs rl = new RunLogs(
                        new ArgumentListBuilder(
							"-report.html", htmldir.getRemote(), "-report.junit", junitdir.getRemote()
                        ).toStringWithQuote()
                );

                int nReports = args.addSuiteConfig(qrzdir, rl);
                if (nReports > 0) {
                    startQFTestProc.apply(args).join();
                } else {
                    listener.getLogger().println("No reports found. Marking run with `test failure'");
                    run.setResult(Result.fromString(qftParams.getOnTestFailure()));
                }
            } catch (java.lang.Exception ex) {
                resultSetter.accept(qftParams.getOnTestFailure());
                Functions.printStackTrace(ex, listener.fatalError(ex.getMessage()));
            }

            //Publish HTML report
            HtmlPublisher.publishReports(
                    run, workspace, listener, Collections.singletonList(new HtmlPublisherTarget(
						"QF-Test Report", htmldir.getRemote(), "report.html", true, false, false
                    )), qftParams.getClass() //TODO: this clazz ok?
            );
        }
    }
}