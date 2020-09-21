package org.jenkinsci.plugins.qftest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import com.google.common.collect.ImmutableSet;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.ListBoxModel;

public class QFTestStep extends Step implements QFTestParamProvider {

	private static final long serialVersionUID = 958905529533079450L;

	/* >> SAME LOGIC AS IN QFTESTSTEP >> */

	@DataBoundConstructor
    public QFTestStep(List<Suites> suitefield) {
        if (suitefield != null) {
            this.suitefield = new ArrayList<Suites>(suitefield);
        } else {
            this.suitefield = Collections.<Suites>emptyList();
        }
    }

    private final List<Suites> suitefield;

    public List<Suites> getSuitefield() {
        return suitefield;
    }

    @CheckForNull
    private String customPath;

    public @CheckForNull
    String getCustomPath() {
        return customPath;
    }

    @DataBoundSetter
    public void setCustomPath(String customPath) {
        if (customPath != null) {
            this.customPath = customPath.isEmpty() ? null : customPath;
        }
    }

    @CheckForNull
    private String customReports;

    public @Nonnull
    String getReportDirectory() {
        return (customReports != null ? customReports : DefaultValues.reportDir);
    }

    @DataBoundSetter
    public void setReportDirectory(String customReports) {
        if (customReports == null || customReports.isEmpty() || customReports.equals(DefaultValues.reportDir)) {
            this.customReports = null;
        } else {
            this.customReports = customReports;
        }
    }

    private String customReportArgs = "";

    @Override
    public String getReportGenArgs() {
        return customReportArgs;
    }

    @DataBoundSetter
    public void setReportGenArgs(String extraArgs) {
        customReportArgs = extraArgs;

    }

    private Result onTestWarning;
    private Result onTestError;
    private Result onTestException;
    private Result onTestFailure;

    public String getOnTestWarning() {
        return (onTestWarning != null ? onTestWarning : DefaultValues.testWarning).toString();
    }

    public String getOnTestError() {
        return (onTestError != null ? onTestError : DefaultValues.testError).toString();
    }

    public String getOnTestException() {
        return (onTestException != null ? onTestException : DefaultValues.testException).toString();
    }

    public String getOnTestFailure() {
        return (onTestFailure != null ? onTestFailure : DefaultValues.testFailure).toString();
    }

    @DataBoundSetter
    public void setOnTestWarning(String onTestWarning) {
        if (!onTestWarning.equals(DefaultValues.testWarning.toString())) {
            this.onTestWarning = Result.fromString(onTestWarning);
        }
    }

    @DataBoundSetter
    public void setOnTestError(String onTestError) {
        if (!onTestError.equals(DefaultValues.testError.toString())) {
            this.onTestError = Result.fromString(onTestError);
        }
    }

    @DataBoundSetter
    public void setOnTestException(String onTestException) {
        if (!onTestException.equals(DefaultValues.testException.toString())) {
            this.onTestException = Result.fromString(onTestException);
        }
    }

    @DataBoundSetter
    public void setOnTestFailure(String onTestFailure) {
        if (!onTestFailure.equals(DefaultValues.testFailure.toString())) {
            this.onTestFailure = Result.fromString(onTestFailure);
        }
    }
    /* << SAME LOGIC AS IN QFTESTSTEP << */

    @Override
    public StepExecution start(StepContext stepContext) throws Exception {
        return new QFTestExecutor(this, stepContext);
    }

    @Extension
    public static final class DescriptorImpl extends StepDescriptor {

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return ImmutableSet.of(Run.class, FilePath.class, Launcher.class, TaskListener.class, EnvVars.class, FlowNode.class);
        }

        @Override
        public String getFunctionName() {
            return "QFTest";
        }

        @Override
        @Nonnull
        public String getDisplayName() {
            return "Run the configured QF-Test suites.";
        }


        public ListBoxModel doFillOnTestWarningItems() {
            //no persistent configuration available .. use default
            return QFTestConfigBuilder.DescriptorImpl.fillOnTestResult(DefaultValues.testWarning);
        }

        public ListBoxModel doFillOnTestErrorItems() {
            //no persistent configuration available .. use default
            return QFTestConfigBuilder.DescriptorImpl.fillOnTestResult(DefaultValues.testError);
        }

        public ListBoxModel doFillOnTestExceptionItems() {
            //no persistent configuration available .. use default
            return QFTestConfigBuilder.DescriptorImpl.fillOnTestResult(DefaultValues.testException);
        }

        public ListBoxModel doFillOnTestFailureItems() {
            //no persistent configuration available .. use default
            return QFTestConfigBuilder.DescriptorImpl.fillOnTestResult(DefaultValues.testFailure);
        }
    }
}
