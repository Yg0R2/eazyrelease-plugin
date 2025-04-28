package org.eazyportal.plugin.release.jenkins.action;

import com.google.inject.Inject;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.InvisibleAction;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.eazyportal.plugin.release.core.action.FinalizeReleaseVersionAction;
import org.eazyportal.plugin.release.core.action.FinalizeSnapshotVersionAction;
import org.eazyportal.plugin.release.core.action.PrepareRepositoryForReleaseAction;
import org.eazyportal.plugin.release.core.action.ReleaseAction;
import org.eazyportal.plugin.release.core.action.SetReleaseVersionAction;
import org.eazyportal.plugin.release.core.action.SetSnapshotVersionAction;
import org.eazyportal.plugin.release.core.action.UpdateScmAction;
import org.eazyportal.plugin.release.core.version.ReleaseVersionProvider;
import org.eazyportal.plugin.release.core.version.SnapshotVersionProvider;
import org.eazyportal.plugin.release.core.version.VersionIncrementProvider;
import org.eazyportal.plugin.release.jenkins.ReleaseStepConfig;
import org.eazyportal.plugin.release.jenkins.project.ProjectDescriptorFactory;
import org.eazyportal.plugin.release.jenkins.project.model.FilePathProjectFile;
import org.eazyportal.plugin.release.jenkins.scm.ScmActionFactory;

import java.io.Serializable;

@Extension
public class ReleaseActionFactory extends InvisibleAction implements Serializable {

    @Inject
    private transient ReleaseStepConfig releaseStepConfig;

    public ReleaseAction create(
        Class<? extends ReleaseAction> clazz, Run<?, ?> run, FilePath workspace, EnvVars envVars,
        Launcher launcher, TaskListener taskListener
    ) {
        var actionContext = run.getAction(ActionContextFactory.class)
            .create(envVars);

        var scmActions = run.getAction(ScmActionFactory.class)
            .create(envVars, launcher, taskListener);

        var projectDescriptor = run.getAction(ProjectDescriptorFactory.class)
            .create(new FilePathProjectFile(workspace), scmActions);

        ReleaseAction instance;
        if (FinalizeReleaseVersionAction.class.isAssignableFrom(clazz)) {
            instance = new FinalizeReleaseVersionAction<>(
                actionContext, projectDescriptor, scmActions
            );
        } else if (FinalizeSnapshotVersionAction.class.isAssignableFrom(clazz)) {
            instance = new FinalizeSnapshotVersionAction<>(
                actionContext, projectDescriptor, scmActions
            );
        } else if (PrepareRepositoryForReleaseAction.class.isAssignableFrom(clazz)) {
            instance = new PrepareRepositoryForReleaseAction<>(
              projectDescriptor, scmActions, releaseStepConfig.getScmConfig()
            );
        } else if (SetReleaseVersionAction.class.isAssignableFrom(clazz)) {
            instance = new SetReleaseVersionAction<>(
                actionContext, releaseStepConfig.getConventionalCommitTypes(), new ReleaseVersionProvider(),
                projectDescriptor, scmActions, releaseStepConfig.getScmConfig(), new VersionIncrementProvider()
            );
        } else if (SetSnapshotVersionAction.class.isAssignableFrom(clazz)) {
            instance = new SetSnapshotVersionAction<>(
                actionContext, projectDescriptor, scmActions, releaseStepConfig.getScmConfig(),
                new SnapshotVersionProvider()
            );
        } else if (UpdateScmAction.class.isAssignableFrom(clazz)) {
            instance = new UpdateScmAction<>(
                projectDescriptor, scmActions, releaseStepConfig.getScmConfig()
            );
        } else {
            throw new IllegalArgumentException("Invalid ReleaseAction implementation: " + clazz.getName());
        }

        return instance;
    }

}
