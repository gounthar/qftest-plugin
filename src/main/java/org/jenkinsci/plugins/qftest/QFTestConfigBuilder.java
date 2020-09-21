/*
 * The MIT License
 *
 * Copyright (c) 2017 Quality First Software GmbH
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.jenkinsci.plugins.qftest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import net.sf.json.JSONObject;

import static java.util.Collections.emptyList;

@edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
	value="UUF_UNUSED_FIELD",
        justification="Need unused transient values for backward compatibility"
)
public class QFTestConfigBuilder extends Builder implements QFTestParamProvider
{

	/* deprecated members */
	private static final long serialVersionUID = 8305941021849820496L;
	
	private transient boolean customReportTempDirectory;
	private transient boolean specificQFTestVersion;
	private transient boolean suitesEmpty;
	private transient boolean daemonSelected;
	private transient String daemonhost;
	private transient String daemonport;

	/* >> SAME LOGIC AS IN QFTESTSTEP >> */

    /**
     * CTOR
     *
     * @param suitefield Contains name of testsuites and their command line arguments
     */
    @DataBoundConstructor
    public QFTestConfigBuilder(List<Suites> suitefield) {
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


	/** Called by XStream when deserializing object
	 */
	protected Object readResolve() {
	    this.setCustomPath(customPath);
	    this.setReportDirectory(customReports);
	    return this;
	}




	@Override
	public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException, java.io.IOException, InterruptedException {
		FilePath workspace = build.getWorkspace();
		assert(workspace != null);
		QFTestExecutor.Imp.run(build, workspace, launcher, listener, build.getEnvironment(listener), this);
		return true;
	}

	/**
	 * Implementation of descriptor
	 */
	@Extension
	public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

		@CheckForNull
		private String qfPath;

		@CheckForNull
		private String qfPathUnix;

		public DescriptorImpl() {

			load();

			//ensure qfPath is either null or non-empty string
			qfPath = this.getQfPath();
			qfPathUnix = this.getQfPathUnix();
		}

		/*
		 * (non-Javadoc)
		 *
		 * @see hudson.tasks.BuildStepDescriptor#isApplicable(java.lang.Class)
		 */
		@Override
		@SuppressWarnings("rawtypes")
		public boolean isApplicable(Class<? extends AbstractProject> aClass) {
			// Indicates that this builder can be used with all kinds of project
			// types
			return true;
		}

		/*
		 * (non-Javadoc)
		 *
		 * @see hudson.model.Descriptor#getDisplayName()
		 */
		@Override
		public String getDisplayName() {
			return "Run QF-Test";
		}

		/*
		 * (non-Javadoc)
		 *
		 * @see
		 * hudson.model.Descriptor#configure(org.kohsuke.stapler.StaplerRequest,
		 * net.sf.json.JSONObject)
		 */
		@Override
		public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {

			qfPath = formData.getString("qfPath");
			qfPathUnix = formData.getString("qfPathUnix");

			save();
			return super.configure(req, formData);
		}

		/**
		 * Returns the defined QF-Test installation path (Windows) in the global
		 * setting.
		 *
		 * @return path to QF-Test installation (Windows)
		 */
		public String getQfPath() {
			if (qfPath != null && !qfPath.isEmpty()) {
				return qfPath;
			} else {
				return null;
			}
		}

		/**
		 * Returns the defined QF-Test installation path (Unix) in the global
		 * setting.
		 *
		 * @return path to QF-Test installation (Unix)
		 */
		public String getQfPathUnix() {
		    if (qfPathUnix != null && !qfPathUnix.isEmpty()) {
				return qfPathUnix;
			} else {
				return null;
			}
		}

		//TODO: change this
		public FormValidation doCheckDirectory(@QueryParameter String value) {

			if (value.contains(":") || value.contains("*")
					|| value.contains("?") || value.contains("<")
					|| value.contains("|") || value.contains(">")) {
				return FormValidation.error("Path contains forbidden characters");
			}
			return FormValidation.ok();
		}


		static public ListBoxModel fillOnTestResult(Result preSelect) {
			ListBoxModel items = new ListBoxModel();
			Stream.of(Result.SUCCESS, Result.UNSTABLE, Result.FAILURE, Result.ABORTED, Result.NOT_BUILT)
					.forEach(res -> {
						items.add(res.toString());
						if (preSelect == res) { //mark this as selection
							items.get(items.size()-1).selected = true;
						}
					});

			return items;
		}


		public ListBoxModel doFillOnTestWarningItems(@QueryParameter("onTestWarning") String preset) {
			return fillOnTestResult(preset.isEmpty()? DefaultValues.testWarning : Result.fromString(preset));
		}

		public ListBoxModel doFillOnTestErrorItems(@QueryParameter("onTestError") String preset) {
			return fillOnTestResult(preset.isEmpty() ? DefaultValues.testError : Result.fromString(preset));
		}

		public ListBoxModel doFillOnTestExceptionItems(@QueryParameter("onTestException") String preset) {
			return fillOnTestResult(preset.isEmpty() ? DefaultValues.testException : Result.fromString(preset));
		}

		public ListBoxModel doFillOnTestFailureItems(@QueryParameter("onTestFailure") String preset) {
			return fillOnTestResult(preset.isEmpty() ? DefaultValues.testFailure : Result.fromString(preset));
		}
	}

	// Descriptor is needed to access global variables
	/*
	 * (non-Javadoc)
	 *
	 * @see hudson.tasks.Builder#getDescriptor()
	 */
	@Override
	public DescriptorImpl getDescriptor() {
		return (DescriptorImpl) super.getDescriptor();
	}
}
