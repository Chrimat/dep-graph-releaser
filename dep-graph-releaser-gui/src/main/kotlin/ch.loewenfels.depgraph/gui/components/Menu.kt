package ch.loewenfels.depgraph.gui.components

import ch.loewenfels.depgraph.ConfigKey
import ch.loewenfels.depgraph.data.ReleaseState
import ch.loewenfels.depgraph.data.TypeOfRun
import ch.loewenfels.depgraph.data.toProcessName
import ch.loewenfels.depgraph.generateEclipsePsf
import ch.loewenfels.depgraph.generateGitCloneCommands
import ch.loewenfels.depgraph.generateListOfDependentsWithoutSubmoduleAndExcluded
import ch.loewenfels.depgraph.gui.*
import ch.loewenfels.depgraph.gui.actions.Downloader
import ch.loewenfels.depgraph.gui.components.Messages.Companion.showError
import ch.loewenfels.depgraph.gui.components.Messages.Companion.showInfo
import ch.loewenfels.depgraph.gui.components.Messages.Companion.showSuccess
import ch.loewenfels.depgraph.gui.components.Messages.Companion.showThrowableAndThrow
import ch.loewenfels.depgraph.gui.jobexecution.*
import ch.loewenfels.depgraph.gui.serialization.ModifiableState
import org.w3c.dom.HTMLElement
import org.w3c.notifications.GRANTED
import org.w3c.notifications.Notification
import org.w3c.notifications.NotificationOptions
import org.w3c.notifications.NotificationPermission
import kotlin.browser.window
import kotlin.dom.addClass
import kotlin.dom.hasClass
import kotlin.dom.removeClass
import kotlin.js.Promise

@Suppress("UnusedPrivateMember" /*TODO remove if https://github.com/arturbosch/detekt/issues/1227 is fixed */)
external fun encodeURIComponent(encodedURI: String): String

class Menu(private val eventManager: EventManager) {
    init {
        setUpMenuLayers(
            Triple(toolsButton, "toolbox", TOOLS_INACTIVE_TITLE to "Close the toolbox."),
            Triple(settingsButton, "config", SETTINGS_INACTIVE_TITLE to "Close Settings.")
        )
    }

    private fun setUpMenuLayers(vararg pairs: Triple<HTMLElement, String, Pair<String, String>>) {
        pairs.forEach { (button, id, inactiveAndActiveTitle) ->
            val activeCssClass = "active"
            button.addClickEventListenerIfNotDeactivatedNorDisabled {
                //close the others
                pairs.forEach { (_, otherId) ->
                    if (id != otherId) {
                        elementById(otherId).removeClass(activeCssClass)
                    }
                }

                val layer = elementById(id)
                if (layer.hasClass(activeCssClass)) {
                    button.title = inactiveAndActiveTitle.first
                } else {
                    button.title = inactiveAndActiveTitle.second
                }
                layer.toggleClass(activeCssClass)
            }
            elementById("$id:close").addClickEventListener {
                elementById(id).removeClass(activeCssClass)
            }
        }
    }

    internal fun initDependencies(
        downloader: Downloader,
        modifiableState: ModifiableState,
        processStarter: ProcessStarter?
    ) {
        Companion.modifiableState = modifiableState

        window.onbeforeunload = {
            when {
                saveButton.isNotDeactivated() ->
                    "Your changes will be lost, sure you want to leave the page?"
                Pipeline.getReleaseState() === ReleaseState.IN_PROGRESS ->
                    "You might lose state changes if you navigate away from this page, sure you want to proceed?"
                else -> null
            }
        }

        initSaveAndDownloadButton(downloader, processStarter)
        initRunButtons(modifiableState, processStarter)
        activateToolsButton()
        activateSettingsButton()
        initStartOverButton(processStarter)
        initExportButtons(modifiableState)
        initReportButtons(modifiableState)
        registerForStartAndEndReleaseEvent(processStarter)
    }


    private fun initSaveAndDownloadButton(downloader: Downloader, processStarter: ProcessStarter?) {
        deactivateSaveButtonAndReactivateOthers()
        if (processStarter != null) {
            saveButton.addClickEventListenerIfNotDeactivatedNorDisabled {
                save(processStarter)
            }
        }
        downloadButton.title = "Download the release.json"
        downloadButton.removeClass(DEACTIVATED_CSS_CLASS)
        downloadButton.addClickEventListenerIfNotDeactivatedNorDisabled { downloader.download() }
    }


