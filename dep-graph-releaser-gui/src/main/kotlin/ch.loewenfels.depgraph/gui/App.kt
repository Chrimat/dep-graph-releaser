package ch.loewenfels.depgraph.gui

import ch.loewenfels.depgraph.data.ReleaseState
import ch.loewenfels.depgraph.gui.actions.Downloader
import ch.loewenfels.depgraph.gui.actions.Publisher
import ch.loewenfels.depgraph.gui.actions.Releaser
import ch.loewenfels.depgraph.gui.components.Loader
import ch.loewenfels.depgraph.gui.components.Login
import ch.loewenfels.depgraph.gui.components.Menu
import ch.loewenfels.depgraph.gui.components.Messages.Companion.showThrowableAndThrow
import ch.loewenfels.depgraph.gui.jobexecution.*
import ch.loewenfels.depgraph.gui.recovery.recover
import ch.loewenfels.depgraph.gui.serialization.ModifiableState
import org.w3c.fetch.Response
import org.w3c.notifications.Notification
import kotlin.browser.window
import kotlin.js.Promise

class App {
    private val publishJobUrl: String?
    private val defaultJenkinsBaseUrl: String?
    private val menu: Menu
    private val eventManager: EventManager

    init {
        try {
            Loader.updateLoaderToLoadApiToken()
            val jsonUrl = determineJsonUrlOrThrow()
            publishJobUrl = determinePublishJob()

            defaultJenkinsBaseUrl = publishJobUrl?.substringBefore("/job/")
            eventManager = EventManager()
            menu = Menu(eventManager)
            start(jsonUrl)
        } catch (@Suppress("TooGenericExceptionCaught") t: Throwable) {
            showThrowableAndThrow(t)
        }
    }

    private fun determinePublishJob(): String? {
        return if (window.location.hash.contains(PUBLISH_JOB)) {
            getJobUrl(window.location.hash.substringAfter(PUBLISH_JOB))
        } else {
            null
        }
    }

    private fun getJobUrl(possiblyRelativePublishJobUrl: String): String {
        require(!possiblyRelativePublishJobUrl.contains("://") || possiblyRelativePublishJobUrl.startsWith("https")) {
            "The publish job URL does not start with https but contains ://"
        }

        val port = if (window.location.port.isNotBlank()) ":" + window.location.port else ""
        val prefix = window.location.protocol + "//" + window.location.hostname + port + "/"
        val tmpUrl = if (possiblyRelativePublishJobUrl.contains("://")) {
            possiblyRelativePublishJobUrl
        } else {
            require(window.location.protocol == "https:" || window.location.hostname == "localhost") {
                "The host needs to use the https protocol if publishJob is defined as relative path."
            }
            prefix + possiblyRelativePublishJobUrl
        }
        return if (tmpUrl.endsWith("/")) tmpUrl else "$tmpUrl/"
    }

    private fun start(jsonUrl: String) {
        val login = Login(defaultJenkinsBaseUrl)
        login.retrieveUserAndApiToken().then { username ->
            display("gui", "block")
            Loader.updateToLoadingJson()

            loadJsonAndCheckStatus(jsonUrl)
                .then { (_, body) ->
                    val modifiableState = ModifiableState(defaultJenkinsBaseUrl, body)
                    val releasePlan = modifiableState.releasePlan
                    val promise = if (username != null) {
                        Loader.updateToLoadOtherTokens()
                        login.loadOtherApiTokens(releasePlan)
                    } else {
                        Promise.resolve(Unit)
                    }
                    promise.then { modifiableState }
                }.then { modifiableState ->
                    val promise = if (modifiableState.releasePlan.state == ReleaseState.IN_PROGRESS) {
                        Loader.updateToRecoverOngoingProcess()
                        recover(modifiableState, defaultJenkinsBaseUrl)
                    } else {
                        Promise.resolve(modifiableState)
                    }
                    promise
                }.then { modifiableState ->
                    Loader.updateToLoadPipeline()
                    val processStarter = createProcessStarter(defaultJenkinsBaseUrl, publishJobUrl, modifiableState)
                    ContentContainer(modifiableState, menu, processStarter)
                    menu.initDependencies(Downloader(modifiableState), modifiableState, processStarter)
                    eventManager.recoverEventState(processStarter)
                    switchLoaderWithPipeline()
                    Notification.requestPermission()
                }
                .catch {
                    showThrowableAndThrow(it)
                }
        }
    }


    private fun switchLoaderWithPipeline() {
        display("loader", "none")
        display("pipeline", "table")
    }

    companion object {
        const val PUBLISH_JOB = "&publishJob="

        fun determineJsonUrlOrThrow(): String {
            return determineJsonUrl() ?: throw IllegalStateException(
                "You need to specify a release.json." +
                    "\nAppend the path with preceding # to the url, e.g., ${window.location}#release.json"
            )
        }


        fun determineJsonUrl(): String? {
            return if (window.location.hash != "") {
                window.location.hash.substring(1).substringBefore("&")
            } else {
                null
            }
        }

        fun loadJsonAndCheckStatus(jsonUrl: String): Promise<Pair<Response, String>> {
            return loadJson(jsonUrl)
                .then(::checkStatusOk)
                .catch<Pair<Response, String>> {
                    throw IllegalStateException("Could not load json from url $jsonUrl.", it)
                }
        }

        private fun loadJson(jsonUrl: String): Promise<Response> {
            val init = createFetchInitWithCredentials()
            return window.fetch(jsonUrl, init)
        }

        internal fun createProcessStarter(
            defaultJenkinsBaseUrl: String?,
            publishJobUrl: String?,
            modifiableState: ModifiableState
        ): ProcessStarter? {
            return if (publishJobUrl != null && defaultJenkinsBaseUrl != null) {

                val publisher = Publisher(publishJobUrl, modifiableState)
                val jenkinsJobExecutor = JenkinsJobExecutor()
                val simulatingJobExecutor = SimulatingJobExecutor()
                ProcessStarter(
                    publisher,
                    jenkinsJobExecutor,
                    simulatingJobExecutor
                ) { processStarter ->
                    Releaser(defaultJenkinsBaseUrl, modifiableState, processStarter)
                }
            } else {
                null
            }
        }

        fun givenOrFakeProcessStarter(
            processStarter: ProcessStarter?,
            modifiableState: ModifiableState
        ): ProcessStarter {
            val fakeJenkinsBaseUrl = "https://github.com/loewenfels/"
            return processStarter ?: App.createProcessStarter(
                fakeJenkinsBaseUrl,
                "${fakeJenkinsBaseUrl}dgr-publisher/",
                modifiableState
            ) ?: throw IllegalStateException("faking ProcessStarter failed, was null")
        }
    }
}
