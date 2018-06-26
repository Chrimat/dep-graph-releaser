package ch.loewenfels.depgraph.gui.recovery

import ch.loewenfels.depgraph.data.*
import ch.loewenfels.depgraph.data.maven.jenkins.M2ReleaseCommand
import ch.loewenfels.depgraph.data.serialization.CommandStateJson
import ch.loewenfels.depgraph.gui.App
import ch.loewenfels.depgraph.gui.jobexecution.*
import ch.loewenfels.depgraph.gui.serialization.ModifiableState
import ch.loewenfels.depgraph.gui.serialization.ProjectJson
import ch.loewenfels.depgraph.gui.serialization.ReleasePlanJson
import ch.loewenfels.depgraph.gui.serialization.deserializeProjectId
import ch.loewenfels.depgraph.gui.showDialog
import ch.loewenfels.depgraph.gui.showInfo
import kotlin.js.Promise


fun recover(modifiableState: ModifiableState, defaultJenkinsBaseUrl: String?): Promise<ModifiableState> {
    if (defaultJenkinsBaseUrl == null) {
        showInfo(
            "You have opened a pipeline which is in state ${ReleaseState.IN_PROGRESS.name}.\n" +
                "Yet, since you have not provided a ${App.PUBLISH_JOB} in the URL we cannot recover the ongoing process."
        )
        return Promise.resolve(modifiableState)
    }

    return showDialog(
        """
            |You have opened a pipeline which is in state ${ReleaseState.IN_PROGRESS.name}.
            |Are you the release manager and would like to recover the ongoing process?
            |
            |Extra information: By clicking 'Yes' the dep-graph-releaser will check if the current state of the individual commands is still appropriate and update if necessary. Furthermore, it will resume the process meaning it will trigger dependent jobs if a job finishes. Or in other words, it will almost look like you have never left the page.
            |
            |Do not click 'Yes' (but 'No') if you (or some else) have started the release process in another tab/browser since otherwise dependent jobs will be triggered multiple times.
            """.trimMargin()
    ).then { isReleaseManager ->
        if (!isReleaseManager) {
            showInfo(
                "We do not yet support tracking of a release process at the moment. Which means, what you see above is only a state of the process but the process as such has likely progressed already." +
                    "\nPlease open a feature request $GITHUB_NEW_ISSUE if you have the need of tracking a release (which runs in another tab/browser)."
            )
            return@then Promise.resolve(modifiableState)
        }
        recoverCommandStates(modifiableState, defaultJenkinsBaseUrl)
    }.then { it }
}

private fun recoverCommandStates(
    modifiableState: ModifiableState,
    jenkinsBaseUrl: String
): Promise<ModifiableState> {
    val releasePlanJson = JSON.parse<ReleasePlanJson>(modifiableState.json)
    val promises = modifiableState.releasePlan.iterator().asSequence().map { project ->
        val lazyProjectJson by lazy {
            releasePlanJson.projects.single { deserializeProjectId(it.id) == project.id }
        }
        val promises = project.commands.mapIndexed { index, command ->
            when (command.state) {
            //TODO we need also to check if a job is queueing or already finished if the state is Ready.
            // It could be that we trigger a job and then the browser crashed (or the user closed the page)
            // before we had a chance to publish the new state => We could introduce a state Triggered but
            // this would mean we need one more publish per job which is bad. This brings me to another idea,
            // we could get rid of the save after state queueing if we implement recovery from state ready.
            // Nah... then we wouldn't save anything anymore which is bad as well (we have to save from time
            // to time :D). But I think there is potential here to reduce the number of publishes per pipeline.
                CommandState.Ready -> TODO("Not yet supported")
                CommandState.Queueing -> recoverStateQueueing(
                    modifiableState, jenkinsBaseUrl, project, command, lazyProjectJson, index
                )
                CommandState.InProgress -> recoverStateTo(lazyProjectJson, index, CommandStateJson.State.RE_POLLING)
                else -> Promise.resolve(Unit)
            }
        }
        Promise.all(promises.toTypedArray())
    }
    return Promise.all(promises.toList().toTypedArray()).then {
        ModifiableState(modifiableState, JSON.stringify(releasePlanJson))
    }
}

private fun recoverStateQueueing(
    modifiableState: ModifiableState,
    jenkinsBaseUrl: String,
    project: Project,
    command: Command,
    lazyProjectJson: ProjectJson,
    index: Int
): Promise<*> {
    return if (command is M2ReleaseCommand) {
        val usernameAndApiToken = UsernameTokenRegistry.forHostOrThrow(jenkinsBaseUrl)
        issueCrumb(jenkinsBaseUrl, usernameAndApiToken).then { authData ->
            val jobExecutionData = recoverJobExecutionData(modifiableState, project, command)
            val buildUrl = command.buildUrl
            extractBuildNumber(buildUrl, authData, jobExecutionData).then { buildNumber ->
                lazyProjectJson.commands[index].p.asDynamic().buildUrl = jobExecutionData.jobBaseUrl + buildNumber
                recoverStateTo(lazyProjectJson, index, CommandStateJson.State.RE_POLLING)
            }.catch {
                recoverStateTo(lazyProjectJson, index, CommandStateJson.State.FAILED)
            }
        }
    } else {
        throw UnsupportedOperationException(
            "We do not know how to recover a command of type ${command::class.simpleName}." +
                "\nCommand: $command"
        )
    }
}

private fun recoverJobExecutionData(
    modifiableState: ModifiableState,
    project: Project,
    command: Command
): JobExecutionData {
    val jobExecutionDataFactory = when (modifiableState.releasePlan.typeOfRun) {
        TypeOfRun.DRY_RUN -> modifiableState.dryRunExecutionDataFactory
        TypeOfRun.RELEASE, TypeOfRun.SIMULATION -> modifiableState.releaseJobExecutionDataFactory
    }
    return jobExecutionDataFactory.create(project, command)
}

private fun recoverStateTo(lazyProjectJson: ProjectJson, index: Int, state: CommandStateJson.State): Promise<*> {
    lazyProjectJson.commands[index].p.state.asDynamic().state = state.name
    return Promise.resolve(Unit)
}