    private fun initRunButtons(modifiableState: ModifiableState, processStarter: ProcessStarter?) {
        if (processStarter != null) {
            activateDryRunButton()
            dryRunButton.addClickEventListenerIfNotDeactivatedNorDisabled {
                eventManager.startDryRun(modifiableState, processStarter)
            }
            activateReleaseButton()
            releaseButton.addClickEventListenerIfNotDeactivatedNorDisabled {
                eventManager.startRelease(modifiableState, processStarter)
            }
        }

        activateExploreButton()
        exploreButton.addClickEventListenerIfNotDeactivatedNorDisabled {
            eventManager.startExploration(modifiableState, processStarter)
        }
    }

    private fun initStartOverButton(processStarter: ProcessStarter?) {
        if (processStarter != null) {
            activateStartOverButton()
            startOverButton.addClickEventListener {
                eventManager.resetForNewProcess().then {
                    resetButtons()
                    startOverButton.style.display = "none"
                    save(processStarter)
                }
            }
        }
    }

    private fun resetButtons() {
        val (processName, _, buttonText) = getCurrentRunData()
        listOf(dryRunButton, releaseButton, exploreButton).forEach {
            it.removeClass(DISABLED_CSS_CLASS)
        }
        buttonText.innerText = processName //currently it is 'Continue:...'
        activateDryRunButton()
        activateReleaseButton()
        activateExploreButton()
    }


    private fun initExportButtons(modifiableState: ModifiableState) {
        activateButton(eclipsePsfButton, "Download an eclipse psf-file to import all projects into eclipse.")
        activateButton(gitCloneCommandsButton, "Show git clone commands to clone the involved projects.")
        activateButton(
            listDependentsButton, "List direct and indirect dependent projects (identifiers) of the root project."
        )

        eclipsePsfButton.addClickEventListenerIfNotDeactivatedNorDisabled {
            val releasePlan = modifiableState.releasePlan
            val psfContent = generateEclipsePsf(
                releasePlan,
                Regex(releasePlan.getConfig(ConfigKey.RELATIVE_PATH_EXCLUDE_PROJECT_REGEX)),
                Regex(releasePlan.getConfig(ConfigKey.RELATIVE_PATH_TO_GIT_REPO_REGEX)),
                releasePlan.getConfig(ConfigKey.RELATIVE_PATH_TO_GIT_REPO_REPLACEMENT)
            )
            Downloader.download("customImport.psf", psfContent)
        }

        gitCloneCommandsButton.addClickEventListenerIfNotDeactivatedNorDisabled {
            val releasePlan = modifiableState.releasePlan
            val gitCloneCommands = generateGitCloneCommands(
                releasePlan,
                Regex(releasePlan.getConfig(ConfigKey.RELATIVE_PATH_EXCLUDE_PROJECT_REGEX)),
                Regex(releasePlan.getConfig(ConfigKey.RELATIVE_PATH_TO_GIT_REPO_REGEX)),
                releasePlan.getConfig(ConfigKey.RELATIVE_PATH_TO_GIT_REPO_REPLACEMENT)
            )
            val title = "Copy the following git clone commands and paste them into a terminal/command prompt"
            showOutput(title, gitCloneCommands)
        }

        listDependentsButton.addClickEventListenerIfNotDeactivatedNorDisabled {
            val releasePlan = modifiableState.releasePlan
            val list = generateListOfDependentsWithoutSubmoduleAndExcluded(
                releasePlan,
                Regex(releasePlan.getConfig(ConfigKey.RELATIVE_PATH_EXCLUDE_PROJECT_REGEX))
            )
            val title = "The following projects are (indirect) dependents of ${releasePlan.rootProjectId.identifier}"
            showOutput(title, list)
        }
    }

    private fun initReportButtons(modifiableState: ModifiableState) {
        activateButton(changelogButton, "Download changelog in CSV format")
        changelogButton.addClickEventListenerIfNotDeactivatedNorDisabled {
            Downloader.download(
                "changelog.csv",
                generateChangelog(modifiableState.releasePlan, ::appendProjectToCsvWithoutWrapper)
            )
        }
        activateButton(changelogButtonExcel, "Download changelog in CSV format for Excel")
        changelogButtonExcel.addClickEventListenerIfNotDeactivatedNorDisabled {
            Downloader.download(
                "changelog-excel.csv",
                generateChangelog(modifiableState.releasePlan, ::appendProjectToCsvExcel)
            )
        }
    }


    private fun registerForStartAndEndReleaseEvent(processStarter: ProcessStarter?) {
        EventManager.registerForProcessStartEvent {
            listOf(dryRunButton, releaseButton, exploreButton).forEach {
                it.addClass(DISABLED_CSS_CLASS)
                it.title = EventManager.getDisabledMessage()
            }
        }
        EventManager.registerForProcessEndEvent { success ->
            val (processName, button, buttonText) = getCurrentRunData()
            button.removeClass(DISABLED_CSS_CLASS)

            if (success) {
                listOf(dryRunButton, releaseButton, exploreButton).forEach {
                    if (button != releaseButton) {
                        it.title =
                            "Current process is '$processName' - click on 'Start Over' to start over with a new process."
                    } else {
                        it.title = "Release successful, use a new pipeline for a new release " +
                            "or make changes and continue with the release process."
                    }
                }
                val hintIfNotRelease = if (processStarter != null && button != releaseButton) {
                    startOverButton.style.display = "inline-block"
                    "Click on the 'Start Over' button if you want to start over with a new process.\n"
                } else {
                    ""
                }
                createNotification("Process $processName succeeded :)")
                showSuccess(
                    """
                    |Process '$processName' ended successfully :) you can now close the window or continue with the process.
                    |$hintIfNotRelease
                    |Please report a bug at $GITHUB_NEW_ISSUE in case some job failed without us noticing it.
                    |Do not forget to star the repository if you like dep-graph-releaser ;-) $GITHUB_REPO
                    |Last but not least, you might want to visit $LOEWENFELS_URL to get to know the company pushing this project forward.
                    """.trimMargin()
                )
                buttonText.innerText = "Continue: $processName"
                button.title = "Continue with the process '$processName'."
                button.addClass(DEACTIVATED_CSS_CLASS)
            } else {
                createNotification("Process $processName failed :(")
                showError(
                    """
                    |Process '$processName' ended with failure :(
                    |At least one job failed. Check errors, fix them and then you can re-trigger the failed jobs, the pipeline respectively, by clicking on the release button (you might have to delete git tags and remove artifacts if they have already been created).
                    |
                    |Please report a bug at $GITHUB_NEW_ISSUE in case a job failed due to an error in dep-graph-releaser.
                    """.trimMargin()
                )
                buttonText.innerText = "Resume '$processName'"
                button.title = "Continue with the process '$processName' by re-processing previously failed projects."
            }
        }
    }

    private fun createNotification(body: String) {
        if (Notification.permission == NotificationPermission.GRANTED && !window.document.hasFocus()) {
            @Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
            val options = js("{}") as NotificationOptions
            options.body = body
            options.requireInteraction = true
            val notification = Notification("DGR ended", options)
            notification.onclick = {
                js("parent.focus()")
                window.focus()
                notification.close()
            }
        }
    }

    private fun HTMLElement.addClickEventListenerIfNotDeactivatedNorDisabled(action: () -> Any) {
        addClickEventListener {
            if (isNotDeactivated() && isNotDisabled()) {
                action()
            }
        }
    }

    private fun HTMLElement.deactivate(reason: String) {
        if (saveButton.isDisabled()) return

        this.addClass(DEACTIVATED_CSS_CLASS)
        this.setTitleSaveOld(reason)
    }

    private fun deactivateSaveButtonAndReactivateOthers() {
        saveButton.deactivate("Nothing to save, no changes were made")
        listOf(dryRunButton, releaseButton, exploreButton).forEach {
            val oldTitle = it.getOldTitleOrNull()
            if (oldTitle != null) {
                it.title = oldTitle
                it.removeClass(DEACTIVATED_CSS_CLASS)
            }
        }
    }

    fun activateSaveButtonAndDeactivateOthers() {
        if (saveButton.isDisabled()) return

        saveButton.removeClass(DEACTIVATED_CSS_CLASS)
        saveButton.title = "Publish changed json file and change location"
        val saveFirst = "You need to save your changes first."
        listOf(dryRunButton, releaseButton, exploreButton).forEach {
            it.deactivate(saveFirst)
        }
    }

    private fun activateDryRunButton() = activateButton(
        dryRunButton, "Start a dry run based on this release plan (no commit will be made, no artifact deployed etc.)."
    )

    private fun activateReleaseButton() = activateButton(
        releaseButton, "Start a release based on this release plan."
    )

    private fun activateExploreButton() = activateButton(
        exploreButton, "See in which order the projects are build, actual order may vary due to unequal execution time."
    )

    private fun activateToolsButton() = activateButton(
        toolsButton, TOOLS_INACTIVE_TITLE
    )

    private fun activateSettingsButton() = activateButton(
        settingsButton, SETTINGS_INACTIVE_TITLE
    )

    private fun activateStartOverButton() = activateButton(
        startOverButton, START_OVER_INACTIVE_TITLE
    )

    private fun activateButton(button: HTMLElement, newTitle: String) {
        if (button.isDisabled()) return

        button.removeClass(DEACTIVATED_CSS_CLASS)
        button.title = newTitle
    }

    private fun save(processStarter: ProcessStarter?): Promise<Unit> {
        saveButton.deactivate("Save in progress, please wait for the publish job to complete.")
        val nonNullProcessStarter = processStarter ?: showThrowableAndThrow(
            IllegalStateException(
                "Save button should not be activated if no publish job url was specified." +
                    "\nPlease report a bug: $GITHUB_REPO"
            )
        )
        return nonNullProcessStarter.publishChanges(verbose = true).then { hadChanges ->
            if (!hadChanges) showInfo("Seems like all changes have been reverted manually. Will not save anything.")
        }.finally { unit ->
            val noError = unit != null
            if (noError) {
                deactivateSaveButtonAndReactivateOthers()
            } else {
                activateSaveButtonAndDeactivateOthers()
            }
        }
    }

    companion object {
        private const val TOOLS_INACTIVE_TITLE = "Open the toolbox to see further available features."
        private const val SETTINGS_INACTIVE_TITLE = "Open Settings."
        private const val START_OVER_INACTIVE_TITLE = "Start over with a new process."

        private val saveButton get() = elementById("save")
        private val downloadButton get() = elementById("download")
        private val dryRunButton get() = elementById("dryRun")
        private val releaseButton get() = elementById("release")
        private val exploreButton get() = elementById("explore")
        private val toolsButton get() = elementById("tools")
        private val settingsButton get() = elementById("settings")
        private val startOverButton get() = elementById("startOver")
        private val eclipsePsfButton get() = elementById("eclipsePsf")
        private val gitCloneCommandsButton get() = elementById("gitCloneCommands")
        private val listDependentsButton get() = elementById("listDependents")
        private val changelogButton get() = elementById("changelog")
        private val changelogButtonExcel get() = elementById("changelogExcel")

        @Suppress(
            "ObjectPropertyName",
            "LateinitUsage",
            //TODO remove once https://github.com/arturbosch/detekt/issues/1215 is fixed
            "ObjectPropertyNaming"
        )
        private lateinit var _modifiableState: ModifiableState
        var modifiableState: ModifiableState
            get() = _modifiableState
            private set(value) {
                _modifiableState = value
            }

        fun getCurrentRunData(): Triple<String, HTMLElement, HTMLElement> {
            val typeOfRun = modifiableState.releasePlan.typeOfRun
            val buttonPair = when (typeOfRun) {
                TypeOfRun.EXPLORE -> exploreButton to elementById("explore:text")
                TypeOfRun.DRY_RUN -> dryRunButton to elementById("dryRun:text")
                TypeOfRun.RELEASE -> releaseButton to elementById("release:text")
            }
            return Triple(typeOfRun.toProcessName(), buttonPair.first, buttonPair.second)
        }

        fun disableButtonsDueToNoAuth(reason: String) {
            listOf(saveButton, dryRunButton, releaseButton).forEach {
                it.addClass(DISABLED_CSS_CLASS)
                it.title = reason
            }
        }
    }
}
